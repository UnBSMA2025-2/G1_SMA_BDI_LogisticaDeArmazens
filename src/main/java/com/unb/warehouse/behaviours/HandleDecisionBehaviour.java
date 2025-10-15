package com.unb.warehouse.behaviours;


import com.unb.warehouse.agents.WarehouseAgent;
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
    private static final long OFFER_WAIT_MS = 1500; // janela de coleta de propostas

    public HandleDecisionBehaviour(WarehouseAgent warehouseAgent) {
    }

    @Override
    public void action() {
        ACLMessage msg = myAgent.receive(MessageTemplate.MatchPerformative(ACLMessage.PROPOSE));
        if (msg != null) {
            List<ACLMessage> collected = new ArrayList<>();
            collected.add(msg);
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < OFFER_WAIT_MS) {
                ACLMessage m = myAgent.receive(MessageTemplate.MatchPerformative(ACLMessage.PROPOSE));
                if (m != null) collected.add(m);
                else block(50);
            }

            ACLMessage best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (ACLMessage p : collected) {
                double score = scoreFor(p);
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }

            if (best != null) {
                JSONObject chosen = new JSONObject(best.getContent());
                ACLMessage accept = best.createReply();
                accept.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                JSONObject payload = new JSONObject();
                payload.put("productId", chosen.getString("productId"));
                payload.put("qty", Math.min(chosen.getInt("qtyAvailable"), 200));
                accept.setContent(payload.toString());
                myAgent.send(accept);

                // reject others
                for (ACLMessage p : collected)
                    if (p != best) {
                        ACLMessage rej = p.createReply();
                        rej.setPerformative(ACLMessage.REJECT_PROPOSAL);
                        myAgent.send(rej);
                    }
                log.info("{} accepted offer from {} score={}", myAgent.getLocalName(), best.getSender().getLocalName(), bestScore);
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

        // weights loaded from config could be injected; hardcoded sample here
        double wCost = 0.5, wTime = 0.25, wDistance = 0.15, wReliability = 0.1;

        // normalized sub-scores: higher is better
        double scCost = 1.0 / cost; // lower cost => higher score
        double scTime = 1.0 / time; // lower time => higher
        double scDist = 1.0 / (distance + 1); // avoid div by zero
        double scRel = reliability; // already [0..1]

        return wCost * scCost + wTime * scTime + wDistance * scDist + wReliability * scRel;
    }
}