package com.mirea.freight.model;

public record Vehicle(int vehicleId, int bodyTypeId, int requiredPermitId, String plate,
                      String model, double capacityT, double volumeM3, String techState) {
    public boolean fits(Request request) {
        return "исправен".equals(techState) && bodyTypeId == request.bodyTypeId()
                && capacityT >= request.cargoMassT() && volumeM3 >= request.cargoVolumeM3();
    }
}
