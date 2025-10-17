package mas.agents;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import mas.logic.ConfigLoader;
import mas.logic.EvaluationService;
import mas.logic.EvaluationService.IssueParameters;
import mas.logic.EvaluationService.IssueType;
import mas.models.Bid;
import mas.models.NegotiationResult;
import mas.models.Proposal;

public class BuyerAgent extends Agent {
    // Nomes dos estados
    private static final String STATE_WAIT_FOR_PROPOSAL = "WaitForProposal";
    private static final String STATE_EVALUATE_PROPOSAL = "EvaluateProposal";
    private static final String STATE_ACCEPT_OFFER = "AcceptOffer";
    private static final String STATE_MAKE_COUNTER_OFFER = "MakeCounterOffer";
    private static final String STATE_END_NEGOTIATION = "EndNegotiation";

    private Bid finalAcceptedBid;
    private double finalUtility;
    private AID sellerAgent;
    private AID coordinatorAgent;
    private EvaluationService evalService;
    private Map<String, Double> weights;
    private Map<String, IssueParameters> issueParams;
    private ACLMessage receivedProposal;
    private double acceptanceThreshold;
    private static final String STATE_SEND_REQUEST = "SendRequest";

    protected void setup() {
        System.out.println("Buyer Agent " + getAID().getName() + " is ready.");

        // Recebe argumentos: [0] = AID do vendedor, [1] = AID do coordenador
        Object[] args = getArguments();
        if (args != null && args.length > 1) {
            sellerAgent = (AID) args[0];
            coordinatorAgent = (AID) args[1];
            System.out.println(getName() + ": Assigned seller is " + sellerAgent.getName());
        } else {
            doDelete(); return;
        }

        // Configuração das preferências do comprador
        setupBuyerPreferences();

        FSMBehaviour fsm = new FSMBehaviour(this) {
            public int onEnd() {
                System.out.println("Buyer FSM finished for " + sellerAgent.getName());
                myAgent.addBehaviour(new InformCoordinatorDone());
                return super.onEnd();
            }
        };

        // REGISTRA OS ESTADOS
        fsm.registerFirstState(new SendRequest(), STATE_SEND_REQUEST);
        fsm.registerState(new WaitForProposal(this, 10000), STATE_WAIT_FOR_PROPOSAL);
        fsm.registerState(new EvaluateProposal(), STATE_EVALUATE_PROPOSAL);
        fsm.registerState(new AcceptOffer(), STATE_ACCEPT_OFFER);
        fsm.registerState(new MakeCounterOffer(), STATE_MAKE_COUNTER_OFFER);
        fsm.registerLastState(new EndNegotiation(), STATE_END_NEGOTIATION);
                
        // REGISTRA AS TRANSIÇÕES
        fsm.registerDefaultTransition(STATE_SEND_REQUEST, STATE_WAIT_FOR_PROPOSAL);
        fsm.registerTransition(STATE_WAIT_FOR_PROPOSAL, STATE_EVALUATE_PROPOSAL, 1);
        fsm.registerTransition(STATE_WAIT_FOR_PROPOSAL, STATE_END_NEGOTIATION, 0);
        fsm.registerTransition(STATE_EVALUATE_PROPOSAL, STATE_ACCEPT_OFFER, 1);
        fsm.registerTransition(STATE_EVALUATE_PROPOSAL, STATE_MAKE_COUNTER_OFFER, 0);
        fsm.registerDefaultTransition(STATE_ACCEPT_OFFER, STATE_END_NEGOTIATION);
        fsm.registerDefaultTransition(STATE_MAKE_COUNTER_OFFER, STATE_END_NEGOTIATION);

        addBehaviour(fsm);
            
    }

    private class SendRequest extends OneShotBehaviour {
        public void action() {
            System.out.println(getName() + ": Sending call for proposal to " + sellerAgent.getName());
            ACLMessage cfp = new ACLMessage(ACLMessage.REQUEST);
            cfp.addReceiver(sellerAgent);
            cfp.setContent("send-proposal");
            myAgent.send(cfp);
        }
    }

