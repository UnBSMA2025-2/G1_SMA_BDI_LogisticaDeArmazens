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

public class OfferResponderBehaviour extends CyclicBehaviour {
    private static final Logger log = LoggerFactory.getLogger(OfferResponderBehaviour.class);
    private final WarehouseModel model;
    private final JSONObject reorderPolicy;

    public OfferResponderBehaviour(Agent a, WarehouseModel model, JSONObject reorderPolicy) {
        super(a);
        this.model = model;
        this.reorderPolicy = reorderPolicy;
    }

    @Override
    public void action() {
        MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
        ACLMessage msg = myAgent.receive(mt);

        if (msg != null) {
            try {
                JSONObject req = new JSONObject(msg.getContent());
                String sku = req.getString("productId");

                int available = model.getAvailable(sku);
                int myTriggerLevel = reorderPolicy.getInt("triggerLevel");

                // Não faz uma oferta se o estoque disponível já estiver no nível de alerta ou abaixo dele.
                if (available <= myTriggerLevel) {
                    log.info("{} has stock ({}) but it is not above its trigger level ({}). Refusing offer to {}.",
                            model.getId(), available, myTriggerLevel, req.getString("requesterId"));
                    return; // Ignora o pedido para proteger o próprio estoque.
                }

                int qtyReq = req.getInt("qty");
                double reqLat = req.getDouble("requesterLat");
                double reqLon = req.getDouble("requesterLon");

                // Oferece no máximo o que tem disponível acima do nível de segurança
                int maxOfferable = available - myTriggerLevel;
                int qtyOffer = Math.min(maxOfferable, qtyReq);

                if (qtyOffer <= 0) {
                    return; // Não há quantidade suficiente para oferecer.
                }

                double unitCost = model.getBaseCost() + (Math.random() * 0.5 - 0.25);
                double distance = GeoUtil.haversine(model.getLat(), model.getLon(), reqLat, reqLon);
                double leadTime = Math.max(1.0, distance / 40.0);
                double reliability = 0.9 + Math.random() * 0.1;

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
                log.error("Error processing CFP in {}: {}", myAgent.getLocalName(), e.getMessage());
            }
        } else {
            block();
        }
    }
}