package com.unb.warehouse.behaviours;

import com.unb.warehouse.model.WarehouseModel;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Behaviour that periodically monitors stock levels for a configured SKU and
 * initiates a call-for-proposal (CFP) to other warehouses when the available
 * quantity falls below a configured trigger level.
 *
 * <p>
 * Responsibilities:
 * - Read SKU and reorder policy from a provided {@link JSONObject}.
 * - Query the {@link WarehouseModel} for the current available quantity.
 * - Use the Directory Facilitator (DF) to discover other agents providing the
 * "warehouse-service" and send them a CFP with reorder details.
 * </p>
 *
 * @author AlefMemTav
 */
public class StockMonitorBehaviour extends TickerBehaviour {
    private static final Logger log = LoggerFactory.getLogger(StockMonitorBehaviour.class);

    private final WarehouseModel model;
    private final JSONObject reorderPolicy;

    /**
     * Create a stock monitor.
     *
     * @param a             the agent this behaviour is attached to
     * @param period        tick period in milliseconds
     * @param model         local warehouse model (provides stock and location info)
     * @param reorderPolicy JSON object containing at least:
     *                      - "sku" (String): product identifier to monitor
     *                      - "triggerLevel" (int): reorder threshold
     *                      - "reorderQuantity" (int): quantity to request when triggered
     */
    public StockMonitorBehaviour(Agent a, long period, WarehouseModel model, JSONObject reorderPolicy) {
        super(a, period);
        this.model = model;
        this.reorderPolicy = reorderPolicy;
    }

    /**
     * Called on each tick. Checks current stock for the configured SKU and, if
     * below the trigger level, prepares and sends a CFP message to discovered
     * warehouse service providers.
     * <p>
     * Message content (JSON):
     * {
     * "productId": <sku>,
     * "qty": <reorderQuantity>,
     * "requesterId": <local warehouse id>,
     * "requesterLat": <latitude>,
     * "requesterLon": <longitude>
     * }
     * <p>
     * DF lookup filters services by type "warehouse-service".
     */
    @Override
    protected void onTick() {
        // Read policy values used for this check
        String sku = reorderPolicy.getString("sku");
        int triggerLevel = reorderPolicy.getInt("triggerLevel");
        int reorderQty = reorderPolicy.getInt("reorderQuantity");

        // Query local model for available quantity
        int available = model.getAvailable(sku);

        // If stock is below threshold, prepare a CFP to other warehouses
        if (available < triggerLevel) {
            log.info("{} stock for {} is low ({} < {}). Sending CFP.",
                    myAgent.getLocalName(), sku, available, triggerLevel);

            // Prepare CFP message with a unique conversation id
            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            cfp.setConversationId("supply-negotiation-" + System.currentTimeMillis());

            // Build JSON content describing the request
            JSONObject content = new JSONObject();
            content.put("productId", sku);
            content.put("qty", reorderQty);
            content.put("requesterId", model.getId());
            content.put("requesterLat", model.getLat());
            content.put("requesterLon", model.getLon());
            cfp.setContent(content.toString());

            // Discover other warehouses via the Directory Facilitator (DF)
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("warehouse-service");
            template.addServices(sd);

            try {
                DFAgentDescription[] result = DFService.search(myAgent, template);
                if (result.length > 0) {
                    for (DFAgentDescription dfd : result) {
                        AID provider = dfd.getName();
                        // Avoid sending to self
                        if (!provider.equals(myAgent.getAID())) {
                            cfp.addReceiver(provider);
                        }
                    }
                } else {
                    // No other warehouses found
                    log.warn("{} found no other warehouse services.", myAgent.getLocalName());
                }
            } catch (FIPAException fe) {
                // Log DF search failures but do not throw from behaviour
                log.error("Error searching DF: {}", fe.getMessage());
            }

            // Only send it if at least one receiver was added
            if (cfp.getAllReceiver().hasNext()) {
                myAgent.send(cfp);
                log.info("{} sent CFP for {} units of {}.", myAgent.getLocalName(), reorderQty, sku);
            }
        }
    }
}