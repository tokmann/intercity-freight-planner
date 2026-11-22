package com.mirea.freight;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mirea.freight.application.*;
import com.mirea.freight.model.Request;
import com.mirea.freight.pricing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MissingReceivedAtTest {
    @TempDir Path directory;

    @Test void missingReceivedAtIsReportedWithoutReservingResources() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode request = (ObjectNode) mapper.readTree(Path.of("data/incoming.json").toFile()).get(0);
        request.remove("received_at");
        Path input = directory.resolve("incoming.json");
        Files.writeString(input, mapper.createArrayNode().add(request).toString());
        JsonStore store = new JsonStore();
        Fleet fleet = store.loadFleet(Path.of("data/fleet.json"));
        int tripCount = fleet.trips().size();
        int assignmentCount = fleet.tripDrivers().size();
        Request incoming = store.loadIncoming(input, fleet.bodyTypes()).getFirst();
        PlanningResult result = new Planner(fleet, new StandardCostRule(), new DangerousCostRule()).process(incoming);

        assertEquals("ожидает сведений", result.status());
        assertEquals(List.of("не указано: дата приема"), result.problems());
        assertNull(result.trip());
        assertEquals(tripCount, fleet.trips().size());
        assertEquals(assignmentCount, fleet.tripDrivers().size());
        assertTrue(new ConsoleApp().resultLines(result, fleet).getFirst().contains("дата приема не указана"));
    }
}
