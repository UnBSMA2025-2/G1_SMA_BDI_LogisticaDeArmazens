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
import mas.models.NegotiationIssue;
import mas.models.ProductBundle;
import mas.models.Proposal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SellerAgent extends Agent {
    private static final Logger logger = LoggerFactory.getLogger(SellerAgent.class);

    // Nomes dos estados
    private static final String STATE_WAIT_FOR_REQUEST = "WaitForRequest";
    private static final String STATE_SEND_INITIAL_PROPOSAL = "SendInitialProposal";
    private static final String STATE_WAIT_FOR_RESPONSE = "WaitForResponse";
    private static final String STATE_EVALUATE_COUNTER = "EvaluateCounterProposal";
    private static final String STATE_ACCEPT_COUNTER = "AcceptCounterOffer"; // Novo estado
    private static final String STATE_MAKE_NEW_PROPOSAL = "MakeNewProposal"; // Novo estado
    private static final String STATE_END_NEGOTIATION = "EndNegotiation";

    // Variáveis de estado
    private AID buyerAgent;
    private ACLMessage receivedCounterMsg;
    private int currentRound = 0; // Começa em 0
    private String negotiationId;
    private ACLMessage initialRequestMsg;
    // Serviços e Configurações
    private EvaluationService evalService;
    private ConcessionService concessionService;
    private Map<String, Double> sellerWeights;
    private Map<String, IssueParameters> sellerIssueParams;
    private double sellerAcceptanceThreshold;
    private double sellerRiskBeta;
    private double sellerGamma;
    private int maxRounds;
    private double discountRate;

    protected void setup() {
        logger.info("Seller Agent {} is ready.", getAID().getName());
        setupSellerPreferences();

        FSMBehaviour fsm = new FSMBehaviour(this) {
            @Override
            public int onEnd() {
                logger.info("{}: FSM finished.", myAgent.getLocalName());
                // O vendedor não informa o coordenador diretamente
                return super.onEnd();
            }
        };

        // REGISTRA ESTADOS
        fsm.registerFirstState(new WaitForRequest(), STATE_WAIT_FOR_REQUEST);
        fsm.registerState(new SendInitialProposal(), STATE_SEND_INITIAL_PROPOSAL);
        fsm.registerState(new WaitForResponse(this), STATE_WAIT_FOR_RESPONSE);
        fsm.registerState(new EvaluateCounterProposal(), STATE_EVALUATE_COUNTER);
        fsm.registerState(new AcceptCounterOffer(), STATE_ACCEPT_COUNTER); // Aceita oferta do comprador
        fsm.registerState(new MakeNewProposal(), STATE_MAKE_NEW_PROPOSAL); // Faz nova proposta
        fsm.registerLastState(new EndNegotiation(), STATE_END_NEGOTIATION);

        // REGISTRA TRANSIÇÕES (Ciclo: WaitReq -> SendInitial -> WaitResp -> Evaluate -> [Accept | NewProp] -> WaitResp ...)
        fsm.registerDefaultTransition(STATE_WAIT_FOR_REQUEST, STATE_SEND_INITIAL_PROPOSAL);
        fsm.registerDefaultTransition(STATE_SEND_INITIAL_PROPOSAL, STATE_WAIT_FOR_RESPONSE);
        fsm.registerTransition(STATE_WAIT_FOR_RESPONSE, STATE_EVALUATE_COUNTER, 1); // Counter recebido
        fsm.registerTransition(STATE_WAIT_FOR_RESPONSE, STATE_END_NEGOTIATION, 0);  // Aceitação recebida ou Timeout
        fsm.registerTransition(STATE_EVALUATE_COUNTER, STATE_ACCEPT_COUNTER, 1);     // Aceita contraproposta do comprador
        fsm.registerTransition(STATE_EVALUATE_COUNTER, STATE_MAKE_NEW_PROPOSAL, 0);  // Rejeita e faz nova proposta
        fsm.registerTransition(STATE_EVALUATE_COUNTER, STATE_END_NEGOTIATION, 2);    // Deadline ou falha na avaliação
        fsm.registerDefaultTransition(STATE_ACCEPT_COUNTER, STATE_END_NEGOTIATION);
        fsm.registerDefaultTransition(STATE_MAKE_NEW_PROPOSAL, STATE_WAIT_FOR_RESPONSE); // Após nova proposta, espera resposta

        addBehaviour(fsm);
    }

    private void setupSellerPreferences() {
        ConfigLoader config = ConfigLoader.getInstance();
        this.evalService = new EvaluationService();
        this.concessionService = new ConcessionService();

        this.sellerAcceptanceThreshold = config.getDouble("seller.acceptanceThreshold");
        this.sellerRiskBeta = config.getDouble("seller.riskBeta");
        this.sellerGamma = config.getDouble("seller.gamma");
        this.maxRounds = config.getInt("negotiation.maxRounds");
        this.discountRate = config.getDouble("negotiation.discountRate");

        sellerWeights = new HashMap<>();
        sellerWeights.put("price", config.getDouble("seller.weights.price"));
        // ... carregar outros pesos do vendedor ...
        sellerWeights.put("service", config.getDouble("seller.weights.service"));


        sellerIssueParams = new HashMap<>();
        // Carrega parâmetros do vendedor
        loadIssueParams(config, "price", IssueType.COST, "seller.params.");
        loadIssueParams(config, "delivery", IssueType.COST, "seller.params.");
        sellerIssueParams.put("quality", new IssueParameters(0, 1, IssueType.QUALITATIVE));
        sellerIssueParams.put("service", new IssueParameters(0, 1, IssueType.QUALITATIVE));

    }

    // Sobrecarga para carregar com prefixo diferente
    private void loadIssueParams(ConfigLoader config, String issueName, IssueType type, String prefix) {
        String key = prefix + issueName;
        String value = config.getString(key);
        if (value != null && !value.isEmpty()) {
            String[] parts = value.split(",");
            if (parts.length == 2) {
                try {
                    double min = Double.parseDouble(parts[0].trim());
                    double max = Double.parseDouble(parts[1].trim());
                    sellerIssueParams.put(issueName, new IssueParameters(min, max, type));
                } catch (NumberFormatException e) {
                    logger.error("{}: Error parsing seller params for {}: {}", getName(), issueName, value, e);
                }
            }
        } else {
            logger.warn("{}: Missing seller params for {} (key: {})", getName(), issueName, key);
        }
    }


    // --- Comportamentos da FSM ---

    private class WaitForRequest extends OneShotBehaviour {
        @Override
        public void action() {
            logger.info("{}: Waiting for negotiation request...", myAgent.getLocalName());
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = myAgent.blockingReceive(mt); // Espera bloqueado
            if (msg != null) {
                initialRequestMsg = msg;
                buyerAgent = msg.getSender();
                negotiationId = msg.getConversationId();
                currentRound = 1; // Inicia contagem ao receber request
                logger.info("{} [R{}]: Received request from {}", myAgent.getLocalName(), currentRound, buyerAgent.getLocalName());
            } else {
                logger.error("{}: Failed to receive initial request. Terminating.", myAgent.getLocalName());
                // Poderia transicionar para um estado de erro ou terminar
                myAgent.doDelete();
            }
        }

        @Override
        public int onEnd() {
            return (buyerAgent != null ? 0 : 1); // 0 para sucesso, 1 para falha (hipotético)
        }
    }

    private class SendInitialProposal extends OneShotBehaviour {
        @Override
        public void action() {
            logger.info("{} [R{}]: Sending initial proposal to {}", myAgent.getLocalName(), currentRound, buyerAgent != null ? buyerAgent.getLocalName() : "unknown");
            ConfigLoader config = ConfigLoader.getInstance();
            double initialPrice = config.getDouble("seller.initial.price");
            // ... carregar outros valores iniciais ...
            String initialQuality = config.getString("seller.initial.quality");
            double initialDelivery = config.getDouble("seller.initial.delivery");
            String initialService = config.getString("seller.initial.service");


            ProductBundle pb = new ProductBundle(new int[]{1, 0, 0, 0}); // Exemplo P1
            List<NegotiationIssue> issues = new ArrayList<>();
            issues.add(new NegotiationIssue("Price", initialPrice));
            issues.add(new NegotiationIssue("Quality", initialQuality));
            issues.add(new NegotiationIssue("Delivery", initialDelivery));
            issues.add(new NegotiationIssue("Service", initialService));

            // Usa parâmetros do vendedor para quantidades (se aplicável, senão usar default)
            int[] quantities;
            String myName = myAgent.getLocalName();

            if (myName.equals("s1")) {
                // s1 oferece P1 e P2 (Pacote com sinergia)
                pb = new ProductBundle(new int[]{1, 1, 0, 0});
                quantities = new int[]{1000, 1000, 0, 0};
                logger.info("{}: Offering bundle P1+P2", myName);
            } else if (myName.equals("s2")) {
                // s2 oferece P3 e P4 (Pacote com sinergia)
                pb = new ProductBundle(new int[]{0, 0, 1, 1});
                quantities = new int[]{0, 0, 2000, 2000};
                logger.info("{}: Offering bundle P3+P4", myName);
            } else { // s3
                // s3 oferece P1 e P3 (Pacote concorrente)
                pb = new ProductBundle(new int[]{1, 0, 1, 0});
                quantities = new int[]{1000, 0, 2000, 0};
                logger.info("{}: Offering bundle P1+P3", myName);
            }
            Bid initialBid = new Bid(pb, issues, quantities);

            Proposal proposal = new Proposal(List.of(initialBid));

            ACLMessage msg = new ACLMessage(ACLMessage.PROPOSE);
            msg.addReceiver(buyerAgent);
            msg.setConversationId(negotiationId);
            msg.setInReplyTo(initialRequestMsg.getReplyWith());
            msg.setReplyWith("prop-" + negotiationId + "-" + System.currentTimeMillis());
            try {
                msg.setContentObject(proposal);
                myAgent.send(msg);
                logger.info("{}: Sent initial proposal -> Price: {}", myAgent.getLocalName(), initialPrice);
            } catch (IOException e) {
                logger.error("{}: Error sending initial proposal", myAgent.getLocalName(), e);
            }
        }
    }

    private class WaitForResponse extends Behaviour {
        private boolean responseReceived = false;
        private int nextTransition = 0; // 0=Timeout/Accept, 1=CounterProposal
        private long startTime;

        public WaitForResponse(Agent a) {
            super(a);
        }

        @Override
        public void onStart() {
            responseReceived = false;
            startTime = System.currentTimeMillis();
        }

        @Override
        public void action() {
            // Espera por ACEITAÇÃO ou CONTRA-PROPOSTA do comprador específico
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchSender(buyerAgent), // Argumento 1
                    MessageTemplate.and( // Argumento 2
                            MessageTemplate.or(
                                    MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL),
                                    MessageTemplate.MatchPerformative(ACLMessage.PROPOSE)
                            ),
                            MessageTemplate.MatchConversationId(negotiationId)
                    )
            );
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                if (msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                    logger.info("{}: Buyer accepted my last offer!", myAgent.getLocalName());
                    nextTransition = 0; // Vai para EndNegotiation
                } else { // É PROPOSE (contraproposta do comprador)
                    receivedCounterMsg = msg;
                    nextTransition = 1; // Vai para EvaluateCounterProposal
                }
                responseReceived = true;
            } else {
                long elapsed = System.currentTimeMillis() - startTime;
                // Timeout de espera pela resposta (15s)
                long timeoutMillis = 15000;
                if (elapsed > timeoutMillis) {
                    logger.info("{}: Timeout waiting for response from {}. Ending negotiation.", myAgent.getLocalName(), buyerAgent != null ? buyerAgent.getLocalName() : "unknown");
                    nextTransition = 0; // Timeout, vai para EndNegotiation
                    responseReceived = true;
                } else {
                    block(500);
                }
            }
        }

        @Override
        public boolean done() {
            return responseReceived;
        }

        @Override
        public int onEnd() {
            return nextTransition;
        }
    }

    private class EvaluateCounterProposal extends OneShotBehaviour {
        private int transitionEvent = 2; // Default: falha/deadline

        @Override
        public void action() {
            // A rodada é incrementada aqui, pois estamos avaliando a proposta da rodada anterior
            currentRound++;
            logger.info("{} [R{}]: Evaluating counter-proposal from {}", myAgent.getLocalName(), currentRound, buyerAgent != null ? buyerAgent.getLocalName() : "unknown");

            if (currentRound > maxRounds) {
                logger.info("{}: Deadline reached ({}/{}). Ending negotiation.", myAgent.getLocalName(), currentRound, maxRounds);
                transitionEvent = 2; // Sinaliza deadline
                return;
            }

            try {
                Serializable content = receivedCounterMsg.getContentObject();
                if (content instanceof Proposal) {
                    Proposal p = (Proposal) content;
                    if (p.getBids() != null && !p.getBids().isEmpty()) {
                        Bid counterBid = p.getBids().get(0);

                        // Avalia a contraproposta do comprador USANDO AS PREFERÊNCIAS DO VENDEDOR
                        double utilityForSeller = evalService.calculateUtility("seller", counterBid, sellerWeights, sellerIssueParams, sellerRiskBeta);
                        logger.info("{}: Received counter utility = {} (Threshold = {})",
                                myAgent.getLocalName(),
                                String.format("%.4f", utilityForSeller),
                                String.format("%.4f", sellerAcceptanceThreshold));

                        // Condição de Aceitabilidade do Vendedor (simplificada)
                        if (utilityForSeller >= sellerAcceptanceThreshold) {
                            logger.info("{}: Counter-offer is acceptable. Accepting.", myAgent.getLocalName());
                            transitionEvent = 1; // Aceitar (vai para AcceptCounterOffer)
                        } else {
                            logger.info("{}: Counter-offer not acceptable. Will make new proposal for round {}", myAgent.getLocalName(), (currentRound + 1));
                            transitionEvent = 0; // Rejeitar e fazer nova proposta (vai para MakeNewProposal)
                        }
                    } else { /* Tratar proposta vazia */
                        transitionEvent = 2;
                    }
                } else { /* Tratar tipo de conteúdo inesperado */
                    transitionEvent = 2;
                }
            } catch (UnreadableException e) {
                logger.error("{}: Failed to read counter-proposal content.", myAgent.getLocalName(), e);
                transitionEvent = 2; // Falha
            }
        }

        @Override
        public int onEnd() {
            return transitionEvent;
        }
    }

    private class AcceptCounterOffer extends OneShotBehaviour {
        @Override
        public void action() {
            logger.info("{}: Sending acceptance for buyer's counter-offer.", myAgent.getLocalName());
            ACLMessage acceptMsg = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
            acceptMsg.addReceiver(buyerAgent);
            acceptMsg.setConversationId(negotiationId);
            acceptMsg.setInReplyTo(receivedCounterMsg.getReplyWith());
            acceptMsg.setContent("Accepted your counter-offer.");
            myAgent.send(acceptMsg);
        }
    }

    private class MakeNewProposal extends OneShotBehaviour {
        @Override
        public void action() {
            // A rodada já foi incrementada em EvaluateCounterProposal
            logger.info("{} [R{}]: Generating new proposal...", myAgent.getLocalName(), currentRound);

            try {
                Proposal receivedP = (Proposal) receivedCounterMsg.getContentObject();
                Bid receivedB = receivedP.getBids().get(0);

                // Vendedor gera sua próxima oferta, baseada na última oferta do comprador
                Bid newSellerBid = concessionService.generateCounterBid(
                        receivedB, // Usa a contraproposta do comprador como referência
                        currentRound,
                        maxRounds,
                        sellerGamma, // Gamma do vendedor
                        discountRate,
                        sellerIssueParams, // Parâmetros do vendedor
                        "seller" // Identifica que é o vendedor fazendo a concessão
                );

                Proposal newProposal = new Proposal(List.of(newSellerBid));
                ACLMessage proposeMsg = new ACLMessage(ACLMessage.PROPOSE);
                proposeMsg.addReceiver(buyerAgent);
                proposeMsg.setConversationId(negotiationId);
                proposeMsg.setInReplyTo(receivedCounterMsg.getReplyWith());
                proposeMsg.setReplyWith("prop-" + negotiationId + "-" + System.currentTimeMillis());
                proposeMsg.setContentObject(newProposal);
                myAgent.send(proposeMsg);
                logger.info("{}: Sent new proposal (Round {}) -> {}", myAgent.getLocalName(), currentRound, newSellerBid.getIssues().get(0));

            } catch (UnreadableException | IOException e) {
                logger.error("{}: Error creating/sending new proposal", myAgent.getLocalName(), e);
            }
        }
    }

    private static class EndNegotiation extends OneShotBehaviour {
        @Override
        public void action() {
            logger.info("{}: Negotiation process finished.", myAgent.getLocalName());
        }
    }
}
