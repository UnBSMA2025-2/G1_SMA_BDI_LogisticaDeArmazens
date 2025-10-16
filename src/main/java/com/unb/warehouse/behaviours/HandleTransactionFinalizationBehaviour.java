package com.unb.warehouse.behaviours;

import com.unb.warehouse.model.WarehouseModel;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HandleTransactionFinalizationBehaviour extends CyclicBehaviour {
    private static final Logger log = LoggerFactory.getLogger(HandleTransactionFinalizationBehaviour.class);
    private final WarehouseModel model;

    public HandleTransactionFinalizationBehaviour(Agent a, WarehouseModel model) {
        super(a);
        this.model = model;
    }

    @Override
    public void action() {
        MessageTemplate mt = MessageTemplate.or(
                MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                MessageTemplate.MatchPerformative(ACLMessage.FAILURE)
        );
        ACLMessage msg = myAgent.receive(mt);

        if (msg != null) {
            if (msg.getPerformative() == ACLMessage.INFORM) {
                try {
                    // Ler o JSON e atualizar o estoque
                    JSONObject content = new JSONObject(msg.getContent());
                    String sku = content.getString("productId");
                    int qty = content.getInt("qty");

                    // Atualizar o invetário
                    model.receiveIn(sku, qty);

                    log.info("{} successfully received {} units of {}. New stock: {}",
                            myAgent.getLocalName(), qty, sku, model.getStock(sku));

                } catch (JSONException e) {
                    log.error("Error processing INFORM message content in {}: {}", myAgent.getLocalName(), e.getMessage());
                }

            } else if (msg.getPerformative() == ACLMessage.FAILURE) {
                log.warn("{}'s purchase was rejected by {} due to stock unavailability.",
                        myAgent.getLocalName(), msg.getSender().getLocalName());
                // Poderíamos reiniciar o processo de CFP aqui se quiséssemos.
            }

        } else {
            block();
        }
    }
}