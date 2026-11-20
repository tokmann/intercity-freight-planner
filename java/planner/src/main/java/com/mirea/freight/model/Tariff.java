package com.mirea.freight.model;

public record Tariff(int tariffId, int bodyTypeId, double distFromKm, double distToKm, double ratePerKm, double minCost, double dangerSurchargePct) {

    public double baseCost(double distance) {
        return Math.max(distance * ratePerKm, minCost);
    }
}
