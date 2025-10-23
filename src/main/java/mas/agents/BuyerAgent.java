package mas.agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import mas.logic.ConcessionService;
import mas.logic.ConfigLoader;
import mas.logic.EvaluationService;
import mas.logic.EvaluationService.IssueParameters;
import mas.logic.EvaluationService.IssueType;
import mas.models.Bid;
import mas.models.NegotiationResult;
import mas.models.Proposal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Representa a empresa compradora na negociação bilateral.
 * Este agente é criado pelo CoordinatorAgent para negociar com um SellerAgent específico.
 * Ele implementa uma Máquina de Estados Finitos (FSM) para gerenciar o protocolo de
 * oferta alternada (alternating-offer protocol).
 * <p>
 * TODO (Simplificação de Arquitetura):
 * Esta implementação negocia apenas UM ÚNICO LANCE (Bid) com o vendedor.
 * O artigo exige que o BA negocie uma PROPOSTA (Proposal) contendo MÚLTIPLOS LANCES
 * simultaneamente. A lógica nos estados (ex: EvaluateProposal)
 * deve ser refatorada para iterar sobre uma lista de lances "bid-by-bid",
 * em vez de apenas processar `getBids().get(0)`.
 */
public class BuyerAgent extends Agent {
    private static final Logger logger = LoggerFactory.getLogger(BuyerAgent.class);

    private static final String PROTOCOL_REPORT_RESULT = "report-negotiation-result";
    // Nomes dos estados
    private static final String STATE_SEND_REQUEST = "SendRequest";
    private static final String STATE_WAIT_FOR_PROPOSAL = "WaitForProposal";
    private static final String STATE_EVALUATE_PROPOSAL = "EvaluateProposal";
    private static final String STATE_ACCEPT_OFFER = "AcceptOffer";
    private static final String STATE_MAKE_COUNTER_OFFER = "MakeCounterOffer";
    private static final String STATE_END_NEGOTIATION = "EndNegotiation";

    // Variáveis de estado da negociação
    private AID sellerAgent;
    private AID coordinatorAgent;
    private ACLMessage receivedProposalMsg;
    private Bid finalAcceptedBid = null; // TODO: Deve ser uma List<Bid> ou List<NegotiationResult>
    private double finalUtility = 0.0;
    private int currentRound = 0;
    private String negotiationId;
    private String lastMessageReplyWith;
    // Serviços e Configurações
    private EvaluationService evalService;
    private ConcessionService concessionService;
    private Map<String, Double> weights;
    private Map<String, IssueParameters> issueParams; // TODO: Deve ser um Map<String, Map<String, IssueParameters>> (Bundle -> Issue -> Params)
    private double acceptanceThreshold;
    private double buyerRiskBeta;
    private double buyerGamma;
    private int maxRounds;
    private double discountRate;
    private Bid lastSentCounterBid = null;

