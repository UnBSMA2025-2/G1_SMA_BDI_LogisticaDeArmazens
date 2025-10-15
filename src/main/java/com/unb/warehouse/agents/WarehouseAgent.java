package com.unb.warehouse.agents;

import jade.core.Agent;

import java.util.HashMap;
import java.util.Map;

import com.unb.warehouse.behaviours.StockMonitorBehaviour;
import com.unb.warehouse.behaviours.OfferResponderBehaviour;
import com.unb.warehouse.behaviours.HandleDecisionBehaviour;

public class WarehouseAgent extends Agent {
    private final Map<String, Integer> inventory = new HashMap<>();
    private Map<String, Integer> reserved = new HashMap<>();
    private double lat;
    private double lon;
    private String id;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        this.id = args != null && args.length > 0 ? (String) args[0] : getLocalName();
        this.lat = args != null && args.length > 1 ? Double.parseDouble((String) args[1]) : 0.0;
        this.lon = args != null && args.length > 2 ? Double.parseDouble((String) args[2]) : 0.0;

        // inventário exemplo
        inventory.put("SKU-123", 100);
        inventory.put("SKU-456", 500);

        // behaviours
        addBehaviour(new StockMonitorBehaviour(this, 5000)); // tick 5s
        addBehaviour(new OfferResponderBehaviour(this));
        addBehaviour(new HandleDecisionBehaviour(this));


        System.out.println(getLocalName() + " ready. coords=" + lat + "," + lon);
    }

    // getters/setters simples
    public int getAvailable(String sku) {
        return inventory.getOrDefault(sku, 0) - reserved.getOrDefault(sku, 0);
    }

    public boolean reserve(String sku, int qty) {
        synchronized (inventory) {
            int avail = getAvailable(sku);
            if (avail >= qty) {
                reserved.put(sku, reserved.getOrDefault(sku, 0) + qty);
                return true;
            }
            return false;
        }
    }

    public void confirmTransferOut(String sku, int qty) {
        synchronized (inventory) {
            int cur = inventory.getOrDefault(sku, 0);
            inventory.put(sku, Math.max(0, cur - qty));
            int res = reserved.getOrDefault(sku, 0) - qty;
            reserved.put(sku, Math.max(0, res));
        }
    }

    public void receiveTransfer(String sku, int qty) {
        synchronized (inventory) {
            inventory.put(sku, inventory.getOrDefault(sku, 0) + qty);
        }
    }

    public String getWarehouseId(){
        return id;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }
}