    private class InformCoordinatorDone extends OneShotBehaviour {
        public void action() {
            ACLMessage doneMsg = new ACLMessage(ACLMessage.INFORM);
            doneMsg.addReceiver(coordinatorAgent);
            try {
                if (finalAcceptedBid != null) {
                    // Envia o objeto NegotiationResult se houve acordo
                    NegotiationResult result = new NegotiationResult(finalAcceptedBid, finalUtility, sellerAgent.getLocalName());
                    doneMsg.setContentObject(result);
                } else {
                    // Envia uma mensagem simples se não houve acordo
                    doneMsg.setContent("NegotiationFailed");
                }
                myAgent.send(doneMsg);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }   

    private void setupBuyerPreferences() {
        ConfigLoader config = ConfigLoader.getInstance();

        // Carrega o limiar e o beta do arquivo de configuração
        this.acceptanceThreshold = config.getDouble("buyer.acceptanceThreshold");
        double beta = config.getDouble("buyer.riskBeta"); 

        this.evalService = new EvaluationService();
        
        // Carrega os pesos do arquivo
        weights = new HashMap<>();
        weights.put("price", config.getDouble("weights.price"));
        weights.put("quality", config.getDouble("weights.quality"));
        weights.put("delivery", config.getDouble("weights.delivery"));
        weights.put("service", config.getDouble("weights.service"));

        // Carrega os parâmetros (min/max) do arquivo
        issueParams = new HashMap<>();
        String[] priceParams = config.getString("params.price").split(",");
        String[] deliveryParams = config.getString("params.delivery").split(",");
        
        issueParams.put("price", new IssueParameters(Double.parseDouble(priceParams[0]), Double.parseDouble(priceParams[1]), IssueType.COST));
        issueParams.put("delivery", new IssueParameters(Double.parseDouble(deliveryParams[0]), Double.parseDouble(deliveryParams[1]), IssueType.COST));
        issueParams.put("quality", new IssueParameters(0, 0, IssueType.QUALITATIVE));
        issueParams.put("service", new IssueParameters(0, 0, IssueType.QUALITATIVE));
    }
    
    // --- CLASSES INTERNAS PARA CADA ESTADO DA FSM ---

    private class WaitForProposal extends jade.core.behaviours.Behaviour {
        // (Código similar ao WaitForResponse do Seller, mas espera por PROPOSE)
        private boolean done = false;
        private int transition = 0;
        private Agent agent;
        private long timeout;
        private long startTime;

        public WaitForProposal(Agent a, long timeout) {
            this.agent = a;
            this.timeout = timeout;
        }
        public void onStart() {
            startTime = System.currentTimeMillis();
        }
        
        public void action() {
            MessageTemplate template = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
            ACLMessage msg = myAgent.receive(template);
            if (msg != null) {
                receivedProposal = msg;
                transition = 1;
                done = true;
            } else {
                block(1000); // Bloqueia por 1 segundo antes de tentar de novo
            }
        }
        public boolean done() { return done; }
        public int onEnd() { return transition; }
    }

    private class EvaluateProposal extends jade.core.behaviours.OneShotBehaviour {
        private int transition;
        public void action() {
            try {
                Proposal p = (Proposal) receivedProposal.getContentObject();
                Bid firstBid = p.getBids().get(0); // Avalia apenas o primeiro lance por simplicidade

                // Usa o EvaluationService da Fase 2
                double utility = evalService.calculateUtility(firstBid, weights, issueParams, 1.0); // beta=1.0 (neutro)
                System.out.println(getName() + ": Received proposal from " + sellerAgent.getLocalName() + " with utility: " + utility);

                if (utility >= acceptanceThreshold) {
                    System.out.println(getName() + ": Utility is acceptable. Accepting the offer.");
                    finalAcceptedBid = firstBid; // GUARDA O LANCE ACEITO
                    finalUtility = utility;      // GUARDA A UTILIDADE
                    transition = 1; // Aceitar
                } else {
                    transition = 0; // Contraproposta
                }
            } catch (UnreadableException e) {
                e.printStackTrace();
                transition = 0;
            }
        }
        public int onEnd() {
            return transition;
        }
    }

    private class AcceptOffer extends jade.core.behaviours.OneShotBehaviour {
        public void action() {
            ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
            accept.addReceiver(sellerAgent);
            accept.setContent("I accept your last proposal.");
            send(accept);
            System.out.println("Buyer: Sent acceptance message.");
        }
    }

    private class MakeCounterOffer extends jade.core.behaviours.OneShotBehaviour {
        public void action() {
            ACLMessage reject = new ACLMessage(ACLMessage.REJECT_PROPOSAL); // Alterado para REJECT
            reject.addReceiver(sellerAgent);
            reject.setContent("Your offer is not good enough.");
            send(reject);
            System.out.println("Buyer: Sent rejection message.");
        }
    }
    
    private class EndNegotiation extends jade.core.behaviours.OneShotBehaviour {
        public void action() {
            System.out.println("Buyer: Negotiation finished.");
        }
    }
}