    protected void setup() {
        logger.info("Buyer Agent {} is ready.", getAID().getName());

        Object[] args = getArguments();
        if (args != null && args.length > 1) {
            sellerAgent = (AID) args[0];
            coordinatorAgent = (AID) args[1];
            logger.info("{}: Assigned seller is {}", getName(), sellerAgent.getName());
        } else {
            logger.error("{}: Missing arguments (sellerAID, coordinatorAID). Terminating.", getName());
            doDelete();
            return;
        }

        setupBuyerPreferences();

        FSMBehaviour fsm = new FSMBehaviour(this) {
            @Override
            public int onEnd() {
                logger.info("{}: FSM finished negotiation with {}.", myAgent.getLocalName(), sellerAgent.getLocalName());
                myAgent.addBehaviour(new InformCoordinatorDone());
                return super.onEnd();
            }
        };

        // REGISTRA ESTADOS
        fsm.registerFirstState(new SendRequest(), STATE_SEND_REQUEST);
        fsm.registerState(new WaitForProposal(this), STATE_WAIT_FOR_PROPOSAL);
        fsm.registerState(new EvaluateProposal(), STATE_EVALUATE_PROPOSAL);
        fsm.registerState(new AcceptOffer(), STATE_ACCEPT_OFFER);
        fsm.registerState(new MakeCounterOffer(), STATE_MAKE_COUNTER_OFFER);
        fsm.registerLastState(new EndNegotiation(), STATE_END_NEGOTIATION);

        // REGISTRA TRANSIÇÕES
        fsm.registerDefaultTransition(STATE_SEND_REQUEST, STATE_WAIT_FOR_PROPOSAL);
        fsm.registerTransition(STATE_WAIT_FOR_PROPOSAL, STATE_EVALUATE_PROPOSAL, 1); // 1 = Proposta recebida
        fsm.registerTransition(STATE_WAIT_FOR_PROPOSAL, STATE_END_NEGOTIATION, 0);  // 0 = Timeout ou erro
        fsm.registerTransition(STATE_WAIT_FOR_PROPOSAL, STATE_END_NEGOTIATION, 2);  // 2 = Vendedor aceitou
        fsm.registerTransition(STATE_EVALUATE_PROPOSAL, STATE_ACCEPT_OFFER, 1);      // Utilidade aceitável
        fsm.registerTransition(STATE_EVALUATE_PROPOSAL, STATE_MAKE_COUNTER_OFFER, 0);// Utilidade baixa, fazer contraproposta
        fsm.registerTransition(STATE_EVALUATE_PROPOSAL, STATE_END_NEGOTIATION, 2);   // Deadline atingido ou falha
        fsm.registerDefaultTransition(STATE_ACCEPT_OFFER, STATE_END_NEGOTIATION);
        fsm.registerDefaultTransition(STATE_MAKE_COUNTER_OFFER, STATE_WAIT_FOR_PROPOSAL);

        addBehaviour(fsm);
    }

    private void setupBuyerPreferences() {
        ConfigLoader config = ConfigLoader.getInstance();
        this.evalService = new EvaluationService();
        this.concessionService = new ConcessionService();

        this.acceptanceThreshold = config.getDouble("buyer.acceptanceThreshold");
        this.buyerRiskBeta = config.getDouble("buyer.riskBeta");
        this.buyerGamma = config.getDouble("buyer.gamma");
        this.maxRounds = config.getInt("negotiation.maxRounds");
        this.discountRate = config.getDouble("negotiation.discountRate");

        weights = new HashMap<>();
        weights.put("price", config.getDouble("weights.price"));
        weights.put("quality", config.getDouble("weights.quality"));
        weights.put("delivery", config.getDouble("weights.delivery"));
        weights.put("service", config.getDouble("weights.service"));

        issueParams = new HashMap<>();
        // TODO (SINERGIA): Esta é a principal falha de sinergia.
        // O código carrega UM conjunto de parâmetros (min/max) para 'price' e 'delivery'.
        // O artigo exige que o agente tenha DIFERENTES [min, max] para CADA PACOTE DE PRODUTO
        // (ex: 'params.1000.price', 'params.1100.price').
        // Esta estrutura de dados 'issueParams' é inadequada para modelar sinergia.
        loadIssueParams(config, "price", IssueType.COST);
        loadIssueParams(config, "delivery", IssueType.COST);
        issueParams.put("quality", new IssueParameters(0, 1, IssueType.QUALITATIVE));
        issueParams.put("service", new IssueParameters(0, 1, IssueType.QUALITATIVE));
    }

    private void loadIssueParams(ConfigLoader config, String issueName, IssueType type) {
        String key = "params." + issueName;
        String value = config.getString(key);
        if (value != null && !value.isEmpty()) {
            String[] parts = value.split(",");
            if (parts.length == 2) {
                try {
                    double min = Double.parseDouble(parts[0].trim());
                    double max = Double.parseDouble(parts[1].trim());
                    issueParams.put(issueName, new IssueParameters(min, max, type));
                } catch (NumberFormatException e) {
                    logger.error("{}: Error parsing params for {}: {}", getName(), issueName, value, e);
                }
            }
        }
    }

    // --- Comportamentos da FSM ---

