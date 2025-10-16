package mas.agents;

import java.util.ArrayList;
import java.util.List;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;

public class CoordinatorAgent extends Agent {

    private List<AID> sellerAgents;
    private int finishedCounter = 0;

    protected void setup() {
        System.out.println("Coordinator Agent " + getAID().getName() + " is ready.");

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
        public WaitForResults() {
            super(CoordinatorAgent.this, 1000); // Verifica por mensagens a cada segundo
        }

        protected void onTick() {
            MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchContent("NegotiationFinished")
            );
            
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                finishedCounter++;
                System.out.println("CA: Result received from " + msg.getSender().getLocalName() + ". Total finished: " + finishedCounter);
            }

            if (finishedCounter >= sellerAgents.size()) {
                System.out.println("CA: Received all negotiation results. Proceeding to winner determination...");
                // Aqui, na Fase 5, chamaremos o WinnerDeterminationService
                stop(); // Para este comportamento
                // O Coordenador pode se autodestruir ou iniciar outra fase
                // myAgent.doDelete(); 
            }
        }
    }
}