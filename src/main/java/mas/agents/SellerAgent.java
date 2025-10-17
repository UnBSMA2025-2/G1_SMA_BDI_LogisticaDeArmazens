package mas.agents;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import mas.models.Bid;
import mas.models.NegotiationIssue;
import mas.models.ProductBundle;
import mas.models.Proposal;

public class SellerAgent extends Agent {
    // Nomes dos estados da FSM
    private static final String STATE_SEND_INITIAL_PROPOSAL = "SendInitialProposal";
    private static final String STATE_WAIT_FOR_RESPONSE = "WaitForResponse";
    private static final String STATE_EVALUATE_COUNTER = "EvaluateCounterProposal";
    private static final String STATE_END_NEGOTIATION = "EndNegotiation";
    private static final String STATE_WAIT_FOR_REQUEST = "WaitForRequest";

    private AID buyerAgent;

    protected void setup() {
        System.out.println("Seller Agent " + getAID().getName() + " is ready.");
        

        FSMBehaviour fsm = new FSMBehaviour(this) {
            public int onEnd() {
                System.out.println("Seller FSM finished for " + getAID().getName());
                return super.onEnd();
            }
        };

        // REGISTRA OS ESTADOS
        fsm.registerFirstState(new WaitForRequest(), STATE_WAIT_FOR_REQUEST);
        fsm.registerState(new SendInitialProposal(), STATE_SEND_INITIAL_PROPOSAL);
        fsm.registerState(new WaitForResponse(this, 10000), STATE_WAIT_FOR_RESPONSE);
        fsm.registerState(new EvaluateCounterProposal(), STATE_EVALUATE_COUNTER);
        fsm.registerLastState(new EndNegotiation(), STATE_END_NEGOTIATION);

        // REGISTRA AS TRANSIÇÕES
        fsm.registerDefaultTransition(STATE_WAIT_FOR_REQUEST, STATE_SEND_INITIAL_PROPOSAL);
        fsm.registerDefaultTransition(STATE_SEND_INITIAL_PROPOSAL, STATE_WAIT_FOR_RESPONSE);
        fsm.registerTransition(STATE_WAIT_FOR_RESPONSE, STATE_END_NEGOTIATION, 0);
        fsm.registerTransition(STATE_WAIT_FOR_RESPONSE, STATE_EVALUATE_COUNTER, 1);
        fsm.registerDefaultTransition(STATE_EVALUATE_COUNTER, STATE_END_NEGOTIATION);

        addBehaviour(fsm);
    }

    private class WaitForRequest extends OneShotBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = myAgent.blockingReceive(mt); // Espera bloqueado até receber
            
            buyerAgent = msg.getSender();
            System.out.println(getAID().getName() + ": Received request from " + buyerAgent.getName());
        }
    }
        
    // --- CLASSES INTERNAS PARA CADA ESTADO DA FSM ---

    private class SendInitialProposal extends jade.core.behaviours.OneShotBehaviour {
        public void action() {
            // Cria uma proposta inicial "medíocre"
            ProductBundle pb = new ProductBundle(new int[]{1, 0, 0, 0});
            List<NegotiationIssue> issues = new ArrayList<>();
            issues.add(new NegotiationIssue("Price", 40.0));
            issues.add(new NegotiationIssue("Quality", "medium"));
            issues.add(new NegotiationIssue("Delivery", 9.0));
            issues.add(new NegotiationIssue("Service", "poor"));
            Bid bid = new Bid(pb, issues, new int[]{1000, 0, 0, 0});
            
            List<Bid> bids = new ArrayList<>();
            bids.add(bid);
            Proposal proposal = new Proposal(bids);

            // Envia a proposta para o comprador
            ACLMessage msg = new ACLMessage(ACLMessage.PROPOSE);
            msg.addReceiver(buyerAgent);
            try {
                msg.setContentObject(proposal);
                send(msg);
                System.out.println("Seller: Sent initial proposal -> Price: 40.0");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class WaitForResponse extends jade.core.behaviours.Behaviour {
        private int transition = 0; // 0 = Fim, 1 = Contraproposta
        private boolean done = false;
        private long timeout;
        private long wakeupTime;
        private MessageTemplate template = MessageTemplate.or(
            MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL),
            MessageTemplate.MatchPerformative(ACLMessage.PROPOSE)
        );

        public WaitForResponse(Agent a, long timeout) {
            super(a);
            this.timeout = timeout;
        }

        public void onStart() {
            this.wakeupTime = System.currentTimeMillis() + timeout;
        }
        
        public void action() {
            ACLMessage msg = myAgent.receive(template);
            if (msg != null) {
                if (msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                    System.out.println("Seller: Buyer accepted my proposal!");
                    transition = 0;
                } else { // É uma contraproposta (PROPOSE)
                    System.out.println("Seller: Received a counter-proposal.");
                    transition = 1;
                }
                done = true;
            } else {
                long dt = wakeupTime - System.currentTimeMillis();
                if (dt <= 0) {
                    System.out.println("Seller: Timeout expired. Ending negotiation.");
                    transition = 0;
                    done = true;
                } else {
                    block(dt);
                }
            }
        }

        public boolean done() {
            return done;
        }

        public int onEnd() {
            return transition;
        }
    }

    private class EvaluateCounterProposal extends jade.core.behaviours.OneShotBehaviour {
        public void action() {
            // Lógica simplificada para a Fase 3
            System.out.println("Seller: Evaluating counter-proposal... For now, I will just end the negotiation.");
        }
    }

    private class EndNegotiation extends jade.core.behaviours.OneShotBehaviour {
        public void action() {
            System.out.println("Seller: Negotiation finished.");
        }
    }
}