package mas.agents;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class CoordinatorAgent extends Agent {
    private static final Logger logger = LoggerFactory.getLogger(CoordinatorAgent.class);

    private static final String PROTOCOL_GET_BUNDLES = "get-bundles-protocol";
    private static final String PROTOCOL_REPORT_RESULT = "report-negotiation-result";
    private static final String PROTOCOL_DEFINE_TASK = "define-task-protocol";
    private List<AID> sellerAgents;
    private int finishedCounter = 0;
    private WinnerDeterminationService wds;
    private List<NegotiationResult> negotiationResults;
    private int[] productDemand;

    protected void setup() {
        logger.info("Coordinator Agent {} is ready.", getAID().getName());

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
            logger.info("CA: Waiting for product requirements from TDA...");
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                    MessageTemplate.MatchProtocol(PROTOCOL_DEFINE_TASK)
            );
            ACLMessage msg = myAgent.blockingReceive(mt); // Espera bloqueado

            if (msg == null) {
                logger.warn("CA: No task message received (null).");
                return;
            }

            String productList = msg.getContent();
            logger.info("CA: Received task. Products required: {}", productList);
            // Define a demanda com base na requisição do TDA (ex: P1,P2,P3,P4 -> [1,1,1,1])
            productDemand = new int[productList.split(",").length];
            for (int i = 0; i < productDemand.length; i++) {
                productDemand[i] = 1;
            }
            logger.debug("CA: Product demand set to length {}.", productDemand.length);
        }
    }

    private class RequestProductBundles extends OneShotBehaviour {
        public void action() {
            logger.info("CA: Requesting preferred product bundles from SDA...");
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(new AID("sda", AID.ISLOCALNAME));
            msg.setContent("generate-bundles");
            msg.setProtocol(PROTOCOL_GET_BUNDLES); // Define o protocolo
            msg.setReplyWith("req-bundles-" + System.currentTimeMillis()); // ID único da pergunta
            myAgent.send(msg);

            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchProtocol(PROTOCOL_GET_BUNDLES) // Só aceita msgs deste protocolo
            );
            ACLMessage reply = myAgent.blockingReceive(mt);
            if (reply == null) {
                logger.warn("CA: No reply received for bundle request.");
                return;
            }
            try {
                @SuppressWarnings("unchecked")
                List<ProductBundle> bundles = (List<ProductBundle>) reply.getContentObject();
                logger.info("CA: Received {} preferred bundles from SDA.", bundles == null ? 0 : bundles.size());
                if (bundles != null && logger.isDebugEnabled()) {
                    for (int i = 0; i < bundles.size(); i++) {
                        logger.debug("CA: Bundle[{}] = {}", i, bundles.get(i));
                    }
                }
                // Em uma implementação completa, usaríamos 'bundles' para configurar os BAs
            } catch (UnreadableException e) {
                logger.error("CA: Failed to read bundles object from SDA.", e);
            }
        }
    }

    private class StartNegotiations extends OneShotBehaviour {
        public void action() {
            logger.info("CA: Preparation complete. Starting negotiation orchestration...");
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
        logger.info("CA: Creating {} to negotiate with {}", buyerName, sellerAgent.getLocalName());

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
            logger.debug("CA: Buyer agent {} started successfully.", buyerName);
        } catch (StaleProxyException e) {
            logger.error("CA: Failed to create/start buyer agent " + buyerName, e);
        }
    }

    private class WaitForResults extends TickerBehaviour {
        public WaitForResults() {
            super(CoordinatorAgent.this, 1000);
        }

        protected void onTick() {
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchProtocol(PROTOCOL_REPORT_RESULT) // Só ouve resultados de negociação
            );
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                finishedCounter++;
                try {
                    // Tenta ler o objeto NegotiationResult
                    Object content = msg.getContentObject();
                    if (content instanceof NegotiationResult) {
                        NegotiationResult result = (NegotiationResult) content;
                        negotiationResults.add(result);
                        logger.info("CA: Result received from {} -> {}", msg.getSender().getLocalName(), result);
                    } else {
                        logger.info("CA: Notification received from {} -> {}", msg.getSender().getLocalName(), msg.getContent());
                    }
                } catch (UnreadableException e) {
                    logger.warn("CA: Received non-object notification from {}", msg.getSender().getLocalName());
                }
            }

            if (finishedCounter >= (sellerAgents == null ? 0 : sellerAgents.size())) {
                logger.info("--- CA: All negotiations concluded. Determining winners... ---");

                List<NegotiationResult> optimalSolution = wds.solveWDPWithBranchAndBound(negotiationResults, productDemand);
                logger.info("\n--- OPTIMAL SOLUTION FOUND ---");
                if (optimalSolution == null || optimalSolution.isEmpty()) {
                    logger.info("No combination of bids could satisfy the demand.");
                } else {
                    double totalUtility = 0;
                    for (NegotiationResult res : optimalSolution) {
                        logger.info("-> {}", res);
                        totalUtility += res.getUtility();
                    }
                    logger.info("Total Maximized Utility: {:.3f}", totalUtility);
                }

                stop();
                // myAgent.doDelete();
            }
        }
    }
}