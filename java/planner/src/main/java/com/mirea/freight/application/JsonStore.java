package com.mirea.freight.application;

import com.mirea.freight.model.*;
import com.mirea.freight.pricing.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public final class JsonStore {
    private final ObjectMapper mapper = new ObjectMapper();

    public Fleet loadFleet(Path path) throws IOException {
        JsonNode root = mapper.readTree(path.toFile());
        Map<Integer, Client> clients = new HashMap<>();
        for (JsonNode n : root.path("clients")) {
            Client c = new Client(n.path("client_id").asInt(), text(n,"name"), text(n,"inn"),
                    text(n,"contact_person"), text(n,"phone"), text(n,"email"));
            clients.put(c.clientId(), c);
        }
        Map<Integer, BodyType> bodies = new HashMap<>();
        for (JsonNode n : root.path("body_types")) {
            BodyType b = new BodyType(n.path("body_type_id").asInt(), text(n,"name")); bodies.put(b.bodyTypeId(), b);
        }
        List<Permit> permits = new ArrayList<>();
        for (JsonNode n : root.path("permits")) permits.add(new Permit(n.path("permit_id").asInt(), text(n,"code"), text(n,"name")));
        List<Vehicle> vehicles = new ArrayList<>();
        for (JsonNode n : root.path("vehicles")) vehicles.add(new Vehicle(n.path("vehicle_id").asInt(), n.path("body_type_id").asInt(),
                n.path("required_permit_id").asInt(), text(n,"plate"), text(n,"model"), n.path("capacity_t").asDouble(),
                n.path("volume_m3").asDouble(), text(n,"tech_state")));
        List<Driver> drivers = new ArrayList<>();
        for (JsonNode n : root.path("drivers")) drivers.add(new Driver(n.path("driver_id").asInt(), text(n,"last_name"),
                text(n,"first_name"), text(n,"middle_name"), text(n,"phone"), text(n,"license_number")));
        List<DriverPermit> driverPermits = new ArrayList<>();
        for (JsonNode n : root.path("driver_permits")) driverPermits.add(new DriverPermit(n.path("driver_id").asInt(),
                n.path("permit_id").asInt(), LocalDate.parse(text(n,"valid_until"))));
        List<Tariff> tariffs = new ArrayList<>();
        for (JsonNode n : root.path("tariffs")) tariffs.add(new Tariff(n.path("tariff_id").asInt(), n.path("body_type_id").asInt(),
                n.path("dist_from_km").asDouble(), n.path("dist_to_km").asDouble(), n.path("rate_per_km").asDouble(),
                n.path("min_cost").asDouble(), n.path("danger_surcharge_pct").asDouble()));
        List<Request> requests = new ArrayList<>();
        for (JsonNode n : root.path("requests")) requests.add(readRequest(n,bodies));
        List<Trip> trips = new ArrayList<>();
        for (JsonNode n : root.path("trips")) trips.add(new Trip(n.path("trip_id").asInt(), n.path("request_id").asInt(),
                n.path("vehicle_id").asInt(), dt(n,"departure_time"), dt(n,"arrival_time"), n.path("cost").asDouble(), text(n,"status")));
        List<TripDriver> crew = new ArrayList<>();
        for (JsonNode n : root.path("trip_drivers")) crew.add(new TripDriver(n.path("trip_id").asInt(), n.path("driver_id").asInt(), text(n,"role")));
        return new Fleet(clients,bodies,permits,vehicles,drivers,driverPermits,tariffs,requests,trips,crew);
    }

    public List<Request> loadIncoming(Path path, Map<Integer, BodyType> bodies) throws IOException {
        List<Request> requests = new ArrayList<>();
        for (JsonNode n : mapper.readTree(path.toFile())) requests.add(readRequest(n,bodies));
        return requests;
    }

    private Request readRequest(JsonNode n, Map<Integer, BodyType> bodies) {
        Integer bodyId = integer(n,"body_type_id");
        boolean refrigerator = bodyId != null && bodies.containsKey(bodyId) && "Рефрижератор".equals(bodies.get(bodyId).name());
        if (refrigerator) return new RefrigeratedRequest(integer(n,"request_id"), integer(n,"client_id"), bodyId, dt(n,"received_at"),
                text(n,"loading_address"), text(n,"unloading_address"), dt(n,"loading_time"), dt(n,"delivery_deadline"),
                number(n,"cargo_mass_t"), number(n,"cargo_volume_m3"), integer(n,"places"), text(n,"packaging"),
                n.path("dangerous").asBoolean(false), text(n,"temperature_mode"), number(n,"distance_km"), text(n,"status"));
        return new Request(integer(n,"request_id"), integer(n,"client_id"), bodyId, dt(n,"received_at"),
                text(n,"loading_address"), text(n,"unloading_address"), dt(n,"loading_time"), dt(n,"delivery_deadline"),
                number(n,"cargo_mass_t"), number(n,"cargo_volume_m3"), integer(n,"places"), text(n,"packaging"),
                n.path("dangerous").asBoolean(false), text(n,"temperature_mode"), number(n,"distance_km"), text(n,"status"));
    }

    private String text(JsonNode n, String key) { return n.path(key).isNull() || n.path(key).isMissingNode() ? null : n.path(key).asText(); }
    private Integer integer(JsonNode n, String key) { return n.path(key).isNumber() ? n.path(key).intValue() : null; }
    private Double number(JsonNode n, String key) { return n.path(key).isNumber() ? n.path(key).doubleValue() : null; }
    private LocalDateTime dt(JsonNode n, String key) {
        String value = text(n,key); return value == null || value.isEmpty() ? null : LocalDateTime.parse(value);
    }
}
