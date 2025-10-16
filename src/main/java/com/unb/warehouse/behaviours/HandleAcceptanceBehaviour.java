package com.unb.warehouse.behaviours;

import com.unb.warehouse.model.WarehouseModel;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Behaviour that handles acceptance messages from other agents.
 *
 * <p>This CyclicBehaviour listens for ACL messages with the performative
 * {@link ACLMessage#ACCEPT_PROPOSAL}. When an acceptance is received it
 * attempts to reserve the requested quantity in the {@link WarehouseModel}.
 *
 * <ul>
 *   <li>If the reservation succeeds, the behaviour replies with an INFORM
 *       containing a JSON confirmation and updates the model's confirmed
 *       outbound quantity.</li>
 *   <li>If the reservation fails, the behaviour replies with a FAILURE
 *       message describing the reason and increments the lost-offer counter
 *       in the model.</li>
 * </ul>
 *
 * @author AlefMemTav
 */
public class HandleAcceptanceBehaviour extends CyclicBehaviour {
    private static final Logger log = LoggerFactory.getLogger(HandleAcceptanceBehaviour.class);

    /**
     * The warehouse model used to query and update stock information.
     */
    private final WarehouseModel model;

    /**
     * Create the behaviour for the given agent and model.
     *
     * @param a     the agent that will run this behaviour
     * @param model the warehouse model used for stock operations
     */
    public HandleAcceptanceBehaviour(Agent a, WarehouseModel model) {
        super(a);
        this.model = model;
    }

    /**
     * Main behaviour loop.
     *
     * <p>Waits for ACCEPT_PROPOSAL messages. Each message is expected to
     * contain a JSON payload with keys:
     * <ul>
     *   <li>`productId` - SKU identifier</li>
     *   <li>`qty` - requested quantity as integer</li>
     * </ul>
     *
     * <p>On success sends an INFORM with a confirmation JSON:
     * `{ "status": "CONFIRMED", "productId": "...", "qty": ... }`
     *
     * <p>On failure sends a FAILURE with a JSON explaining the reason:
     * `{ "status": "FAILURE", "reason": "..." }`
     */
    @Override
    public void action() {
        // Match only ACCEPT_PROPOSAL performative messages
        MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
        ACLMessage msg = myAgent.receive(mt);

        if (msg != null) {
            // Parse the incoming JSON acceptance offer
            JSONObject acceptedOffer = new JSONObject(msg.getContent());
            String sku = acceptedOffer.getString("productId");
            int qty = acceptedOffer.getInt("qty");

            // Prepare a reply to the sender
            ACLMessage reply = msg.createReply();

            // Try to reserve the requested quantity in the model
            if (model.reserve(sku, qty)) {
                // Reservation succeeded: inform sender and update model state
                reply.setPerformative(ACLMessage.INFORM);
                JSONObject confirmationContent = new JSONObject();
                confirmationContent.put("status", "CONFIRMED");
                confirmationContent.put("productId", sku);
                confirmationContent.put("qty", qty);
                reply.setContent(confirmationContent.toString());

                myAgent.send(reply);
                log.info("{} confirmed and reserved {} units of {} for {}",
                        myAgent.getLocalName(), qty, sku, msg.getSender().getLocalName());

                // Persist confirmation in model (e.g. decrement available, increment confirmed out)
                model.confirmOut(sku, qty);
                log.info("{} stock updated for {}. New available: {}", myAgent.getLocalName(), sku, model.getAvailable(sku));

            } else {
                // Reservation failed: inform sender with failure reason and record lost offer
                reply.setPerformative(ACLMessage.FAILURE);
                JSONObject failureContent = new JSONObject();
                failureContent.put("status", "FAILURE");
                failureContent.put("reason", "Stock for " + sku + " is no longer available.");
                reply.setContent(failureContent.toString());

                myAgent.send(reply);
                model.incrLostOffer();
                log.warn("{} failed to reserve stock for {}. Sent FAILURE.", myAgent.getLocalName(), msg.getSender().getLocalName());
            }

        } else {
            // No matching message available: block until a new message arrives
            block();
        }
    }
}