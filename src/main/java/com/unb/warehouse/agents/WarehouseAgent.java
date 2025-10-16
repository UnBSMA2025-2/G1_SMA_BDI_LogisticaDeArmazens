package com.unb.warehouse.agents;

import com.unb.warehouse.behaviours.*;
import com.unb.warehouse.model.WarehouseModel;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WarehouseAgent extends Agent {
    private static final Logger log = LoggerFactory.getLogger(WarehouseAgent.class);
    private WarehouseModel model;
    private JSONObject decisionWeights;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args == null || args.length < 2) {
            log.error("Agent {} requires two arguments: its JSON config and the decision weights JSON config.", getLocalName());
            doDelete();
            return;
        }

        JSONObject whConfig = new JSONObject((String) args[0]);
        this.decisionWeights = new JSONObject((String) args[1]);

        // Initialize model from configuration
        this.model = new WarehouseModel(
                whConfig.getString("id"),
                whConfig.getDouble("lat"),
                whConfig.getDouble("lon")
        );
        model.setBaseCost(whConfig.getDouble("baseCost"));
        JSONObject stock = whConfig.getJSONObject("initialStock");
        for (String sku : stock.keySet()) {
            model.setStock(sku, stock.getInt(sku));
        }

        registerService();

        JSONObject reorderPolicy = whConfig.getJSONObject("reorderPolicy");
        addBehaviour(new StockMonitorBehaviour(this, 5000, model, reorderPolicy));
        addBehaviour(new OfferResponderBehaviour(this, model, reorderPolicy));
        addBehaviour(new HandleDecisionBehaviour(this, model, decisionWeights));
        addBehaviour(new HandleAcceptanceBehaviour(this, model));
        addBehaviour(new HandleTransactionFinalizationBehaviour(this, model));

        log.info("{} ready. coords={},{} stock(SKU-123)={} baseCost={}",
                model.getId(), model.getLat(), model.getLon(), model.getStock("SKU-123"), model.getBaseCost());
    }

    private void registerService() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("warehouse-service");
        sd.setName(getLocalName() + "-warehouse-service");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            log.info("Agent {} registered service 'warehouse-service'", getLocalName());
        } catch (FIPAException fe) {
            log.error("Failed to register service for agent {}: {}", getLocalName(), fe.getMessage());
        }
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
            log.info("Agent {} deregistered from DF.", getLocalName());
        } catch (FIPAException fe) {
            log.error("Failed to deregister agent {}: {}", getLocalName(), fe.getMessage());
        }
    }
}