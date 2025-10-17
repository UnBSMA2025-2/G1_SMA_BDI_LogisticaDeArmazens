package mas.agents;

import java.util.ArrayList;
import java.util.List;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import mas.logic.WinnerDeterminationService;
import mas.models.NegotiationResult;
import mas.models.ProductBundle;

public class CoordinatorAgent extends Agent {

    private List<AID> sellerAgents;
    private int finishedCounter = 0;
    private WinnerDeterminationService wds;
    private List<NegotiationResult> negotiationResults;
    private int[] productDemand;

    protected void setup() {
        System.out.println("Coordinator Agent " + getAID().getName() + " is ready.");

        // Inicializa a lista de resultados e o serviço de determinação do vencedor
        this.wds = new WinnerDeterminationService();
        this.negotiationResults = new ArrayList<>();

        // Comportamento sequencial para a fase de preparação
        SequentialBehaviour preparationPhase = new SequentialBehaviour();
        preparationPhase.addSubBehaviour(new WaitForTask());
        preparationPhase.addSubBehaviour(new RequestProductBundles());
        preparationPhase.addSubBehaviour(new StartNegotiations());

        addBehaviour(preparationPhase);
    }

    // --- Comportamentos da Fase de Preparação ---

    private class WaitForTask extends OneShotBehaviour {
        public void action() {
            System.out.println("CA: Waiting for product requirements from TDA...");
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
            ACLMessage msg = myAgent.blockingReceive(mt); // Espera bloqueado
            
            String productList = msg.getContent();
            System.out.println("CA: Received task. Products required: " + productList);
            // Define a demanda com base na requisição do TDA (ex: P1,P2,P3,P4 -> [1,1,1,1])
            productDemand = new int[productList.split(",").length];
            for (int i = 0; i < productDemand.length; i++) {
                productDemand[i] = 1;
            }
        }
    }

    private class RequestProductBundles extends OneShotBehaviour {
        public void action() {
            System.out.println("CA: Requesting preferred product bundles from SDA...");
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(new AID("sda", AID.ISLOCALNAME));
            msg.setContent("generate-bundles");
            myAgent.send(msg);

            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
            ACLMessage reply = myAgent.blockingReceive(mt);
            try {
                List<ProductBundle> bundles = (List<ProductBundle>) reply.getContentObject();
                System.out.println("CA: Received " + bundles.size() + " preferred bundles from SDA.");
                // Em uma implementação completa, usaríamos 'bundles' para configurar os BAs
            } catch (UnreadableException e) {
                e.printStackTrace();
            }
        }
    }

    private class StartNegotiations extends OneShotBehaviour {
        public void action() {
            System.out.println("CA: Preparation complete. Starting negotiation orchestration...");
            sellerAgents = new ArrayList<>();
            sellerAgents.add(new AID("s1", AID.ISLOCALNAME));
            sellerAgents.add(new AID("s2", AID.ISLOCALNAME));
            sellerAgents.add(new AID("s3", AID.ISLOCALNAME));

            for (AID seller : sellerAgents) {
                createBuyerFor(seller);
            }
            myAgent.addBehaviour(new WaitForResults());
        }
    }

    // --- Métodos e Comportamentos de Orquestração ---

    private void createBuyerFor(AID sellerAgent) {
        String buyerName = "buyer_for_" + sellerAgent.getLocalName();
        System.out.println("CA: Creating " + buyerName + " to negotiate with " + sellerAgent.getLocalName());

        try {
            // Cria os argumentos para passar ao BuyerAgent: [AID do Vendedor, AID do Coordenador]
            Object[] args = new Object[]{sellerAgent, getAID()};
            
            // Cria a instância do agente no container
            AgentController buyerController = getContainerController().createNewAgent(
                buyerName,          // Nome do novo agente
                "mas.agents.BuyerAgent", // Classe do novo agente
                args                // Argumentos
            );
            buyerController.start(); // Inicia o agente
        } catch (StaleProxyException e) {
            e.printStackTrace();
        }
    }

    private class WaitForResults extends TickerBehaviour {
        public WaitForResults() { super(CoordinatorAgent.this, 1000); }

        protected void onTick() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                finishedCounter++;
                try {
                    // Tenta ler o objeto NegotiationResult
                    Object content = msg.getContentObject();
                    if (content instanceof NegotiationResult) {
                        NegotiationResult result = (NegotiationResult) content;
                        negotiationResults.add(result);
                        System.out.println("CA: Result received from " + msg.getSender().getLocalName() + " -> " + result);
                    } else {
                        System.out.println("CA: Notification received from " + msg.getSender().getLocalName() + " -> " + msg.getContent());
                    }
                } catch (UnreadableException e) {
                    System.out.println("CA: Received non-object notification from " + msg.getSender().getLocalName());
                }
            }

            if (finishedCounter >= sellerAgents.size()) {
                System.out.println("--- CA: All negotiations concluded. Determining winners... ---");

                List<NegotiationResult> optimalSolution = wds.solveWDPWithBranchAndBound(negotiationResults, productDemand);
                System.out.println("\n--- OPTIMAL SOLUTION FOUND ---");
                if (optimalSolution.isEmpty()) {
                    System.out.println("No combination of bids could satisfy the demand.");
                } else {
                    double totalUtility = 0;
                    for (NegotiationResult res : optimalSolution) {
                        System.out.println("-> " + res);
                        totalUtility += res.getUtility();
                    }
                    System.out.printf("Total Maximized Utility: %.3f\n", totalUtility);
                }

                stop();
                // myAgent.doDelete();
            }
        }
    }
}