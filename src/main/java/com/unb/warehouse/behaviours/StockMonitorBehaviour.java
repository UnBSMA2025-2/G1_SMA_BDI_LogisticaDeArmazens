package com.unb.warehouse.behaviours;

import com.unb.warehouse.model.WarehouseModel;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StockMonitorBehaviour extends TickerBehaviour {
    private static final Logger log = LoggerFactory.getLogger(StockMonitorBehaviour.class);

    private final WarehouseModel model;
    private final JSONObject reorderPolicy;

    public StockMonitorBehaviour(Agent a, long period, WarehouseModel model, JSONObject reorderPolicy) {
        super(a, period);
        this.model = model;
        this.reorderPolicy = reorderPolicy;
    }

    @Override
    protected void onTick() {
        String sku = reorderPolicy.getString("sku");
        int triggerLevel = reorderPolicy.getInt("triggerLevel");
        int reorderQty = reorderPolicy.getInt("reorderQuantity");

        int available = model.getAvailable(sku);
        if (available < triggerLevel) {
            log.info("{} stock for {} is low ({} < {}). Sending CFP.", myAgent.getLocalName(), sku, available, triggerLevel);

            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            cfp.setConversationId("supply-negotiation-" + System.currentTimeMillis());

            JSONObject content = new JSONObject();
            content.put("productId", sku);
            content.put("qty", reorderQty);
            content.put("requesterId", model.getId());
            content.put("requesterLat", model.getLat());
            content.put("requesterLon", model.getLon());
            cfp.setContent(content.toString());

            // Find other warehouses using Directory Facilitator
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("warehouse-service");
            template.addServices(sd);

            try {
                DFAgentDescription[] result = DFService.search(myAgent, template);
                if (result.length > 0) {
                    for (DFAgentDescription dfd : result) {
                        AID provider = dfd.getName();
                        // Do not send CFP to self
                        if (!provider.equals(myAgent.getAID())) {
                            cfp.addReceiver(provider);
                        }
                    }
                } else {
                    log.warn("{} found no other warehouse services.", myAgent.getLocalName());
                }
            } catch (FIPAException fe) {
                log.error("Error searching DF: {}", fe.getMessage());
            }

            if (cfp.getAllReceiver().hasNext()) { // Check if there are any receivers
                myAgent.send(cfp);
                log.info("{} sent CFP for {} units of {}.", myAgent.getLocalName(), reorderQty, sku);
            }
        }
    }
}