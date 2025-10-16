package mas.agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.FSMBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import mas.logic.EvaluationService;
import mas.logic.EvaluationService.IssueParameters;
import mas.logic.EvaluationService.IssueType;
import mas.models.Bid;
import mas.models.Proposal;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class BuyerAgent extends Agent {
    // Nomes dos estados
    private static final String STATE_WAIT_FOR_PROPOSAL = "WaitForProposal";
    private static final String STATE_EVALUATE_PROPOSAL = "EvaluateProposal";
    private static final String STATE_ACCEPT_OFFER = "AcceptOffer";
    private static final String STATE_MAKE_COUNTER_OFFER = "MakeCounterOffer";
    private static final String STATE_END_NEGOTIATION = "EndNegotiation";

    private AID sellerAgent;
    private EvaluationService evalService;
    private Map<String, Double> weights;
    private Map<String, IssueParameters> issueParams;
    private ACLMessage receivedProposal;
    private double acceptanceThreshold = 0.7; // Limiar de aceitação

    protected void setup() {
        System.out.println("Buyer Agent " + getAID().getName() + " is ready.");
        sellerAgent = new AID("seller1", AID.ISLOCALNAME);

        // Configuração das preferências do comprador
        setupBuyerPreferences();

        FSMBehaviour fsm = new FSMBehaviour(this) {
            public int onEnd() {
                System.out.println("Buyer FSM behaviour finished.");
                return super.onEnd();
            }
        };

        // REGISTRA OS ESTADOS
        fsm.registerFirstState(new WaitForProposal(this, 5000), STATE_WAIT_FOR_PROPOSAL);
        fsm.registerState(new EvaluateProposal(), STATE_EVALUATE_PROPOSAL);
        fsm.registerState(new AcceptOffer(), STATE_ACCEPT_OFFER);
        fsm.registerState(new MakeCounterOffer(), STATE_MAKE_COUNTER_OFFER);
        fsm.registerLastState(new EndNegotiation(), STATE_END_NEGOTIATION);
        
        // REGISTRA AS TRANSIÇÕES
        fsm.registerTransition(STATE_WAIT_FOR_PROPOSAL, STATE_EVALUATE_PROPOSAL, 1); // Proposta recebida
        fsm.registerTransition(STATE_WAIT_FOR_PROPOSAL, STATE_END_NEGOTIATION, 0);  // Timeout
        fsm.registerTransition(STATE_EVALUATE_PROPOSAL, STATE_ACCEPT_OFFER, 1); // Utilidade > limiar
        fsm.registerTransition(STATE_EVALUATE_PROPOSAL, STATE_MAKE_COUNTER_OFFER, 0); // Utilidade <= limiar
        fsm.registerDefaultTransition(STATE_ACCEPT_OFFER, STATE_END_NEGOTIATION);
        fsm.registerDefaultTransition(STATE_MAKE_COUNTER_OFFER, STATE_END_NEGOTIATION); // Simplificado

        addBehaviour(fsm);
    }

    private void setupBuyerPreferences() {
        this.evalService = new EvaluationService();
        
        weights = new HashMap<>();
        weights.put("price", 0.4);
        weights.put("quality", 0.3);
        weights.put("delivery", 0.15);
        weights.put("service", 0.15);

        issueParams = new HashMap<>();
        issueParams.put("price", new IssueParameters(50.0, 60.0, IssueType.COST));
        issueParams.put("delivery", new IssueParameters(1.0, 10.0, IssueType.COST));
        issueParams.put("quality", new IssueParameters(0, 0, IssueType.QUALITATIVE));
        issueParams.put("service", new IssueParameters(0, 0, IssueType.QUALITATIVE));
    }
    
    // --- CLASSES INTERNAS PARA CADA ESTADO DA FSM ---

    private class WaitForProposal extends jade.core.behaviours.Behaviour {
        // (Código similar ao WaitForResponse do Seller, mas espera por PROPOSE)
        private boolean done = false;
        private int transition = 0;
        public WaitForProposal(Agent a, long timeout) { /* ... construtor ... */ }
        public void onStart() { /* ... */ }
        
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
                System.out.println("Buyer: Received proposal with calculated utility: " + utility);

                if (utility >= acceptanceThreshold) {
                    System.out.println("Buyer: Utility is acceptable. Accepting the offer.");
                    transition = 1; // Aceitar
                } else {
                    System.out.println("Buyer: Utility is too low. Making a counter-offer.");
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