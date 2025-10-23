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

/**
 * Agente central que orquestra o processo de seleção de fornecedores.
 * Implementa a arquitetura do CA da Figura 1.
 * 1. (Preparação) Obtém a tarefa do TDA e os pacotes (bundles) do SDA .
 * 2. (Barganha) Cria BAs para negociar com SAs.
 * 3. (Determinação) Coleta resultados e usa o WDS para selecionar vencedores.
 */
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
    private List<ProductBundle> preferredBundles; // Armazena os pacotes preferidos

    protected void setup() {
        logger.info("Coordinator Agent {} is ready.", getAID().getName());

        this.wds = new WinnerDeterminationService();
        this.negotiationResults = new ArrayList<>();
        this.preferredBundles = new ArrayList<>(); // Inicializa a lista

        SequentialBehaviour preparationPhase = new SequentialBehaviour();
        preparationPhase.addSubBehaviour(new WaitForTask());
        preparationPhase.addSubBehaviour(new RequestProductBundles());
        preparationPhase.addSubBehaviour(new StartNegotiations());

        addBehaviour(preparationPhase);
    }

    // --- Comportamentos da Fase de Preparação ---

    /**
     * Estado 1: Aguarda a definição da tarefa (produtos requeridos) do TDA.
     */
    private class WaitForTask extends OneShotBehaviour {
        public void action() {
            logger.info("CA: Waiting for product requirements from TDA...");
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                    MessageTemplate.MatchProtocol(PROTOCOL_DEFINE_TASK)
            );
            ACLMessage msg = myAgent.blockingReceive(mt);

            if (msg == null) {
                logger.warn("CA: No task message received (null).");
                return;
            }

            String productList = msg.getContent();
            logger.info("CA: Received task. Products required: {}", productList);
            // Constrói o vetor de demanda (ex: P1,P2,P3,P4 -> [1,1,1,1])
            productDemand = new int[productList.split(",").length];
            for (int i = 0; i < productDemand.length; i++) {
                productDemand[i] = 1;
            }
            logger.debug("CA: Product demand set to length {}.", productDemand.length);
        }
    }

    /**
     * Estado 2: Solicita os pacotes de produtos (bundles) preferidos ao SDA.
     */
    private class RequestProductBundles extends OneShotBehaviour {
        public void action() {
            logger.info("CA: Requesting preferred product bundles from SDA...");
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(new AID("sda", AID.ISLOCALNAME));
            msg.setContent("generate-bundles");
            msg.setProtocol(PROTOCOL_GET_BUNDLES);
            msg.setReplyWith("req-bundles-" + System.currentTimeMillis());
            myAgent.send(msg);

            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchProtocol(PROTOCOL_GET_BUNDLES)
            );
            ACLMessage reply = myAgent.blockingReceive(mt);
            if (reply == null) {
                logger.warn("CA: No reply received for bundle request.");
                return;
            }
            try {
                // Armazena a lista de pacotes recebida
                @SuppressWarnings("unchecked")
                List<ProductBundle> bundles = (List<ProductBundle>) reply.getContentObject();
                if (bundles != null) {
                    preferredBundles.addAll(bundles); // Salva na variável do agente
                    logger.info("CA: Received {} preferred bundles from SDA.", bundles.size());
                } else {
                    logger.warn("CA: Received null or empty bundle list from SDA.");
                }
            } catch (UnreadableException e) {
                logger.error("CA: Failed to read bundles object from SDA.", e);
            }
        }
    }

    /**
     * Estado 3: Identifica SAs e cria um BA para cada um, iniciando a fase de barganha.
     */
    private class StartNegotiations extends OneShotBehaviour {
        public void action() {
            logger.info("CA: Preparation complete. Starting negotiation orchestration...");
            // Simula a descoberta de fornecedores
            sellerAgents = new ArrayList<>();
            sellerAgents.add(new AID("s1", AID.ISLOCALNAME));
            sellerAgents.add(new AID("s2", AID.ISLOCALNAME));
            sellerAgents.add(new AID("s3", AID.ISLOCALNAME));

            for (AID seller : sellerAgents) {
                createBuyerFor(seller);
            }
            // Adiciona o comportamento que ouve os resultados
            myAgent.addBehaviour(new WaitForResults());
        }
    }

    // --- Métodos e Comportamentos de Orquestração ---

    /**
     * Cria e inicia um novo BuyerAgent, passando os AIDs do Vendedor e do Coordenador.
     *
     * @param sellerAgent O AID do Vendedor com quem o BA deve negociar.
     */
    private void createBuyerFor(AID sellerAgent) {
        String buyerName = "buyer_for_" + sellerAgent.getLocalName();
        logger.info("CA: Creating {} to negotiate with {}", buyerName, sellerAgent.getLocalName());

        try {
            // TODO (Simplificação de Arquitetura): O CA deveria configurar a estratégia do BA.
            // O artigo afirma que o CA deve "Configurar [as] estratégias de negociação dos BAs
            // para diferentes fornecedores e diferentes pacotes de produtos".
            // Atualmente, estamos passando apenas os AIDs. A implementação correta
            // passaria também a lista 'preferredBundles' e, potencialmente,
            // estratégias (gamas/betas) específicas para este 'sellerAgent'.
            Object[] args = new Object[]{
                    sellerAgent,    // Argumento 0: AID do Vendedor
                    getAID(),       // Argumento 1: AID do Coordenador
                    // TODO: Argumento 2: preferredBundles (a lista de pacotes)
            };

            AgentController buyerController = getContainerController().createNewAgent(
                    buyerName,
                    "mas.agents.BuyerAgent",
                    args
            );
            buyerController.start();
            logger.debug("CA: Buyer agent {} started successfully.", buyerName);
        } catch (StaleProxyException e) {
            logger.error("CA: Failed to create/start buyer agent " + buyerName, e);
        }
    }

    /**
     * Comportamento (Ticker) que coleta os resultados das negociações bilaterais.
     * Quando todos os BAs terminam, ele aciona o WinnerDeterminationService.
     */
    private class WaitForResults extends TickerBehaviour {
        public WaitForResults() {
            super(CoordinatorAgent.this, 1000); // Verifica a cada 1 segundo
        }

        protected void onTick() {
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchProtocol(PROTOCOL_REPORT_RESULT)
            );
            ACLMessage msg = myAgent.receive(mt);

            if (msg != null) {
                finishedCounter++;
                try {
                    // TODO (Simplificação): O 'content' pode ser UMA NegotiationResult
                    // ou uma LISTA<NegotiationResult>. O código atual só trata de UMA.
                    Object content = msg.getContentObject();
                    if (content instanceof NegotiationResult) {
                        NegotiationResult result = (NegotiationResult) content;
                        negotiationResults.add(result);
                        logger.info("CA: Result received from {} -> {}", msg.getSender().getLocalName(), result);
                    } else {
                        // Trata falhas (ex: "NegotiationFailed" ou timeout)
                        logger.info("CA: Notification received from {} -> {}", msg.getSender().getLocalName(), msg.getContent());
                    }
                } catch (UnreadableException e) {
                    logger.warn("CA: Received non-object notification from {}", msg.getSender().getLocalName());
                }
            }

            // Quando todos os BAs (um por SA) tiverem respondido
            if (finishedCounter >= (sellerAgents == null ? 0 : sellerAgents.size())) {
                logger.info("--- CA: All negotiations concluded. Determining winners... ---");

                // Aciona o WDS com todos os lances finais coletados
                List<NegotiationResult> optimalSolution = wds.solveWDPWithBranchAndBound(negotiationResults, productDemand);

                // Imprime a solução final
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

                stop(); // Para o TickerBehaviour
                // myAgent.doDelete(); // Opcional: desliga o CA
            }
        }
    }
}