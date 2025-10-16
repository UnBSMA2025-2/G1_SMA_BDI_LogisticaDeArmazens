package com.unb.warehouse.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a warehouse with a simple thread-safe inventory model.
 *
 * <p>Each warehouse has an identifier, geographic coordinates and maintains two
 * concurrent maps: one for current inventory levels and one for reserved quantities.
 * Some operations synchronize on the {@code inventory} map to ensure atomic checks
 * and updates that touch both maps.</p>
 *
 * <p>Fields {@code baseCost} and {@code lostOffers} are declared {@code volatile}
 * to provide visibility across threads for simple reads/writes. For compound
 * updates that involve inventory/reservation consistency, explicit synchronization
 * is used.</p>
 *
 * @author AlefMemTav
 */
public class WarehouseModel {
    private final String id;
    private final double lat;
    private final double lon;
    private final Map<String, Integer> inventory = new ConcurrentHashMap<>();
    private final Map<String, Integer> reserved = new ConcurrentHashMap<>();
    private volatile double baseCost;
    private volatile int lostOffers = 0; // metric for adaptive behaviour

    /**
     * Create a warehouse model.
     *
     * @param id  unique identifier for the warehouse
     * @param lat latitude coordinate
     * @param lon longitude coordinate
     */
    public WarehouseModel(String id, double lat, double lon) {
        this.id = id;
        this.lat = lat;
        this.lon = lon;
    }

    /**
     * Get the warehouse identifier.
     *
     * @return warehouse id
     */
    public String getId() {
        return id;
    }

    /**
     * Get the warehouse latitude.
     *
     * @return latitude
     */
    public double getLat() {
        return lat;
    }

    /**
     * Get the warehouse longitude.
     *
     * @return longitude
     */
    public double getLon() {
        return lon;
    }

    /**
     * Set the warehouse base cost used for pricing/selection heuristics.
     *
     * @param c base cost value
     */
    public void setBaseCost(double c) {
        this.baseCost = c;
    }

    /**
     * Get the warehouse base cost.
     *
     * @return base cost
     */
    public double getBaseCost() {
        return baseCost;
    }

    /**
     * Set the absolute stock level for a SKU.
     *
     * @param sku product identifier
     * @param qty quantity to set (overwrites existing value)
     */
    public void setStock(String sku, int qty) {
        inventory.put(sku, qty);
    }

    /**
     * Get the current stock level for a SKU.
     *
     * @param sku product identifier
     * @return stock quantity (0 if absent)
     */
    public int getStock(String sku) {
        return inventory.getOrDefault(sku, 0);
    }

    /**
     * Get the currently available quantity for a SKU (stock minus reserved).
     *
     * @param sku product identifier
     * @return available quantity (may be 0)
     */
    public int getAvailable(String sku) {
        return getStock(sku) - reserved.getOrDefault(sku, 0);
    }

    /**
     * Attempt to reserve a quantity of a SKU.
     *
     * <p>This method synchronizes on the {@code inventory} map to ensure the check
     * and reservation update happen atomically with respect to other reservation
     * or stock-modifying operations that also synchronize on the same monitor.</p>
     *
     * @param sku product identifier
     * @param qty quantity to reserve
     * @return {@code true} if the reservation succeeded, {@code false} otherwise
     */
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

    /**
     * Release a previously reserved quantity for a SKU.
     *
     * <p>If the release amount exceeds the current reserved amount, the reserved
     * value is clamped to 0.</p>
     *
     * @param sku product identifier
     * @param qty quantity to release
     */
    public void releaseReservation(String sku, int qty) {
        synchronized (inventory) {
            int currentReserved = reserved.getOrDefault(sku, 0);
            reserved.put(sku, Math.max(0, currentReserved - qty));
        }
    }

    /**
     * Confirm an outbound shipment: reduce stock and decrease reserved quantity.
     *
     * <p>Both the inventory and reserved maps are updated together under the same
     * synchronization monitor to keep them consistent.</p>
     *
     * @param sku product identifier
     * @param qty quantity shipped out
     */
    public void confirmOut(String sku, int qty) {
        synchronized (inventory) {
            inventory.put(sku, Math.max(0, getStock(sku) - qty));
            int res = reserved.getOrDefault(sku, 0) - qty;
            reserved.put(sku, Math.max(0, res));
        }
    }

    /**
     * Receive incoming stock for a SKU (adds to existing stock).
     *
     * @param sku product identifier
     * @param qty quantity received
     */
    public void receiveIn(String sku, int qty) {
        synchronized (inventory) {
            inventory.put(sku, getStock(sku) + qty);
        }
    }

    /**
     * Increment the counter tracking lost offers (used for adaptive behaviour).
     */
    public void incrLostOffer() {
        lostOffers++;
    }

    /**
     * Get the number of lost offers recorded for this warehouse.
     *
     * @return lost offers count
     */
    public int getLostOffers() {
        return lostOffers;
    }
}