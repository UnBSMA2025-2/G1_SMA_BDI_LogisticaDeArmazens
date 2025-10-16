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

public class HandleDecisionBehaviour extends CyclicBehaviour {
    private static final Logger log = LoggerFactory.getLogger(HandleDecisionBehaviour.class);
    private static final long OFFER_WAIT_MS = 2000; // Window to collect proposals

    private final WarehouseModel model;
    private final JSONObject weights;

    public HandleDecisionBehaviour(Agent a, WarehouseModel model, JSONObject weights) {
        super(a);
        this.model = model;
        this.weights = weights;
    }

    @Override
    public void action() {
        MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
        ACLMessage firstProposal = myAgent.receive(mt);

        if (firstProposal != null) {
            List<ACLMessage> collected = new ArrayList<>();
            collected.add(firstProposal);

            // Collect all proposals within the time window
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < OFFER_WAIT_MS) {
                ACLMessage proposal = myAgent.receive(mt);
                if (proposal != null) {
                    collected.add(proposal);
                } else {
                    block(50); // Wait a bit for more messages
                }
            }

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
                // Accept the best offer
                JSONObject chosenOffer = new JSONObject(bestOffer.getContent());
                ACLMessage accept = bestOffer.createReply();
                accept.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                // Content of acceptance is the original offer that is being accepted
                accept.setContent(bestOffer.getContent());
                myAgent.send(accept);

                // Reject all other offers
                for (ACLMessage proposal : collected) {
                    if (proposal != bestOffer) {
                        ACLMessage reject = proposal.createReply();
                        reject.setPerformative(ACLMessage.REJECT_PROPOSAL);
                        myAgent.send(reject);
                    }
                }
                log.info("{} accepted offer from {} (score: {:.4f}). Rejecting {} others.",
                        myAgent.getLocalName(), bestOffer.getSender().getLocalName(), bestScore, collected.size() - 1);
            }
        } else {
            block();
        }
    }

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
        double scCost = 1.0 / (cost + 0.001); // lower cost => higher score
        double scTime = 1.0 / (time + 0.001); // lower time => higher
        double scDist = 1.0 / (distance + 1); // avoid div by zero
        double scRel = reliability;

        return wCost * scCost + wTime * scTime + wDistance * scDist + wReliability * scRel;
    }
}