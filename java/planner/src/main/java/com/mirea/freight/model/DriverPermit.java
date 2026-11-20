package com.mirea.freight.model;

public record DriverPermit(int driverId, int permitId, java.time.LocalDate validUntil) {
}
