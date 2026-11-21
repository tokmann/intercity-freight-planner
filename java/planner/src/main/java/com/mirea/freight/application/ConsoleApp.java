package com.mirea.freight.application;

import com.mirea.freight.model.*;
import com.mirea.freight.pricing.*;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class ConsoleApp {
    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        try { new ConsoleApp().run(args); }
        catch (IOException | IllegalArgumentException e) {
            System.err.println("Ошибка входных данных: " + e.getMessage()); System.exit(1);
        }
    }

    public void run(String[] args) throws IOException {
        Path fleetPath = Path.of("data/fleet.json");
        Path incomingPath = Path.of("data/incoming.json");
        for (int i = 0; i < args.length; i++) {
            if (("--fleet".equals(args[i]) || "--incoming".equals(args[i])) && i + 1 < args.length) {
                if ("--fleet".equals(args[i])) fleetPath = Path.of(args[++i]);
                else incomingPath = Path.of(args[++i]);
            } else throw new IllegalArgumentException("Использование: java -jar planner.jar [--fleet путь] [--incoming путь]");
        }
        JsonStore store = new JsonStore();
        Fleet fleet = store.loadFleet(fleetPath);
        Planner planner = new Planner(fleet,new StandardCostRule(),new DangerousCostRule());
        List<PlanningResult> results = new ArrayList<>();
        for (Request request : store.loadIncoming(incomingPath,fleet.bodyTypes())) {
            PlanningResult result = planner.process(request); results.add(result);
            System.out.println(String.join("\n", resultLines(result,fleet))); System.out.println();
        }
        System.out.println("Итог:");
        for (PlanningResult result : results) System.out.println("  заявка № " + result.request().requestId() + ": " + result.status());
    }

    public List<String> resultLines(PlanningResult result, Fleet fleet) {
        Request q = result.request();
        Client c = q.clientId() == null ? null : fleet.clients().get(q.clientId());
        List<String> lines = new ArrayList<>();
        lines.add("Заявка № " + q.requestId() + " от " + dt(q.receivedAt()) + ", " + (c == null ? "клиент не указан" : c.name()));
        if (q.loadingAddress() != null && !q.loadingAddress().isEmpty() && q.unloadingAddress() != null && !q.unloadingAddress().isEmpty()) {
            String tail = q.distanceKm() == null || q.distanceKm() == 0 ? "" : " (" + number(q.distanceKm()) + " км)";
            lines.add("  Маршрут: " + q.loadingAddress() + " -> " + q.unloadingAddress() + tail);
        }
        if (q.cargoMassT() != null && q.cargoMassT() != 0 && q.cargoVolumeM3() != null && q.cargoVolumeM3() != 0) {
            lines.add("  Груз: " + number(q.cargoMassT()) + " т, " + number(q.cargoVolumeM3()) + " м3" + (q.dangerous() ? ", опасный" : ""));
        }
        if (result.start() != null) {
            long minutes = (long) Math.rint(Duration.between(result.start(),result.end()).toNanos() / 60_000_000_000.0);
            lines.add("  Время в пути: " + minutes / 60 + " ч " + String.format(Locale.ROOT,"%02d",minutes % 60)
                    + " мин (погрузка " + dt(result.start()) + ", прибытие " + dt(result.end()) + ")");
        }
        Vehicle v = result.vehicle(); Driver d = result.driver();
        if (v != null) lines.add("  Автомобиль: " + v.model() + ", " + v.plate() + " (" + number(v.capacityT()) + " т, " + number(v.volumeM3()) + " м3)");
        if (d != null) lines.add("  Водитель: " + d.shortName() + ", тел. " + d.phone());
        if (result.cost() != null) lines.add("  Стоимость: " + String.format(Locale.US,"%,.2f",result.cost()).replace(',',' ').replace('.',',') + " руб.");
        lines.add("  Статус: " + result.status());
        for (String problem : result.problems()) lines.add("    - " + problem);
        Trip t = result.trip();
        if (t != null) {
            lines.add("  Рейсовое задание № " + t.tripId() + ":");
            lines.add("    водитель " + d.shortName() + ", автомобиль " + v.plate());
            lines.add("    погрузка " + dt(t.departureTime()) + ": " + q.loadingAddress());
            lines.add("    выгрузка до " + dt(q.deliveryDeadline()) + ", расчетное прибытие " + dt(t.arrivalTime()) + ": " + q.unloadingAddress());
            lines.add("    контакт грузоотправителя: " + c.contactPerson() + ", " + c.phone());
        }
        return lines;
    }

    private String dt(LocalDateTime value) { return value.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")); }
    private String number(double value) {
        String[] parts = String.format(Locale.ROOT, "%.6g", value).split("e", 2);
        String mantissa = parts[0];
        if (mantissa.contains(".")) mantissa = mantissa.replaceFirst("0+$", "").replaceFirst("\\.$", "");
        return (mantissa + (parts.length == 2 ? "e" + parts[1] : "")).replace('.', ',');
    }
}