    /**
     * Estado 1: Envia o "Call for Proposal" (CFP) para o Vendedor.
     * Inicia a negociação.
     */
    private class SendRequest extends OneShotBehaviour {
        @Override
        public void action() {
            currentRound = 1;
            negotiationId = "neg-" + sellerAgent.getLocalName() + "-" + System.currentTimeMillis();
            logger.info("{} [R{}]: Sending call for proposal to {}", myAgent.getLocalName(), currentRound, sellerAgent.getLocalName());
            ACLMessage cfp = new ACLMessage(ACLMessage.REQUEST);
            cfp.addReceiver(sellerAgent);
            cfp.setContent("send-proposal");
            cfp.setConversationId(negotiationId);
            lastMessageReplyWith = "req-" + negotiationId + "-" + System.currentTimeMillis();
            cfp.setReplyWith(lastMessageReplyWith);
            myAgent.send(cfp);
        }
    }

    /**
     * Estado 2: Aguarda uma resposta (Proposta) do Vendedor.
     * Implementa um timeout para evitar bloqueio infinito.
     */
    /**
     * Estado 2: Aguarda uma resposta (Proposta OU Aceitação) do Vendedor.
     * Implementa um timeout para evitar bloqueio infinito.
     * Esta versão foi CORRIGIDA para lidar com ACCEPT_PROPOSAL.
     */
    private class WaitForProposal extends Behaviour {
        private boolean responseReceived = false;
        private long startTime;
        private long timeoutMillis = 15000;
        private int exitValue = 0; // 0=Timeout, 1=Propose (avaliar), 2=Accept (terminar)

        public WaitForProposal(Agent a) {
            super(a);
        }

        @Override
        public void onStart() {
            responseReceived = false;
            startTime = System.currentTimeMillis();
            exitValue = 0; // Reseta o valor de saída
            // logger.debug("{} [R{}]: Waiting for response from {}", myAgent.getLocalName(), currentRound, sellerAgent.getLocalName());
        }

        @Override
        public void action() {

            MessageTemplate idTemplate = MessageTemplate.and(
                    MessageTemplate.MatchConversationId(negotiationId),
                    MessageTemplate.MatchInReplyTo(lastMessageReplyWith)
            );

            // Template para Proposta
            MessageTemplate proposeTemplate = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
            // Template para Aceitação
            MessageTemplate acceptTemplate = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
            // Combina ambos com OR
            MessageTemplate performativeTemplate = MessageTemplate.or(proposeTemplate, acceptTemplate);

            // Combina com o Remetente (Seller)
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchSender(sellerAgent),
                    MessageTemplate.and(performativeTemplate, idTemplate)
            );

            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                responseReceived = true;
                receivedProposalMsg = msg; // Armazena a mensagem (seja ela qual for)

