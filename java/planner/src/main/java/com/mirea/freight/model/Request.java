package com.mirea.freight.model;

import java.util.ArrayList;
import java.util.List;

public class Request {
    private Integer requestId;
    private final Integer clientId;
    private final Integer bodyTypeId;
    private final java.time.LocalDateTime receivedAt;
    private final String loadingAddress;
    private final String unloadingAddress;
    private final java.time.LocalDateTime loadingTime;
    private final java.time.LocalDateTime deliveryDeadline;
    private final Double cargoMassT;
    private final Double cargoVolumeM3;
    private final Integer places;
    private final String packaging;
    private final boolean dangerous;
    private final String temperatureMode;
    private final Double distanceKm;
    private String status;

    public Request(Integer requestId, Integer clientId, Integer bodyTypeId, java.time.LocalDateTime receivedAt, String loadingAddress, String unloadingAddress, java.time.LocalDateTime loadingTime, java.time.LocalDateTime deliveryDeadline, Double cargoMassT, Double cargoVolumeM3, Integer places, String packaging, boolean dangerous, String temperatureMode, Double distanceKm, String status) {
        this.requestId = requestId;
        this.clientId = clientId;
        this.bodyTypeId = bodyTypeId;
        this.receivedAt = receivedAt;
        this.loadingAddress = loadingAddress;
        this.unloadingAddress = unloadingAddress;
        this.loadingTime = loadingTime;
        this.deliveryDeadline = deliveryDeadline;
        this.cargoMassT = cargoMassT;
        this.cargoVolumeM3 = cargoVolumeM3;
        this.places = places;
        this.packaging = packaging;
        this.dangerous = dangerous;
        this.temperatureMode = temperatureMode;
        this.distanceKm = distanceKm;
        this.status = status;
    }

    public Integer requestId() { return requestId; }
    public Integer clientId() { return clientId; }
    public Integer bodyTypeId() { return bodyTypeId; }
    public java.time.LocalDateTime receivedAt() { return receivedAt; }
    public String loadingAddress() { return loadingAddress; }
    public String unloadingAddress() { return unloadingAddress; }
    public java.time.LocalDateTime loadingTime() { return loadingTime; }
    public java.time.LocalDateTime deliveryDeadline() { return deliveryDeadline; }
    public Double cargoMassT() { return cargoMassT; }
    public Double cargoVolumeM3() { return cargoVolumeM3; }
    public Integer places() { return places; }
    public String packaging() { return packaging; }
    public boolean dangerous() { return dangerous; }
    public String temperatureMode() { return temperatureMode; }
    public Double distanceKm() { return distanceKm; }
    public String status() { return status; }

    public void register(int id) {
        if (requestId != null) throw new IllegalStateException("Заявка уже зарегистрирована");
        requestId = id;
        status = "зарегистрирована";
    }

    public void changeStatus(String status) { this.status = status; }

    public List<String> checkCompleteness(java.util.Map<Integer, Client> clients, java.util.Map<Integer, BodyType> bodyTypes) {
        List<String> problems = new ArrayList<>();
        Object[] values = { clientId, bodyTypeId, loadingAddress, unloadingAddress, loadingTime,
                deliveryDeadline, cargoMassT, cargoVolumeM3, places, packaging, distanceKm };
        String[] names = { "клиент", "тип кузова", "адрес погрузки", "адрес выгрузки", "время погрузки",
                "срок доставки", "масса груза", "объем груза", "число мест", "вид упаковки", "расстояние" };
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null || "".equals(values[i])) problems.add("не указано: " + names[i]);
        }
        if (!problems.isEmpty()) return problems;
        if (!clients.containsKey(clientId)) problems.add("клиент не найден в базе");
        if (!bodyTypes.containsKey(bodyTypeId)) problems.add("неизвестный тип кузова");
        checkBody(problems);
        if (cargoMassT <= 0) problems.add("неверное значение: масса груза");
        if (cargoVolumeM3 <= 0) problems.add("неверное значение: объем груза");
        if (places <= 0) problems.add("неверное значение: число мест");
        if (distanceKm <= 0) problems.add("неверное значение: расстояние");
        if (!deliveryDeadline.isAfter(loadingTime)) problems.add("срок доставки не позже времени погрузки");
        return problems;
    }

    protected void checkBody(List<String> problems) { }
}
