package com.mirea.freight.application;

import com.mirea.freight.model.*;
import com.mirea.freight.pricing.*;
import java.time.LocalDateTime;
import java.util.*;

public final class Fleet {
    private final Map<Integer, Client> clients;
    private final Map<Integer, BodyType> bodyTypes;
    private final List<Permit> permits;
    private final List<Vehicle> vehicles;
    private final List<Driver> drivers;
    private final List<DriverPermit> driverPermits;
    private final Map<Integer, List<Tariff>> tariffIndex;
    private final List<Request> requests;
    private final List<Trip> trips;
    private final List<TripDriver> tripDrivers;

    public Fleet(Map<Integer, Client> clients, Map<Integer, BodyType> bodyTypes,
                 List<Permit> permits, List<Vehicle> vehicles, List<Driver> drivers,
                 List<DriverPermit> driverPermits, List<Tariff> tariffs,
                 List<Request> requests, List<Trip> trips, List<TripDriver> tripDrivers) {
        this.clients = Map.copyOf(clients);
        this.bodyTypes = Map.copyOf(bodyTypes);
        this.permits = List.copyOf(permits);
        this.vehicles = List.copyOf(vehicles);
        this.drivers = List.copyOf(drivers);
        this.driverPermits = List.copyOf(driverPermits);
        this.requests = new ArrayList<>(requests);
        this.trips = new ArrayList<>(trips);
        this.tripDrivers = new ArrayList<>(tripDrivers);
        Map<Integer, List<Tariff>> grouped = new HashMap<>();
        for (Tariff tariff : tariffs) grouped.computeIfAbsent(tariff.bodyTypeId(), id -> new ArrayList<>()).add(tariff);
        Map<Integer, List<Tariff>> index = new HashMap<>();
        for (var entry : grouped.entrySet()) {
            entry.getValue().sort(Comparator.comparingDouble(Tariff::distFromKm));
            index.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        tariffIndex = Map.copyOf(index);
    }

    public Map<Integer, Client> clients() { return clients; }
    public Map<Integer, BodyType> bodyTypes() { return bodyTypes; }
    public List<Permit> permits() { return permits; }
    public List<Vehicle> vehicles() { return vehicles; }
    public List<Driver> drivers() { return drivers; }
    public List<DriverPermit> driverPermits() { return driverPermits; }
    public Map<Integer, List<Tariff>> tariffIndex() { return tariffIndex; }
    public List<Request> requests() { return Collections.unmodifiableList(requests); }
    public List<Trip> trips() { return Collections.unmodifiableList(trips); }
    public List<TripDriver> tripDrivers() { return Collections.unmodifiableList(tripDrivers); }

    public void registerRequest(Request request) {
        int id = requests.stream().mapToInt(Request::requestId).max().orElse(0) + 1;
        request.register(id);
        requests.add(request);
    }

    public Vehicle selectVehicle(Request request, LocalDateTime start, LocalDateTime end) {
        Vehicle best = null;
        double bestReserve = 0;
        for (Vehicle vehicle : vehicles) {
            if (vehicle.fits(request)) {
                boolean busy = false;
                int i = 0;
                while (i < trips.size() && !busy) {
                    Trip trip = trips.get(i);
                    if (trip.vehicleId() == vehicle.vehicleId()
                            && overlaps(trip.departureTime(), trip.arrivalTime(), start, end)) busy = true;
                    i++;
                }
                if (!busy) {
                    double reserve = vehicle.capacityT() - request.cargoMassT();
                    if (best == null || reserve < bestReserve) { best = vehicle; bestReserve = reserve; }
                }
            }
        }
        return best;
    }

    public Driver selectDriver(Vehicle vehicle, Request request, LocalDateTime start, LocalDateTime end) {
        Set<Integer> needed = new HashSet<>();
        needed.add(vehicle.requiredPermitId());
        if (request.dangerous()) {
            for (Permit permit : permits) if ("ДОПОГ".equals(permit.code())) needed.add(permit.permitId());
        }
        Map<Integer, Trip> byId = new HashMap<>();
        for (Trip trip : trips) byId.put(trip.tripId(), trip);
        Driver best = null;
        int bestLoad = 0;
        for (Driver driver : drivers) {
            Set<Integer> valid = new HashSet<>();
            for (DriverPermit permit : driverPermits) {
                if (permit.driverId() == driver.driverId() && !permit.validUntil().isBefore(end.toLocalDate())) {
                    valid.add(permit.permitId());
                }
            }
            if (valid.containsAll(needed)) {
                boolean busy = false;
                int load = 0;
                for (TripDriver assignment : tripDrivers) {
                    if (assignment.driverId() == driver.driverId()) {
                        Trip trip = byId.get(assignment.tripId());
                        load++;
                        if (overlaps(trip.departureTime(), trip.arrivalTime(),
                                start.minusHours(11), end.plusHours(11))) busy = true;
                    }
                }
                if (!busy && (best == null || load < bestLoad)) { best = driver; bestLoad = load; }
            }
        }
        return best;
    }

    public Trip makeTripTask(Request request, Vehicle vehicle, Driver driver,
                             LocalDateTime start, LocalDateTime end, double cost) {
        int id = trips.stream().mapToInt(Trip::tripId).max().orElse(0) + 1;
        Trip trip = new Trip(id, request.requestId(), vehicle.vehicleId(), start, end, cost, "запланирован");
        trips.add(trip);
        tripDrivers.add(new TripDriver(id, driver.driverId(), "основной"));
        return trip;
    }

    private boolean overlaps(LocalDateTime start1, LocalDateTime end1,
                             LocalDateTime start2, LocalDateTime end2) {
        return start1.isBefore(end2) && start2.isBefore(end1);
    }
}
