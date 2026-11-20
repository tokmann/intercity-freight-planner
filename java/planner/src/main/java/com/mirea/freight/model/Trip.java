package com.mirea.freight.model;

public record Trip(int tripId, int requestId, int vehicleId, java.time.LocalDateTime departureTime, java.time.LocalDateTime arrivalTime, double cost, String status) {
}
