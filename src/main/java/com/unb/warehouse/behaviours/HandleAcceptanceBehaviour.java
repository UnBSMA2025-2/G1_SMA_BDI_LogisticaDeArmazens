package com.unb.warehouse.behaviours;

import com.unb.warehouse.model.WarehouseModel;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HandleAcceptanceBehaviour extends CyclicBehaviour {
    private static final Logger log = LoggerFactory.getLogger(HandleAcceptanceBehaviour.class);
    private final WarehouseModel model;

    public HandleAcceptanceBehaviour(Agent a, WarehouseModel model) {
        super(a);
        this.model = model;
    }

    @Override
    public void action() {
        MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
        ACLMessage msg = myAgent.receive(mt);

        if (msg != null) {
            JSONObject acceptedOffer = new JSONObject(msg.getContent());
            String sku = acceptedOffer.getString("productId");
            int qty = acceptedOffer.getInt("qty");

            ACLMessage reply = msg.createReply();

            if (model.reserve(sku, qty)) {
                reply.setPerformative(ACLMessage.INFORM);
                JSONObject confirmationContent = new JSONObject();
                confirmationContent.put("status", "CONFIRMED");
                confirmationContent.put("productId", sku);
                confirmationContent.put("qty", qty);
                reply.setContent(confirmationContent.toString());

                myAgent.send(reply);
                log.info("{} confirmed and reserved {} units of {} for {}",
                        myAgent.getLocalName(), qty, sku, msg.getSender().getLocalName());

                model.confirmOut(sku, qty);
                log.info("{} stock updated for {}. New available: {}", myAgent.getLocalName(), sku, model.getAvailable(sku));

            } else {
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
            block();
        }
    }
}