                if (msg.getPerformative() == ACLMessage.PROPOSE) {
                    // logger.debug("{} [R{}]: Received proposal.", myAgent.getLocalName(), currentRound);
                    exitValue = 1; // Proposta recebida, ir para EvaluateProposal
                } else { // Deve ser ACCEPT_PROPOSAL
                    logger.info("{}: Seller ACCEPTED my last counter-offer.", myAgent.getLocalName());
                    if (lastSentCounterBid != null) {
                        finalAcceptedBid = lastSentCounterBid;
                        finalUtility = evalService.calculateUtility("buyer", finalAcceptedBid, weights, issueParams, buyerRiskBeta);
                    } else {
                        logger.warn("{}: Seller accepted, but lastSentCounterBid is null!", myAgent.getLocalName());
                    }
                    exitValue = 2;
                }

            } else {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > timeoutMillis) {
                    logger.warn("{}: Timeout waiting for proposal from {}. Ending negotiation.", myAgent.getLocalName(), sellerAgent.getLocalName());
                    responseReceived = true; // Marca 'done' para sair
                    exitValue = 0; // Sai com timeout
                } else {
                    block(500); // Espera um pouco antes de checar de novo
                }
            }
        }

        @Override
        public boolean done() {
            return responseReceived;
        }

        @Override
        public int onEnd() {
            return exitValue; // Retorna 0 (Timeout), 1 (Propose) ou 2 (Accept)
        }
    }

    /**
     * Estado 3: Avalia a proposta recebida do Vendedor.
     * Decide se aceita, rejeita (faz contraproposta) ou encerra por deadline.
     */
    private class EvaluateProposal extends OneShotBehaviour {
        private int transitionEvent = 2; // Default: falha/deadline

        @Override
        public void action() {
            currentRound++;
            logger.info("{} [R{}]: Evaluating proposal from {}", myAgent.getLocalName(), currentRound, sellerAgent.getLocalName());

            if (currentRound > maxRounds) {
                logger.warn("{}: Deadline reached ({}/{}) . Ending negotiation.", myAgent.getLocalName(), currentRound, maxRounds);
                finalAcceptedBid = null;
                transitionEvent = 2;
                return;
            }

            try {
                Serializable content = receivedProposalMsg.getContentObject();
                if (content instanceof Proposal) {
                    Proposal p = (Proposal) content;
                    if (p.getBids() != null && !p.getBids().isEmpty()) {

                        // TODO (Simplificação de Arquitetura): Esta é a falha central.
                        // O código pega apenas o *primeiro* lance.
                        // O artigo exige que o agente avalie CADA lance na lista p.getBids()
                        // "bid-by-bid".
                        // Isto deveria ser um LOOP (for each bid in p.getBids()).
                        Bid receivedBid = p.getBids().get(0);

                        // TODO (SINERGIA): O 'issueParams' usado aqui é genérico.
                        // Para a sinergia, o EvaluationService deveria ser chamado com
                        // parâmetros [min, max] específicos para o
                        // 'receivedBid.getProductBundle()'.
                        double utility = evalService.calculateUtility("buyer", receivedBid, weights, issueParams, buyerRiskBeta);
                        logger.info("{}: Received bid utility = {} (Threshold = {})", myAgent.getLocalName(), String.format("%.4f", utility), String.format("%.4f", acceptanceThreshold));

                        // Implementação da Eq. 7: U(Bid_s) >= U_min E U(Bid_s) >= U(Bid_b(t+1))
                        Bid hypotheticalCounter = concessionService.generateCounterBid(receivedBid, currentRound + 1, maxRounds, buyerGamma, discountRate, issueParams, "buyer");
                        double nextCounterUtility = evalService.calculateUtility("buyer", hypotheticalCounter, weights, issueParams, buyerRiskBeta);

                        if (utility >= acceptanceThreshold && utility >= nextCounterUtility) {
                            logger.info("{}: Offer is acceptable (Utility {} >= Threshold {} AND >= Next Counter {}). Accepting.",
                                    myAgent.getLocalName(),
                                    String.format("%.4f", utility),
                                    String.format("%.4f", acceptanceThreshold),
                                    String.format("%.4f", nextCounterUtility));
                            finalAcceptedBid = receivedBid; // TODO: Deveria *adicionar* a uma lista de lances aceitos
                            finalUtility = utility;
                            transitionEvent = 1;
                        } else {
                            logger.info("{}: Offer not acceptable (Utility {}). Will make counter-offer.", myAgent.getLocalName(), String.format("%.4f", utility));
                            transitionEvent = 0;
                            // TODO: Deveria *adicionar* a uma lista de lances a serem contra-ofertados
                        }
                    } else {
                        logger.warn("{}: Received empty proposal.", myAgent.getLocalName());
                    }
                } else {
                    logger.error("{}: Received unexpected content type: {}", myAgent.getLocalName(), (content == null ? "null" : content.getClass().getName()));
                }
            } catch (UnreadableException e) {
                logger.error("{}: Failed to read proposal content.", myAgent.getLocalName(), e);
            }
        }

        @Override
        public int onEnd() {
            return transitionEvent;
        }
    }

    /**
     * Estado 4: Gera e envia uma contraproposta ao Vendedor.
     */
    private class MakeCounterOffer extends OneShotBehaviour {
        @Override
        public void action() {
            logger.info("{} [R{}]: Generating counter-offer...", myAgent.getLocalName(), currentRound);

            try {
                Proposal receivedP = (Proposal) receivedProposalMsg.getContentObject();
                // TODO (Simplificação): Novamente, pega apenas o lance [0].
                // Isto deveria ser um LOOP que gera contra-lances para CADA
                // lance que foi rejeitado no estado EvaluateProposal.
                Bid receivedB = receivedP.getBids().get(0);

                // TODO (SINERGIA): 'issueParams' genérico. Deveria usar params
                // específicos para o 'receivedB.getProductBundle()'.
                Bid counterBid = concessionService.generateCounterBid(
                        receivedB,
                        currentRound,
                        maxRounds,
                        buyerGamma,
                        discountRate,
                        issueParams,
                        "buyer"
                );
                lastSentCounterBid = counterBid;
                // TODO (Simplificação): A contraproposta deveria ser uma *lista*
                // de todos os contra-lances gerados no loop.
                Proposal counterProposal = new Proposal(List.of(counterBid));
                ACLMessage proposeMsg = new ACLMessage(ACLMessage.PROPOSE);
                proposeMsg.addReceiver(sellerAgent);
                proposeMsg.setConversationId(negotiationId);
                proposeMsg.setInReplyTo(receivedProposalMsg.getReplyWith());
                lastMessageReplyWith = "prop-" + negotiationId + "-" + System.currentTimeMillis();
                proposeMsg.setReplyWith(lastMessageReplyWith);
                proposeMsg.setContentObject(counterProposal);
                myAgent.send(proposeMsg);
                logger.info("{}: Sent counter-proposal (Round {}) -> {}", myAgent.getLocalName(), currentRound, counterBid.getIssues().get(0));

            } catch (UnreadableException | IOException e) {
                logger.error("{}: Error creating/sending counter-proposal", myAgent.getLocalName(), e);
            }
        }
    }

    /**
     * Estado 5: Envia uma mensagem de aceitação para a proposta do Vendedor.
     */
    private class AcceptOffer extends OneShotBehaviour {
        @Override
        public void action() {
            // TODO (Simplificação): O agente está aceitando a *proposta inteira*.
            // Na implementação correta, ele deveria aceitar *lances específicos*
            // que foram avaliados positivamente.
            logger.info("{}: Sending acceptance message to {}", myAgent.getLocalName(), sellerAgent.getLocalName());
            ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
            accept.addReceiver(sellerAgent);
            accept.setConversationId(negotiationId);
            accept.setInReplyTo(receivedProposalMsg.getReplyWith());
            accept.setContent("Offer accepted.");
            myAgent.send(accept);
        }
    }

    /**
     * Estado Final (FSM): Ações de finalização da negociação.
     */
    private static class EndNegotiation extends OneShotBehaviour {
        @Override
        public void action() {
            logger.info("{}: Negotiation process finished.", myAgent.getLocalName());
        }
    }

    /**
     * Comportamento Final: Informa o Coordenador sobre o resultado da negociação.
     * Envia o lance final (se houver) ou uma mensagem de falha.
     */
    private class InformCoordinatorDone extends OneShotBehaviour {
        @Override
        public void action() {
            ACLMessage doneMsg = new ACLMessage(ACLMessage.INFORM);
            doneMsg.addReceiver(coordinatorAgent);
            doneMsg.setProtocol(PROTOCOL_REPORT_RESULT);
            try {
                // TODO (Simplificação): Envia apenas UM resultado.
                // Na implementação correta, deveria enviar uma LISTA de
                // NegotiationResult, contendo todos os lances acordados
                // com este fornecedor específico.
                if (finalAcceptedBid != null) {
                    NegotiationResult result = new NegotiationResult(finalAcceptedBid, finalUtility, sellerAgent.getLocalName());
                    doneMsg.setContentObject(result);
                    logger.info("{}: Informing Coordinator of successful negotiation.", myAgent.getLocalName());
                } else {
                    doneMsg.setContent("NegotiationFailed");
                    logger.info("{}: Informing Coordinator of failed negotiation.", myAgent.getLocalName());
                }
                myAgent.send(doneMsg);
            } catch (IOException e) {
                logger.error("{}: Error sending result to Coordinator", myAgent.getLocalName(), e);
            }
        }
    }
}