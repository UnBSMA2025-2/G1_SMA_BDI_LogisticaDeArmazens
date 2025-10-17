package mas.agents;

import java.util.ArrayList;
import java.util.List;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import mas.logic.WinnerDeterminationService;
import mas.models.NegotiationResult;

public class CoordinatorAgent extends Agent {

    private List<AID> sellerAgents;
    private int finishedCounter = 0;
    private WinnerDeterminationService wds;
    private List<NegotiationResult> negotiationResults;
    private int[] productDemand = new int[]{1, 1, 1, 1}; // Demanda por 4 produtos

    protected void setup() {
        System.out.println("Coordinator Agent " + getAID().getName() + " is ready.");

        // Inicializa a lista de resultados e o serviço de determinação do vencedor
        this.wds = new WinnerDeterminationService();
        this.negotiationResults = new ArrayList<>();

        // Lista de fornecedores (hardcoded para a Fase 4)
        sellerAgents = new ArrayList<>();
        sellerAgents.add(new AID("s1", AID.ISLOCALNAME));
        sellerAgents.add(new AID("s2", AID.ISLOCALNAME));
        sellerAgents.add(new AID("s3", AID.ISLOCALNAME));

        // Inicia um comportamento para criar os BuyerAgents
        addBehaviour(new TickerBehaviour(this, 2000) {
            private int sellersStarted = 0;
            protected void onTick() {
                if (sellersStarted < sellerAgents.size()) {
                    AID seller = sellerAgents.get(sellersStarted);
                    createBuyerFor(seller);
                    sellersStarted++;
                } else {
                    // Depois de criar todos, para este comportamento
                    stop();
                    // E inicia o comportamento de espera
                    myAgent.addBehaviour(new WaitForResults());
                }
            }
        });
    }

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

                List<NegotiationResult> optimalSolution = wds.findOptimalCombination(negotiationResults, productDemand);

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