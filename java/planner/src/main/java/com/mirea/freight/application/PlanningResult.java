package com.mirea.freight.application;

import com.mirea.freight.model.*;
import com.mirea.freight.pricing.*;
import java.time.LocalDateTime;
import java.util.List;

public record PlanningResult(Request request, String status, List<String> problems,
                             Vehicle vehicle, Driver driver, LocalDateTime start,
                             LocalDateTime end, Double cost, Trip trip) {
    public PlanningResult { problems = List.copyOf(problems); }
}
