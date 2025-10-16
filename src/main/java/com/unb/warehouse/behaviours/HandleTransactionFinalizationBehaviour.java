package com.unb.warehouse.behaviours;

import com.unb.warehouse.model.WarehouseModel;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Behaviour that handles the finalization of a transaction.
 *
 * <p>This cyclic behaviour listens for two kinds of ACL messages:
 * - ACLMessage.INFORM: indicates that a shipment or stock update has occurred. The message content
 * is expected to be a JSON object containing a "productId" (string) and "qty" (int). When received,
 * the behaviour updates the {@link WarehouseModel} inventory accordingly.
 * - ACLMessage.FAILURE: indicates that a purchase or reservation failed (for example due to
 * insufficient stock). The behaviour logs the rejection.</p>
 *
 * <p>If no message matching the template is available the behaviour blocks until new messages arrive.</p>
 *
 * @author AlefMemTav
 */
public class HandleTransactionFinalizationBehaviour extends CyclicBehaviour {
    private static final Logger log = LoggerFactory.getLogger(HandleTransactionFinalizationBehaviour.class);
    private final WarehouseModel model;

    /**
     * Creates a new behaviour instance.
     *
     * @param a     the agent this behaviour belongs to
     * @param model the warehouse model used to update and query stock levels
     */
    public HandleTransactionFinalizationBehaviour(Agent a, WarehouseModel model) {
        super(a);
        this.model = model;
    }

    /**
     * Main loop of the behaviour. Waits for INFORM or FAILURE messages.
     * <p>
     * - On INFORM: expects a JSON payload with keys "productId" (String) and "qty" (int).
     * Updates the warehouse inventory via {@link WarehouseModel#receiveIn(String, int)} and logs the new stock level.
     * - On FAILURE: logs a warning that the purchase was rejected.
     * <p>
     * Any JSON parsing errors are logged as errors.
     */
    @Override
    public void action() {
        MessageTemplate mt = MessageTemplate.or(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchPerformative(ACLMessage.FAILURE)
        );
        ACLMessage msg = myAgent.receive(mt);

        if (msg != null) {
            if (msg.getPerformative() == ACLMessage.INFORM) {
                try {
                    // Parse the JSON content and update inventory
                    JSONObject content = new JSONObject(msg.getContent());
                    String sku = content.getString("productId");
                    int qty = content.getInt("qty");

                    // Update the inventory
                    model.receiveIn(sku, qty);

                    log.info("{} successfully received {} units of {}. New stock: {}",
                            myAgent.getLocalName(), qty, sku, model.getStock(sku));

                } catch (JSONException e) {
                    log.error("Error processing INFORM message content in {}: {}", myAgent.getLocalName(), e.getMessage());
                }

            } else if (msg.getPerformative() == ACLMessage.FAILURE) {
                log.warn("{}'s purchase was rejected by {} due to stock unavailability.",
                        myAgent.getLocalName(), msg.getSender().getLocalName());
                // We could restart the CFP process here if desired.
            }

        } else {
            block();
        }
    }
}