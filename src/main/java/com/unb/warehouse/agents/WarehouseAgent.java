package com.unb.warehouse.agents;

import com.unb.warehouse.behaviours.HandleDecisionBehaviour;
import com.unb.warehouse.behaviours.OfferResponderBehaviour;
import com.unb.warehouse.behaviours.StockMonitorBehaviour;
import com.unb.warehouse.model.WarehouseModel;
import jade.core.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WarehouseAgent extends Agent {
    private static final Logger log = LoggerFactory.getLogger(WarehouseAgent.class);
    private WarehouseModel model;


    @Override
    protected void setup() {
        Object[] args = getArguments();
        String id = args != null && args.length > 0 ? (String) args[0] : getLocalName();
        double lat = args != null && args.length > 1 ? Double.parseDouble((String) args[1]) : 0.0;
        double lon = args != null && args.length > 2 ? Double.parseDouble((String) args[2]) : 0.0;


        model = new WarehouseModel(id, lat, lon); // initial values will be overridden by config loader in App; but keep defaults
        model.setBaseCost(3.0);
        model.setStock("SKU-123", 100);
        model.setStock("SKU-456", 500);

        addBehaviour(new StockMonitorBehaviour(this, 5000));
        addBehaviour(new OfferResponderBehaviour(this));
        addBehaviour(new HandleDecisionBehaviour(this));

        log.info("{} ready. coords={},{} stock(SKU-123)={} baseCost={}", id, lat, lon, model.getStock("SKU-123"), model.getBaseCost());
    }

    public WarehouseModel getModel() {
        return model;
    }
}