package com.unb.warehouse.behaviours;

import com.unb.warehouse.model.WarehouseModel;
import com.unb.warehouse.util.GeoUtil;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Behaviour that listens for Call-For-Proposals (CFP) messages and responds with a PROPOSE
 * when the warehouse can offer stock without dropping below its configured safety level.
 * <p>
 * Responsibilities:
 * - Parse incoming CFP messages containing a product request.
 * - Determine available quantity above the warehouse's reorder trigger level.
 * - Build and send a PROPOSE message with quantity, unit cost, estimated lead time,
 * distance and reliability when appropriate.
 * <p>
 * This behaviour is cyclic: it continuously receives CFP messages and replies or ignores them.
 *
 * @author AlefMemTav
 */
public class OfferResponderBehaviour extends CyclicBehaviour {
    private static final Logger log = LoggerFactory.getLogger(OfferResponderBehaviour.class);

    /**
     * Model representing this warehouse (id, location, stock levels, base cost, ...).
     */
    private final WarehouseModel model;

    /**
     * JSON object that contains the reorder policy parameters for this warehouse.
     * Expected to include:
     * - "triggerLevel" : int (safety stock level below which the warehouse should not offer)
     */
    private final JSONObject reorderPolicy;

    /**
     * Create a new OfferResponderBehaviour bound to the given agent, warehouse model and reorder policy.
     *
     * @param a             the JADE agent owning this behaviour
     * @param model         the WarehouseModel with stock and location data
     * @param reorderPolicy JSON object describing reorder policy (expects "triggerLevel")
     */
    public OfferResponderBehaviour(Agent a, WarehouseModel model, JSONObject reorderPolicy) {
        super(a);
        this.model = model;
        this.reorderPolicy = reorderPolicy;
    }

    /**
     * Main behaviour loop. Waits for CFP messages and, for each received CFP:
     * - Parses the request content (expects JSON with productId, qty, requesterId, requesterLat, requesterLon).
     * - Checks available stock and the warehouse's trigger level.
     * - If safe to offer, computes an offer with:
     * - qty: min(requested, available - triggerLevel)
     * - unitCost: baseCost with a small random perturbation
     * - distanceKm: Haversine distance to requester
     * - leadTimeHours: at least 1 hour; based on distance / average speed (40 km/h)
     * - reliability: a random value between 0.9 and 1.0
     * - Sends a PROPOSE reply containing the offer as JSON.
     * <p>
     * If any error occurs while processing the CFP, it is logged and the message is ignored.
     */
    @Override
    public void action() {
        MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
        ACLMessage msg = myAgent.receive(mt);

        if (msg != null) {
            try {
                // Parse incoming CFP JSON
                JSONObject req = new JSONObject(msg.getContent());
                String sku = req.getString("productId");

                int available = model.getAvailable(sku);
                int myTriggerLevel = reorderPolicy.getInt("triggerLevel");

                // Do not make an offer if available stock is at or below the trigger level.
                if (available <= myTriggerLevel) {
                    log.info("{} has stock ({}) but it is not above its trigger level ({}). Refusing offer to {}.",
                            model.getId(), available, myTriggerLevel, req.getString("requesterId"));
                    return; // Ignore the request to protect own stock.
                }

                int qtyReq = req.getInt("qty");
                double reqLat = req.getDouble("requesterLat");
                double reqLon = req.getDouble("requesterLon");

                // Offer at most the quantity available above the safety level.
                int maxOfferable = available - myTriggerLevel;
                int qtyOffer = Math.min(maxOfferable, qtyReq);

                if (qtyOffer <= 0) {
                    return; // Nothing safe to offer.
                }

                // Compute offer details
                double unitCost = model.getBaseCost() + (Math.random() * 0.5 - 0.25); // small random adjustment
                double distance = GeoUtil.haversine(model.getLat(), model.getLon(), reqLat, reqLon);
                double leadTime = Math.max(1.0, distance / 40.0); // assume 40 km/h average speed, min 1 hour
                double reliability = 0.9 + Math.random() * 0.1; // 0.90 - 1.00

                // Build and send the PROPOSE reply with offer JSON
                ACLMessage propose = msg.createReply();
                propose.setPerformative(ACLMessage.PROPOSE);
                JSONObject offer = new JSONObject();
                offer.put("productId", sku);
                offer.put("qty", qtyOffer);
                offer.put("unitCost", unitCost);
                offer.put("leadTimeHours", leadTime);
                offer.put("distanceKm", distance);
                offer.put("reliability", reliability);
                offer.put("responderId", model.getId());

                propose.setContent(offer.toString());
                myAgent.send(propose);

                log.info("{} proposed to {} with qty={}, cost={}, dist={}km",
                        model.getId(), req.getString("requesterId"), qtyOffer, String.format("%.2f", unitCost), String.format("%.1f", distance));

            } catch (Exception e) {
                // Log and ignore malformed messages or unexpected errors to keep the behaviour robust.
                log.error("Error processing CFP in {}: {}", myAgent.getLocalName(), e.getMessage());
            }
        } else {
            block(); // Wait until a new message arrives
        }
    }
}