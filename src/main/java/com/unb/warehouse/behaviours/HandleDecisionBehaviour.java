package com.unb.warehouse.behaviours;

import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.json.JSONObject;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

import com.unb.warehouse.agents.WarehouseAgent;
import com.unb.warehouse.util.GeoUtil;

public class HandleDecisionBehaviour extends CyclicBehaviour {
    private static final long OFFER_WAIT_MS = 1500; // janela de coleta de propostas

    public HandleDecisionBehaviour(WarehouseAgent warehouseAgent) {
    }

    @Override
    public void action() {
        // coleta mensagens PROPOSE relacionadas a um CFP recente
        ACLMessage msg = myAgent.receive(MessageTemplate.MatchPerformative(ACLMessage.PROPOSE));
        if (msg != null) {
            // armazena e espera uma pequena janela para coletar mais
            List<ACLMessage> collected = new ArrayList<>();
            collected.add(msg);
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < OFFER_WAIT_MS) {
                ACLMessage m = myAgent.receive(MessageTemplate.MatchPerformative(ACLMessage.PROPOSE));
                if (m != null) collected.add(m);
                else block(100);
            }

            // avalia propostas
            double bestScore = -1;
            ACLMessage best = null;
            for (ACLMessage p : collected) {
                double score = getScore(p);

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

                // envia rejeições para os demais
                for (ACLMessage p : collected) {
                    if (p != best) {
                        ACLMessage rej = p.createReply();
                        rej.setPerformative(ACLMessage.REJECT_PROPOSAL);
                        myAgent.send(rej);
                    }
                }

                System.out.println(myAgent.getLocalName() + " accepted offer from " + best.getSender().getLocalName());
            }
        } else {
            block();
        }
    }

    private static double getScore(ACLMessage p) {
        JSONObject offer = new JSONObject(p.getContent());
        double cost = offer.getDouble("unitCost");
        double time = offer.getDouble("leadTimeHours");
        double distance = offer.getDouble("distanceKm");
        double reliability = offer.getDouble("reliability");

        // pesos (configuráveis)
        double wCost = 0.5, wTime = 0.25, wDistance = 0.15, wReliability = 0.1;
        double score = (wCost * (1.0 / cost)) + (wTime * (1.0 / time)) + (wDistance * (1.0 / (distance + 1))) + (wReliability * reliability);
        return score;
    }
}