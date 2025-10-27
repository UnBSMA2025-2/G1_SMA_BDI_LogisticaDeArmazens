package mas.agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TaskDecomposerAgent extends Agent {

    private static final Logger logger = LoggerFactory.getLogger(TaskDecomposerAgent.class);
    private List<String> pendingProducts = new ArrayList<>();
    private Random random = new Random();
    
    private static final long INITIAL_DELAY = 30000;
    private static final long DYNAMIC_INTERVAL = 15000;
    private static final double NEW_DEMAND_PROBABILITY = 0.3;

    protected void setup() {
        logger.info("TDA {} setup started.", getAID().getName());
        
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            String initialProducts = (String) args[0];
            if (initialProducts != null && !initialProducts.isEmpty()) {
                String[] products = initialProducts.split(",");
                for (String product : products) {
                    pendingProducts.add(product.trim());
                }
                logger.info("TDA {} loaded initial products: {}", getAID().getName(), pendingProducts);
            }
        }
        
        if (pendingProducts.isEmpty()) {
            pendingProducts.add("P1");
            pendingProducts.add("P2");
            pendingProducts.add("P3");
            pendingProducts.add("P4");
            logger.info("TDA {} using default products: {}", getAID().getName(), pendingProducts);
        }

        logger.info("TDA {} is ready.", getAID().getName());

        addBehaviour(new DemandUpdateBehaviour());
        addBehaviour(new ManualTriggerBehaviour());

        logger.debug("TDA: Scheduling start of process in {} ms for agent {}.", INITIAL_DELAY, getAID().getName());
        addBehaviour(new WakerBehaviour(this, INITIAL_DELAY) {
            protected void onWake() {
                logger.info("TDA: Initial WakerBehaviour triggered for agent {}.", myAgent.getAID().getName());
                sendCurrentDemands();
                myAgent.addBehaviour(new DynamicDemandBehaviour());
            }
        });
    }

    private void sendCurrentDemands() {
        if (!pendingProducts.isEmpty()) {
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(new AID("ca", AID.ISLOCALNAME));
            
            String content = String.join(",", pendingProducts);
            msg.setContent(content);
            msg.setProtocol("define-task-protocol");

            logger.debug("TDA: Prepared ACLMessage to coordinator with content='{}' and protocol='{}'.", 
                        msg.getContent(), msg.getProtocol());
            send(msg);
            logger.info("TDA: Request sent to Coordinator Agent for products: {}", content);
        } else {
            logger.info("TDA: No pending products to send.");
        }
    }

    public void addProductDemand(String product) {
        if (!pendingProducts.contains(product)) {
            pendingProducts.add(product);
            logger.info("TDA: Added new product demand: {}. Current demands: {}", product, pendingProducts);
        } else {
            logger.debug("TDA: Product {} already in demand list.", product);
        }
    }

    public void removeProductDemand(String product) {
        if (pendingProducts.remove(product)) {
            logger.info("TDA: Removed product demand: {}. Current demands: {}", product, pendingProducts);
        } else {
            logger.debug("TDA: Product {} not found in demand list.", product);
        }
    }

    public List<String> getCurrentDemands() {
        return new ArrayList<>(pendingProducts);
    }

    private class DynamicDemandBehaviour extends CyclicBehaviour {
        private long lastExecutionTime = 0;

        public void action() {
            long currentTime = System.currentTimeMillis();
            
            if (lastExecutionTime == 0 || (currentTime - lastExecutionTime) >= DYNAMIC_INTERVAL) {
                if (random.nextDouble() < NEW_DEMAND_PROBABILITY) {
                    generateDynamicDemand();
                }
                
                sendCurrentDemands();
                
                lastExecutionTime = currentTime;
            }
            
            block(1000);
        }

        private void generateDynamicDemand() {
            String[] productPool = {"P1", "P2", "P3", "P4", "P5", "P6", "P7", "P8"};
            String randomProduct = productPool[random.nextInt(productPool.length)];
            
            addProductDemand(randomProduct);
            logger.info("TDA: Generated dynamic demand for product: {}", randomProduct);
        }
    }

    private class DemandUpdateBehaviour extends CyclicBehaviour {
        private MessageTemplate template = MessageTemplate.MatchPerformative(ACLMessage.INFORM);

        public void action() {
            ACLMessage msg = myAgent.receive(template);
            if (msg != null) {
                try {
                    String content = msg.getContent();
                    logger.info("TDA: Received demand update: {}", content);
                    
                    if (content.contains(":")) {
                        String[] parts = content.split(":", 2);
                        String command = parts[0].toUpperCase();
                        String productsStr = parts[1];
                        
                        switch (command) {
                            case "ADD":
                                String[] productsToAdd = productsStr.split(",");
                                for (String product : productsToAdd) {
                                    addProductDemand(product.trim());
                                }
                                break;
                                
                            case "REMOVE":
                                String[] productsToRemove = productsStr.split(",");
                                for (String product : productsToRemove) {
                                    removeProductDemand(product.trim());
                                }
                                break;
                                
                            case "SET":
                                pendingProducts.clear();
                                String[] newProducts = productsStr.split(",");
                                for (String product : newProducts) {
                                    addProductDemand(product.trim());
                                }
                                break;
                                
                            case "SEND_NOW":
                                sendCurrentDemands();
                                break;
                                
                            default:
                                logger.warn("TDA: Unknown command received: {}", command);
                        }
                        
                        ACLMessage reply = msg.createReply();
                        reply.setPerformative(ACLMessage.CONFIRM);
                        reply.setContent("Demand updated successfully. Current demands: " + String.join(",", pendingProducts));
                        myAgent.send(reply);
                        
                    } else {
                        logger.warn("TDA: Invalid message format received: {}", content);
                    }
                    
                } catch (Exception e) {
                    logger.error("TDA: Error processing demand update message", e);
                }
            } else {
                block();
            }
        }
    }

    private class ManualTriggerBehaviour extends CyclicBehaviour {
        private MessageTemplate template = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);

        public void action() {
            ACLMessage msg = myAgent.receive(template);
            if (msg != null) {
                logger.info("TDA: Received manual trigger request");
                sendCurrentDemands();
                
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.INFORM);
                reply.setContent("Manual trigger processed. Demands sent: " + String.join(",", pendingProducts));
                myAgent.send(reply);
            } else {
                block();
            }
        }
    }

    private class SendRequestBehaviour extends OneShotBehaviour {
        public void action() {
            sendCurrentDemands();
        }
    }
}