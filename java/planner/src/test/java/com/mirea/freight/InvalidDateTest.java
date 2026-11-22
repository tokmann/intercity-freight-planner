package com.mirea.freight;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mirea.freight.application.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.format.DateTimeParseException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InvalidDateTest {
    @TempDir Path directory;

    @Test void malformedRequestDatesReportTheFieldName() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonStore store = new JsonStore();
        Fleet fleet = store.loadFleet(Path.of("data/fleet.json"));
        for (String field : List.of("received_at", "loading_time", "delivery_deadline")) {
            ObjectNode request = (ObjectNode) mapper.readTree(Path.of("data/incoming.json").toFile()).get(0);
            request.put(field, "2026-13-26T08:00");
            Path input = directory.resolve(field + ".json");
            Files.writeString(input, mapper.createArrayNode().add(request).toString());
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> store.loadIncoming(input, fleet.bodyTypes()));
            assertTrue(error.getMessage().contains(field));
            assertInstanceOf(DateTimeParseException.class, error.getCause());
        }
    }

    @Test void malformedPermitDateReportsTheFieldName() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode fleet = (ObjectNode) mapper.readTree(Path.of("data/fleet.json").toFile());
        ((ObjectNode) fleet.get("driver_permits").get(0)).put("valid_until", "2026-13-26");
        Path input = directory.resolve("fleet.json");
        Files.writeString(input, fleet.toString());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new JsonStore().loadFleet(input));
        assertTrue(error.getMessage().contains("valid_until"));
        assertInstanceOf(DateTimeParseException.class, error.getCause());
    }
}
