package com.mirea.freight.application;

import com.mirea.freight.model.*;
import com.mirea.freight.pricing.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

public final class Planner {
    private final Fleet fleet;
    private final CostRule standardRule;
    private final CostRule dangerousRule;

    public Planner(Fleet fleet, CostRule standardRule, CostRule dangerousRule) {
        this.fleet = fleet;
        this.standardRule = standardRule;
        this.dangerousRule = dangerousRule;
    }

    public double calcTravelTime(double distanceKm) {
        double driving = distanceKm / 60;
        int days = (int) Math.ceil(driving / 9);
        double lastDay = driving - 9 * (days - 1);
        int breaks = days - 1 + (int) Math.ceil(lastDay / 4.5) - 1;
        return driving + breaks * 0.75 + (days - 1) * 11;
    }

    public CostResult calcCost(Request request) {
        List<Tariff> bands = fleet.tariffIndex().getOrDefault(request.bodyTypeId(), List.of());
        double distance = request.distanceKm();
        int low = 0;
        int high = bands.size() - 1;
        Tariff band = null;
        while (low <= high && band == null) {
            int mid = (low + high) / 2;
            if (distance < bands.get(mid).distFromKm()) high = mid - 1;
            else if (distance >= bands.get(mid).distToKm()) low = mid + 1;
            else band = bands.get(mid);
        }
        if (band == null) return new CostResult(null, true);
        CostRule rule = request.dangerous() ? dangerousRule : standardRule;
        double cost = rule.apply(band.baseCost(distance), band);
        // Конструктор из double сохраняет его двоичное значение, как при round() в Python.
        double rounded = new BigDecimal(cost).setScale(2, RoundingMode.HALF_EVEN).doubleValue();
        return new CostResult(rounded, rule.needsApproval());
    }

    public PlanningResult process(Request request) {
        fleet.registerRequest(request);
        List<String> problems = request.checkCompleteness(fleet.clients(), fleet.bodyTypes());
        if (!problems.isEmpty()) return result(request, "ожидает сведений", problems, null, null, null, null, null, null);
        LocalDateTime start = request.loadingTime();
        long micros = (long) Math.rint(calcTravelTime(request.distanceKm()) * 3_600_000_000.0);
        LocalDateTime end = start.plusNanos(micros * 1000);
        Vehicle vehicle = fleet.selectVehicle(request, start, end);
        if (vehicle == null) return result(request, "отклонена", List.of("нет свободного подходящего автомобиля"),
                null, null, start, end, null, null);
        Driver driver = fleet.selectDriver(vehicle, request, start, end);
        if (driver == null) return result(request, "отклонена", List.of("нет свободного водителя с нужными допусками"),
                vehicle, null, start, end, null, null);
        CostResult price = calcCost(request);
        if (price.needsApproval()) {
            String problem = price.cost() == null
                    ? "расстояние вне тарифной сетки, стоимость назначает начальник отдела эксплуатации"
                    : "опасный груз, стоимость согласует начальник отдела эксплуатации";
            return result(request, "на согласовании", List.of(problem), vehicle, driver, start, end, price.cost(), null);
        }
        Trip trip = fleet.makeTripTask(request, vehicle, driver, start, end, price.cost());
        return result(request, "рейс запланирован", List.of(), vehicle, driver, start, end, price.cost(), trip);
    }

    private PlanningResult result(Request request, String status, List<String> problems,
                                  Vehicle vehicle, Driver driver, LocalDateTime start,
                                  LocalDateTime end, Double cost, Trip trip) {
        request.changeStatus(status);
        return new PlanningResult(request, status, problems, vehicle, driver, start, end, cost, trip);
    }
}
