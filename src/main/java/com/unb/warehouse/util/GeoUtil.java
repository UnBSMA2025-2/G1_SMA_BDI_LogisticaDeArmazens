package com.unb.warehouse.util;

/**
 * Utility class for geographic calculations.
 *
 * <p>Provides methods to compute distances on the Earth's surface. Current implementation
 * uses the haversine formula to calculate great-circle distances between two points
 * specified by latitude and longitude in decimal degrees.
 *
 * @author AlefMemTav
 */
public class GeoUtil {

    private static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Calculates the great-circle distance between two geographic coordinates using the
     * haversine formula.
     *
     * @param lat1 latitude of the first point in decimal degrees
     * @param lon1 longitude of the first point in decimal degrees
     * @param lat2 latitude of the second point in decimal degrees
     * @param lon2 longitude of the second point in decimal degrees
     * @return distance between the two points in kilometers
     */
    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}