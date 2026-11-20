package com.mirea.freight.model;

import java.util.List;

public final class RefrigeratedRequest extends Request {
    public RefrigeratedRequest(Integer requestId, Integer clientId, Integer bodyTypeId, java.time.LocalDateTime receivedAt, String loadingAddress, String unloadingAddress, java.time.LocalDateTime loadingTime, java.time.LocalDateTime deliveryDeadline, Double cargoMassT, Double cargoVolumeM3, Integer places, String packaging, boolean dangerous, String temperatureMode, Double distanceKm, String status) {
        super(requestId, clientId, bodyTypeId, receivedAt, loadingAddress, unloadingAddress, loadingTime, deliveryDeadline, cargoMassT, cargoVolumeM3, places, packaging, dangerous, temperatureMode, distanceKm, status);
    }

    @Override
    protected void checkBody(List<String> problems) {
        if (temperatureMode() == null || temperatureMode().isEmpty()) {
            problems.add("не указано: температурный режим");
        }
    }
}
