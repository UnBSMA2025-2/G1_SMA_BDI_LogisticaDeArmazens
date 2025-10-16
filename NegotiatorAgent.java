import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class NegotiatorAgent extends Agent {
    private ProductDB myProducts;
    private String partnerName;
    
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            partnerName = (String) args[0];
        }
        
        myProducts = new ProductDB();
                String agentName = getLocalName();
        if (agentName.equals("agent1")) {
            myProducts.addProduct("apples", 5);
            myProducts.addProduct("bananas", 1);
            myProducts.addProduct("oranges", 3);
        } else {
            myProducts.addProduct("apples", 1);
            myProducts.addProduct("bananas", 5);
            myProducts.addProduct("oranges", 4);
        }
        
        System.out.println(getLocalName() + " started with products: " + myProducts.toJSON());
                addBehaviour(new NegotiationBehaviour());
        if (getLocalName().equals("agent1")) {
            addBehaviour(new InitiatorBehaviour());
        }
    }
    
    private class NegotiationBehaviour extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = receive(mt);
            
            if (msg != null) {
                String requestedProduct = msg.getContent();
                System.out.println(getLocalName() + " received request for: " + requestedProduct);
                
                ACLMessage reply = msg.createReply();
                
                if (myProducts.hasProduct(requestedProduct)) {
                    reply.setPerformative(ACLMessage.AGREE);
                    reply.setContent("OK");
                    myProducts.decreaseQuantity(requestedProduct);
                    System.out.println(getLocalName() + " agrees to give: " + requestedProduct);
                } else {
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setContent("I don't have " + requestedProduct);
                    System.out.println(getLocalName() + " refuses: don't have " + requestedProduct);
                }
                
                send(reply);
            } else {
                block();
            }
        }
    }
    
    private class InitiatorBehaviour extends CyclicBehaviour {
        private boolean negotiationStarted = false;
        
        public void action() {
            if (!negotiationStarted) {
                ProductDB otherDB = new ProductDB();
                if (getLocalName().equals("agent1")) {
                    otherDB.addProduct("apples", 1);
                    otherDB.addProduct("bananas", 5);
                    otherDB.addProduct("oranges", 4);
                }
                
                String neededProduct = myProducts.findNeededProduct(otherDB);
                
                if (neededProduct != null) {
                    System.out.println(getLocalName() + " needs: " + neededProduct);
                    
                    ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                    msg.addReceiver(new jade.core.AID(partnerName, jade.core.AID.ISLOCALNAME));
                    msg.setContent(neededProduct);
                    send(msg);
                    
                    MessageTemplate mt = MessageTemplate.MatchConversationId(msg.getConversationId());
                    ACLMessage reply = blockingReceive(mt);
                    
                    if (reply != null) {
                        System.out.println(getLocalName() + " received reply: " + reply.getContent());
                    }
                }
                
                negotiationStarted = true;
            }
            block();
        }
    }
}