package mas.agents;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List; // Import List
import java.util.Map;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour; // Import Behaviour
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

public class BuyerAgent extends Agent {
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
    private ACLMessage receivedProposalMsg; // Renomeado para clareza
    private Bid finalAcceptedBid = null;
    private double finalUtility = 0.0;
    private int currentRound = 0; // Começa em 0, incrementa ao fazer/receber proposta

    // Serviços e Configurações
    private EvaluationService evalService;
    private ConcessionService concessionService;
    private Map<String, Double> weights;
    private Map<String, IssueParameters> issueParams;
    private double acceptanceThreshold;
    private double buyerRiskBeta;
    private double buyerGamma;
    private int maxRounds;
    private double discountRate;


    protected void setup() {
        System.out.println("Buyer Agent " + getAID().getName() + " is ready.");

        Object[] args = getArguments();
        if (args != null && args.length > 1) {
            sellerAgent = (AID) args[0];
            coordinatorAgent = (AID) args[1];
            System.out.println(getName() + ": Assigned seller is " + sellerAgent.getName());
        } else {
            System.err.println(getName() + ": Missing arguments (sellerAID, coordinatorAID). Terminating.");
            doDelete(); return;
        }

        setupBuyerPreferences();

        FSMBehaviour fsm = new FSMBehaviour(this) {
            @Override
            public int onEnd() {
                System.out.println(myAgent.getLocalName() + ": FSM finished negotiation with " + sellerAgent.getLocalName() + ".");
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

        // REGISTRA TRANSIÇÕES (Ciclo: Request -> Wait -> Evaluate -> [Accept | Counter] -> Wait ...)
        fsm.registerDefaultTransition(STATE_SEND_REQUEST, STATE_WAIT_FOR_PROPOSAL);
        fsm.registerTransition(STATE_WAIT_FOR_PROPOSAL, STATE_EVALUATE_PROPOSAL, 1); // Proposta recebida
        fsm.registerTransition(STATE_WAIT_FOR_PROPOSAL, STATE_END_NEGOTIATION, 0);  // Timeout ou erro
        fsm.registerTransition(STATE_EVALUATE_PROPOSAL, STATE_ACCEPT_OFFER, 1);      // Utilidade aceitável
        fsm.registerTransition(STATE_EVALUATE_PROPOSAL, STATE_MAKE_COUNTER_OFFER, 0);// Utilidade baixa, fazer contraproposta
        fsm.registerTransition(STATE_EVALUATE_PROPOSAL, STATE_END_NEGOTIATION, 2);   // Deadline atingido ou falha
        fsm.registerDefaultTransition(STATE_ACCEPT_OFFER, STATE_END_NEGOTIATION);
        fsm.registerDefaultTransition(STATE_MAKE_COUNTER_OFFER, STATE_WAIT_FOR_PROPOSAL); // Após contraproposta, espera resposta

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
        // ... carregar outros pesos ...
        weights.put("service", config.getDouble("weights.service"));

        issueParams = new HashMap<>();
        // Carrega parâmetros para cada issue relevante (ex: Price, Delivery)
        loadIssueParams(config, "price", IssueType.COST);
        loadIssueParams(config, "delivery", IssueType.COST);
        // Assume parâmetros dummy para qualitativos, pois min/max não se aplicam diretamente
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
                     System.err.println(getName() + ": Error parsing params for " + issueName + ": " + value);
                 }
             }
         }
     }

    // --- Comportamentos da FSM ---

    private class SendRequest extends OneShotBehaviour {
        @Override
        public void action() {
             currentRound = 1; // Inicia contagem de rodadas
            System.out.println(myAgent.getLocalName() + " [R" + currentRound + "]: Sending call for proposal to " + sellerAgent.getLocalName());
            ACLMessage cfp = new ACLMessage(ACLMessage.REQUEST);
            cfp.addReceiver(sellerAgent);
            cfp.setContent("send-proposal");
            myAgent.send(cfp);
        }
    }

    private class WaitForProposal extends Behaviour {
        private boolean proposalReceived = false;
        private long startTime;
        private long timeoutMillis = 15000; // Timeout de espera pela proposta (15s)

        public WaitForProposal(Agent a) { super(a); }

        @Override
        public void onStart() {
            proposalReceived = false;
            startTime = System.currentTimeMillis();
            //System.out.println(myAgent.getLocalName() + " [R" + currentRound + "]: Waiting for proposal from " + sellerAgent.getLocalName());
        }

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchSender(sellerAgent), // Só aceita do vendedor esperado
                MessageTemplate.MatchPerformative(ACLMessage.PROPOSE)
            );
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                receivedProposalMsg = msg;
                proposalReceived = true;
                //System.out.println(myAgent.getLocalName() + " [R" + currentRound + "]: Received proposal.");
            } else {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > timeoutMillis) {
                    System.out.println(myAgent.getLocalName() + ": Timeout waiting for proposal from " + sellerAgent.getLocalName() + ". Ending negotiation.");
                    // Define um estado para indicar falha/timeout
                    proposalReceived = true; // Marca como 'done' para sair do estado
                } else {
                    block(500); // Espera um pouco antes de checar de novo
                }
            }
        }

        @Override
        public boolean done() {
            return proposalReceived;
        }

        @Override
        public int onEnd() {
            if (receivedProposalMsg != null) {
                return 1; // Proposta recebida, ir para Evaluate
            } else {
                return 0; // Timeout, ir para EndNegotiation
            }
        }
    }

    private class EvaluateProposal extends OneShotBehaviour {
        private int transitionEvent = 2; // Default: falha/deadline

        @Override
        public void action() {
             // Incrementa a rodada ao avaliar uma proposta recebida
             currentRound++;
            System.out.println(myAgent.getLocalName() + " [R" + currentRound + "]: Evaluating proposal from " + sellerAgent.getLocalName());


            if (currentRound > maxRounds) {
                System.out.println(myAgent.getLocalName() + ": Deadline reached (" + currentRound + "/" + maxRounds + "). Ending negotiation.");
                finalAcceptedBid = null;
                transitionEvent = 2; // Sinaliza deadline
                return;
            }

            try {
                Serializable content = receivedProposalMsg.getContentObject();
                if (content instanceof Proposal) {
                    Proposal p = (Proposal) content;
                    if (p.getBids() != null && !p.getBids().isEmpty()) {
                        Bid receivedBid = p.getBids().get(0); // Assume um lance por proposta por simplicidade

                        double utility = evalService.calculateUtility("buyer",receivedBid, weights, issueParams, buyerRiskBeta);
                        System.out.printf("%s: Received bid utility = %.4f (Threshold = %.4f)\n", myAgent.getLocalName(), utility, acceptanceThreshold);


                        // Condição de Aceitabilidade (Eq. 7 - Simplificada)
                        // U(Bid_s(t)) >= U_min E U(Bid_s(t)) >= U(Bid_b(t+1))
                        // Aqui U_min é acceptanceThreshold. Calculamos U(Bid_b(t+1))
                        Bid hypotheticalCounter = concessionService.generateCounterBid(receivedBid, currentRound + 1, maxRounds, buyerGamma, discountRate, issueParams, "buyer");
                        double nextCounterUtility = evalService.calculateUtility("buyer",hypotheticalCounter, weights, issueParams, buyerRiskBeta);

                        if (utility >= acceptanceThreshold && utility >= nextCounterUtility) {
                             System.out.println(myAgent.getLocalName() + ": Offer is acceptable (Utility " + String.format("%.4f", utility) + " >= Threshold " + String.format("%.4f", acceptanceThreshold) + " AND >= Next Counter " + String.format("%.4f", nextCounterUtility) + "). Accepting.");
                            finalAcceptedBid = receivedBid;
                            finalUtility = utility;
                            transitionEvent = 1; // Aceitar
                        } else {
                             System.out.println(myAgent.getLocalName() + ": Offer not acceptable (Utility " + String.format("%.4f", utility) + "). Will make counter-offer.");
                            transitionEvent = 0; // Fazer contraproposta
                        }
                    } else {
                        System.err.println(myAgent.getLocalName() + ": Received empty proposal.");
                    }
                } else {
                     System.err.println(myAgent.getLocalName() + ": Received unexpected content type: " + (content == null ? "null" : content.getClass().getName()));
                 }
            } catch (UnreadableException e) {
                System.err.println(myAgent.getLocalName() + ": Failed to read proposal content.");
                e.printStackTrace();
            }
        }

        @Override
        public int onEnd() {
            return transitionEvent;
        }
    }

     private class MakeCounterOffer extends OneShotBehaviour {
         @Override
         public void action() {
             // A rodada já foi incrementada em EvaluateProposal ANTES de decidir fazer a contraproposta
             System.out.println(myAgent.getLocalName() + " [R" + currentRound + "]: Generating counter-offer...");

             try {
                 Proposal receivedP = (Proposal) receivedProposalMsg.getContentObject();
                 Bid receivedB = receivedP.getBids().get(0);

                 Bid counterBid = concessionService.generateCounterBid(
                         receivedB, // Usa o lance recebido como referência
                         currentRound,
                         maxRounds,
                         buyerGamma,
                         discountRate,
                         issueParams,
                         "buyer" // Identifica que é o comprador fazendo a concessão
                 );

                 Proposal counterProposal = new Proposal(List.of(counterBid));
                 ACLMessage proposeMsg = new ACLMessage(ACLMessage.PROPOSE);
                 proposeMsg.addReceiver(sellerAgent);
                 proposeMsg.setContentObject(counterProposal);
                 myAgent.send(proposeMsg);
                 System.out.println(myAgent.getLocalName() + ": Sent counter-proposal (Round " + currentRound + ") -> " + counterBid.getIssues().get(0)); // Exibe o preço

             } catch (UnreadableException | IOException e) {
                 System.err.println(myAgent.getLocalName() + ": Error creating/sending counter-proposal");
                 e.printStackTrace();
                 // Poderia transicionar para EndNegotiation em caso de erro grave
             }
         }
     }

    private class AcceptOffer extends OneShotBehaviour {
        @Override
        public void action() {
            System.out.println(myAgent.getLocalName() + ": Sending acceptance message to " + sellerAgent.getLocalName());
            ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
            accept.addReceiver(sellerAgent);
            accept.setConversationId(receivedProposalMsg.getConversationId()); // Opcional: manter a conversa
            accept.setInReplyTo(receivedProposalMsg.getReplyWith());      // Opcional: referenciar a proposta
            accept.setContent("Offer accepted.");
            myAgent.send(accept);
        }
    }

    private class EndNegotiation extends OneShotBehaviour {
        @Override
        public void action() {
            System.out.println(myAgent.getLocalName() + ": Negotiation process finished.");
            // A lógica de informar o coordenador já está no onEnd da FSM
        }
    }

    private class InformCoordinatorDone extends OneShotBehaviour {
        @Override
        public void action() {
            ACLMessage doneMsg = new ACLMessage(ACLMessage.INFORM);
            doneMsg.addReceiver(coordinatorAgent);
            try {
                if (finalAcceptedBid != null) {
                    NegotiationResult result = new NegotiationResult(finalAcceptedBid, finalUtility, sellerAgent.getLocalName());
                    doneMsg.setContentObject(result);
                    System.out.println(myAgent.getLocalName() + ": Informing Coordinator of successful negotiation.");
                } else {
                    doneMsg.setContent("NegotiationFailed");
                    System.out.println(myAgent.getLocalName() + ": Informing Coordinator of failed negotiation.");
                }
                myAgent.send(doneMsg);
            } catch (IOException e) {
                System.err.println(myAgent.getLocalName() + ": Error sending result to Coordinator");
                e.printStackTrace();
            }
        }
    }
}