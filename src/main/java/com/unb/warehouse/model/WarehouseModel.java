package com.unb.warehouse.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WarehouseModel {
    private final String id;
    private final double lat;
    private final double lon;
    private final Map<String, Integer> inventory = new ConcurrentHashMap<>();
    private final Map<String, Integer> reserved = new ConcurrentHashMap<>();
    private volatile double baseCost;
    private volatile int lostOffers = 0; // metric for adaptive behaviour

    public WarehouseModel(String id, double lat, double lon) {
        this.id = id;
        this.lat = lat;
        this.lon = lon;
    }

    public String getId() {
        return id;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public void setBaseCost(double c) {
        this.baseCost = c;
    }

    public double getBaseCost() {
        return baseCost;
    }

    public void setStock(String sku, int qty) {
        inventory.put(sku, qty);
    }

    public int getStock(String sku) {
        return inventory.getOrDefault(sku, 0);
    }

    public int getAvailable(String sku) {
        return getStock(sku) - reserved.getOrDefault(sku, 0);
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

    public void confirmOut(String sku, int qty) {
        synchronized (inventory) {
            inventory.put(sku, Math.max(0, getStock(sku) - qty));
            int res = reserved.getOrDefault(sku, 0) - qty;
            reserved.put(sku, Math.max(0, res));
        }
    }

    public void receiveIn(String sku, int qty) {
        synchronized (inventory) {
            inventory.put(sku, getStock(sku) + qty);
        }
    }

    public void incrLostOffer() {
        lostOffers++;
    }

    public int getLostOffers() {
        return lostOffers;
    }
}