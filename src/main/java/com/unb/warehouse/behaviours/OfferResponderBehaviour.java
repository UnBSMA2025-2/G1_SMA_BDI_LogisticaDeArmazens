package com.unb.warehouse.behaviours;

import com.unb.warehouse.agents.WarehouseAgent;
import com.unb.warehouse.util.GeoUtil;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import org.json.JSONObject;

public class OfferResponderBehaviour extends CyclicBehaviour {

    public OfferResponderBehaviour(WarehouseAgent warehouseAgent) {
    }

    @Override
    public void action() {
        ACLMessage msg = myAgent.receive();
        if (msg != null && msg.getPerformative() == ACLMessage.CFP) {
            try {
                WarehouseAgent wa = (WarehouseAgent) myAgent;
                JSONObject req = new JSONObject(msg.getContent());
                String sku = req.getString("productId");
                int qtyReq = req.getInt("qty");
                String requester = req.getString("requester");

                int available = wa.getAvailable(sku);
                if (available <= 0) return;

                int qtyOffer = Math.min(available, Math.min(qtyReq, 150));
                double unitCost = 3.0 + Math.random(); // exemplo

                // distância entre agentes (pega coords do requester por convenção de nomes simples)
                double reqLat = 0, reqLon = 0;
                if (requester.equals("wh1")) {
                    reqLat = -23.55;
                    reqLon = -46.63;
                }
                if (requester.equals("wh2")) {
                    reqLat = -22.90;
                    reqLon = -43.20;
                }
                if (requester.equals("wh3")) {
                    reqLat = -19.92;
                    reqLon = -43.94;
                }

                double distance = GeoUtil.haversine(wa.getLat(), wa.getLon(), reqLat, reqLon);
                double leadTime = Math.max(1, Math.ceil(distance / 60.0 * 24)); // simplificação
                double reliability = 0.9 + Math.random() * 0.1;

                ACLMessage propose = msg.createReply();
                propose.setPerformative(ACLMessage.PROPOSE);
                JSONObject offer = new JSONObject();
                offer.put("productId", sku);
                offer.put("qtyAvailable", qtyOffer);
                offer.put("unitCost", unitCost);
                offer.put("leadTimeHours", leadTime);
                offer.put("distanceKm", distance);
                offer.put("reliability", reliability);
                offer.put("responder", wa.getWarehouseId());

                propose.setContent(offer.toString());
                myAgent.send(propose);

                System.out.println(wa.getLocalName() + " proposed qty=" + qtyOffer + " cost=" + unitCost + " dist=" + String.format("%.1f", distance));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            block();
        }
    }
}