package mas.agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.lang.acl.ACLMessage;

public class TaskDecomposerAgent extends Agent {

    protected void setup() {
        System.out.println("TDA " + getAID().getName() + " is ready.");

        // Adiciona um comportamento que espera 30 segundos antes de iniciar todo o processo
        addBehaviour(new WakerBehaviour(this, 30000) {
            protected void onWake() {
                // O comportamento de enviar a requisição é adicionado AQUI, após o atraso
                myAgent.addBehaviour(new SendRequestBehaviour());
            }
        });
    }

    private class SendRequestBehaviour extends OneShotBehaviour {
        public void action() {
            System.out.println("TDA: Woke up. Sending product requirements to Coordinator Agent...");
            
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(new AID("ca", AID.ISLOCALNAME));
            msg.setContent("P1,P2,P3,P4"); 
            
            myAgent.send(msg);
        }
    }
}