package com.unb.warehouse.behaviours;

import com.unb.warehouse.model.WarehouseModel;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Behaviour that listens for proposals (offers) from supplier agents,
 * collects proposals within a short time window, evaluates them using
 * configurable weights, accepts the best proposal and rejects the others.
 * <p>
 * The evaluation uses a weighted sum of normalized sub-scores for cost,
 * lead time, distance and reliability.
 *
 * @author AlefMemTav
 */
public class HandleDecisionBehaviour extends CyclicBehaviour {
    private static final Logger log = LoggerFactory.getLogger(HandleDecisionBehaviour.class);

    /**
     * Time window in milliseconds to collect proposals after the first arrives.
     */
    private static final long OFFER_WAIT_MS = 2000; // Window to collect proposals

    private final WarehouseModel model;
    private final JSONObject weights;

    /**
     * Create behaviour.
     *
     * @param a       owning agent
     * @param model   reference to warehouse model (not used directly here but kept for potential extension)
     * @param weights JSON object with weight values for the scoring function. Expected keys:
     *                "cost", "time", "distance", "reliability"
     */
    public HandleDecisionBehaviour(Agent a, WarehouseModel model, JSONObject weights) {
        super(a);
        this.model = model;
        this.weights = weights;
    }

    /**
     * Main loop: wait for a proposal performative, then collect additional proposals
     * that arrive within the configured time window. Score each proposal and accept
     * the highest scoring one. Send explicit rejects to all other proposals.
     */
    @Override
    public void action() {
        // Only accept messages with the PROPOSE performative
        MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
        ACLMessage firstProposal = myAgent.receive(mt);

        if (firstProposal != null) {
            // Collect proposals, starting with the first received
            List<ACLMessage> collected = new ArrayList<>();
            collected.add(firstProposal);

            // Collect all proposals within the time window
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < OFFER_WAIT_MS) {
                ACLMessage proposal = myAgent.receive(mt);
                if (proposal != null) {
                    collected.add(proposal);
                } else {
                    // Yield the behaviour for a short period to avoid busy-waiting
                    block(50);
                }
            }

            // Choose the best proposal according to the scoring function
            ACLMessage bestOffer = null;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (ACLMessage proposal : collected) {
                double score = scoreFor(proposal);
                if (score > bestScore) {
                    bestScore = score;
                    bestOffer = proposal;
                }
            }

            if (bestOffer != null) {
                // Accept the best offer by replying with ACCEPT_PROPOSAL
                JSONObject chosenOffer = new JSONObject(bestOffer.getContent()); // kept for possible future use
                ACLMessage accept = bestOffer.createReply();
                accept.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                // Include the original offer content in the acceptance message
                accept.setContent(bestOffer.getContent());
                myAgent.send(accept);

                // Reject all other proposals explicitly
                for (ACLMessage proposal : collected) {
                    if (proposal != bestOffer) {
                        ACLMessage reject = proposal.createReply();
                        reject.setPerformative(ACLMessage.REJECT_PROPOSAL);
                        myAgent.send(reject);
                    }
                }

                // Log the decision with score and number of rejected proposals
                log.info("{} accepted offer from {} (score: {:.4f}). Rejecting {} others.",
                        myAgent.getLocalName(), bestOffer.getSender().getLocalName(), bestScore, collected.size() - 1);
            }
        } else {
            // No proposal available: block until a new message arrives
            block();
        }
    }

    /**
     * Computes a numeric score for a proposal message.
     * <p>
     * Expected message content is a JSON string with numeric fields:
     * - unitCost
     * - leadTimeHours
     * - distanceKm
     * - reliability
     * <p>
     * The method normalizes each criterion into a sub-score where higher is better:
     * - cost, time and distance are inverted so lower values produce higher sub-scores
     * - reliability is used directly (assumed between 0 and 1)
     * <p>
     * The final score is a weighted sum using the {@code weights} provided to the behaviour.
     *
     * @param p proposal message containing a JSON string as content
     * @return weighted score where higher values indicate more favorable offers
     */
    private double scoreFor(ACLMessage p) {
        JSONObject offer = new JSONObject(p.getContent());
        double cost = offer.getDouble("unitCost");
        double time = offer.getDouble("leadTimeHours");
        double distance = offer.getDouble("distanceKm");
        double reliability = offer.getDouble("reliability");

        // Weights loaded from config
        double wCost = weights.getDouble("cost");
        double wTime = weights.getDouble("time");
        double wDistance = weights.getDouble("distance");
        double wReliability = weights.getDouble("reliability");

        // Normalized sub-scores: higher is better
        double scCost = 1.0 / (cost + 0.001); // lower cost => higher score, avoid division by zero
        double scTime = 1.0 / (time + 0.001); // lower lead time => higher score
        double scDist = 1.0 / (distance + 1); // add 1 to avoid overly large scores for very small distances
        double scRel = reliability; // assumed already normalized

        return wCost * scCost + wTime * scTime + wDistance * scDist + wReliability * scRel;
    }
}