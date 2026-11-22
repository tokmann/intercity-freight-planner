package com.mirea.freight;

import org.junit.jupiter.api.Test;
import com.mirea.freight.model.*;
import com.mirea.freight.pricing.*;
import com.mirea.freight.application.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlannerTest {
    private Fleet fleet() throws Exception { return new JsonStore().loadFleet(Path.of("data/fleet.json")); }
    private Planner planner(Fleet f) { return new Planner(f,new StandardCostRule(),new DangerousCostRule()); }
    private List<Request> incoming(Fleet f) throws Exception { return new JsonStore().loadIncoming(Path.of("data/incoming.json"),f.bodyTypes()); }

    @Test void allSixRequestsMatchProceduralOutput() throws Exception {
        Fleet f=fleet(); Planner p=planner(f); ConsoleApp console=new ConsoleApp();
        List<PlanningResult> results=new ArrayList<>(); StringBuilder text=new StringBuilder();
        for (Request q: incoming(f)) {
            PlanningResult r=p.process(q); results.add(r);
            text.append(String.join("\n",console.resultLines(r,f))).append("\n\n");
        }
        text.append("Итог:\n");
        for (PlanningResult r:results) text.append("  заявка № ").append(r.request().requestId()).append(": ").append(r.status()).append("\n");
        assertEquals(Files.readString(Path.of("src/test/resources/expected.txt")).replace("\r\n","\n"),text.toString());
    }

    @Test void inheritanceChecksRefrigeratorTemperature() throws Exception {
        Fleet f=fleet(); Request q=incoming(f).get(1);
        assertInstanceOf(RefrigeratedRequest.class,q);
        assertEquals(List.of("не указано: температурный режим"),q.checkCompleteness(f.clients(),f.bodyTypes()));
        assertTrue(incoming(f).getFirst().checkCompleteness(f.clients(),f.bodyTypes()).isEmpty());
    }

    @Test void costRulesAreUsedThroughInterface() {
        Tariff band=new Tariff(1,1,0,700,80,24000,25);
        CostRule standard=new StandardCostRule(); CostRule dangerous=new DangerousCostRule();
        assertEquals(36000,standard.apply(36000,band)); assertFalse(standard.needsApproval());
        assertEquals(45000,dangerous.apply(36000,band)); assertTrue(dangerous.needsApproval());
    }

    @Test void tariffBoundariesAreHalfOpen() throws Exception {
        Fleet f=fleet(); Planner p=planner(f);
        for (double distance:List.of(0.01,299.99,300.0,699.99,700.0,1499.99,1500.0,2999.99,3000.0)) {
            Request q=request(distance,false); CostResult actual=p.calcCost(q);
            Tariff expected=f.tariffIndex().get(1).stream().filter(t->distance>=t.distFromKm()&&distance<t.distToKm()).findFirst().orElse(null);
            assertEquals(expected==null,actual.cost()==null);
            if(expected!=null) assertEquals(expected.baseCost(distance),actual.cost(),0.0051);
        }
    }

    @Test void emptyTariffsRequireApproval() throws Exception {
        assertEquals(new CostResult(null,true),planner(fleet()).calcCost(new Request(null,1,99,null,null,null,null,null,null,null,null,null,false,null,100.0,null)));
    }

    @Test void minimumCostAndDangerousSurcharge() throws Exception {
        Planner p=planner(fleet());
        assertEquals(new CostResult(9000.0,false),p.calcCost(request(1,false)));
        assertEquals(new CostResult(11700.0,true),p.calcCost(request(1,true)));
    }

    @Test void busyVehicleIsSkippedAndSmallestFreeVehicleWins() throws Exception {
        Fleet f=fleet(); Request q=incoming(f).getFirst();
        Vehicle v=f.selectVehicle(q,q.loadingTime(),q.loadingTime().plusDays(1));
        assertEquals(1,v.vehicleId());
        Request smaller=new Request(null,1,1,null,null,null,null,null,9.0,30.0,null,null,false,null,null,null);
        assertEquals(3,f.selectVehicle(smaller,q.loadingTime(),q.loadingTime().plusHours(1)).vehicleId());
    }

    @Test void vehicleBecomesFreeExactlyAtArrival() throws Exception {
        Fleet f=fleet(); Request q=new Request(null,1,3,null,null,null,null,null,9.0,30.0,null,null,false,null,null,null);
        LocalDateTime end=LocalDateTime.parse("2026-10-26T18:00");
        assertNull(f.selectVehicle(q,end.minusMinutes(1),end.plusHours(1)));
        assertEquals(6,f.selectVehicle(q,end,end.plusHours(1)).vehicleId());
    }

    @Test void expiredDangerousPermitIsNotAccepted() throws Exception {
        Fleet f=fleet(); Request q=incoming(f).get(3);
        LocalDateTime start=q.loadingTime(); Vehicle v=f.vehicles().getFirst();
        assertEquals(1,f.selectDriver(v,q,start,start.plusHours(9)).driverId());
    }

    @Test void incompleteRequestDoesNotReserveResources() throws Exception {
        Fleet f=fleet(); int before=f.trips().size(); PlanningResult r=planner(f).process(incoming(f).get(1));
        assertEquals("ожидает сведений",r.status()); assertEquals(before,f.trips().size());
    }

    @Test void requestCannotBeRegisteredTwiceAndCollectionsAreProtected() throws Exception {
        Fleet f=fleet(); Request q=incoming(f).getFirst(); f.registerRequest(q);
        assertThrows(IllegalStateException.class,()->f.registerRequest(q));
        assertThrows(UnsupportedOperationException.class,()->f.trips().clear());
        assertThrows(UnsupportedOperationException.class,()->f.clients().clear());
    }

    @Test void travelTimeIncludesBreaksAndDailyRest() throws Exception {
        Planner p=planner(fleet());
        assertEquals(4.5,p.calcTravelTime(270),1e-9);
        assertEquals(5.75,p.calcTravelTime(300),1e-9);
        assertEquals(9.75,p.calcTravelTime(540),1e-9);
        assertEquals(21.75,p.calcTravelTime(600),1e-9);
    }

    private Request request(double distance,boolean dangerous) {
        return new Request(null,1,1,null,null,null,null,null,1.0,1.0,1,"ящики",dangerous,"",distance,null);
    }
}
