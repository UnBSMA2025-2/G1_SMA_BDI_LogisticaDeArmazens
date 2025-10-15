package com.unb.warehouse.behaviours;

import com.unb.warehouse.agents.WarehouseAgent;
import com.unb.warehouse.model.WarehouseModel;
import com.unb.warehouse.util.GeoUtil;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OfferResponderBehaviour extends CyclicBehaviour {
    private static final Logger log = LoggerFactory.getLogger(OfferResponderBehaviour.class);

    public OfferResponderBehaviour(WarehouseAgent warehouseAgent) {
    }

    @Override
    public void action() {
        ACLMessage msg = myAgent.receive();
        if (msg != null && msg.getPerformative() == ACLMessage.CFP) {
            try {
                WarehouseAgent wa = (WarehouseAgent) myAgent;
                WarehouseModel m = wa.getModel();
                JSONObject req = new JSONObject(msg.getContent());
                String sku = req.getString("productId");
                int qtyReq = req.getInt("qty");
                String requester = req.getString("requester");

                int available = m.getAvailable(sku);
                if (available <= 0) return;

                int qtyOffer = Math.min(available, Math.min(qtyReq, 150));
                double unitCost = m.getBaseCost() + Math.random(); // base + small random

                double reqLat = getLatFor(requester);
                double reqLon = getLonFor(requester);

                double distance = GeoUtil.haversine(m.getLat(), m.getLon(), reqLat, reqLon);
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
                offer.put("responder", m.getId());

                propose.setContent(offer.toString());
                myAgent.send(propose);

                log.info("{} proposed qty={} cost={} dist={}", m.getId(), qtyOffer, unitCost, String.format("%.1f", distance));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            block();
        }
    }

    // helper: in demo we map names to coords; replace with DF lookup in real system
    private double getLatFor(String id) {
        switch (id) {
            case "wh1":
                return -23.55;
            case "wh2":
                return -22.90;
            case "wh3":
                return -19.92;
            default:
                return 0;
        }
    }

    private double getLonFor(String id) {
        switch (id) {
            case "wh1":
                return -46.63;
            case "wh2":
                return -43.20;
            case "wh3":
                return -43.94;
            default:
                return 0;
        }
    }
}