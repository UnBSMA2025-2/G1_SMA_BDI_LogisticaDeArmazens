package mas.agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.lang.acl.ACLMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskDecomposerAgent extends Agent {

    private static final Logger logger = LoggerFactory.getLogger(TaskDecomposerAgent.class);

    protected void setup() {
        logger.info("TDA {} setup started.", getAID().getName());
        logger.info("TDA {} is ready.", getAID().getName());

        // Adiciona um comportamento que espera 30 segundos antes de iniciar todo o processo
        logger.debug("TDA: Scheduling start of process in 30000 ms for agent {}.", getAID().getName());
        addBehaviour(new WakerBehaviour(this, 30000) {
            protected void onWake() {
                logger.info("TDA: WakerBehaviour triggered for agent {}.", myAgent.getAID().getName());
                // O comportamento de enviar a requisição é adicionado AQUI, após o atraso
                myAgent.addBehaviour(new SendRequestBehaviour());
            }
        });
    }

    private static class SendRequestBehaviour extends OneShotBehaviour {

        public void action() {
            logger.info("TDA: Woke up. Preparing to send product requirements to Coordinator Agent.");

            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(new AID("ca", AID.ISLOCALNAME));
            msg.setContent("P1,P2,P3,P4");
            msg.setProtocol("define-task-protocol");

            logger.debug("TDA: Prepared ACLMessage to coordinator with content='{}' and protocol='{}'.", msg.getContent(), msg.getProtocol());
            myAgent.send(msg);
            logger.info("TDA: Request sent to Coordinator Agent (local name: ca) from agent {}.", myAgent.getAID().getName());
        }
    }
}