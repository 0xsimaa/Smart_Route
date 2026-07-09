package com.cybersec.smartroute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.cybersec.smartroute.engine.NativeRouteSimulator;
import com.cybersec.smartroute.model.LatLng;
import com.cybersec.smartroute.model.SpoofConfig;

import org.junit.Test;

public class RouteSimulatorTest {

    @Test
    public void haversine_zero_for_same_point() {
        LatLng a = new LatLng(40, -73);
        assertEquals(0, LatLng.haversineKm(a, a), 1e-6);
    }

    @Test
    public void haversine_known_distance() {
        // ~111 km per degree of latitude near the equator.
        LatLng a = new LatLng(0, 0);
        LatLng b = new LatLng(1, 0);
        assertEquals(111.19, LatLng.haversineKm(a, b), 0.5);
    }

    @Test
    public void simulator_advances_progress() {
        SpoofConfig cfg = SpoofConfig.defaults().toBuilder()
                .start(new LatLng(0, 0))
                .end(new LatLng(0.1, 0.1))
                .speedRange(60, 60)
                .build();
        NativeRouteSimulator sim = new NativeRouteSimulator(cfg.toEngineJson(0));
        NativeRouteSimulator.TickResult before = sim.tick(0);
        NativeRouteSimulator.TickResult after = sim.tick(30);
        assertNotEquals(before.lat, after.lat, 1e-9);
        assertTrue(sim.toJson().optDouble("progress", 0) > 0);
    }

    @Test
    public void simulator_completes_eventually() {
        SpoofConfig cfg = SpoofConfig.defaults().toBuilder()
                .start(new LatLng(0, 0))
                .end(new LatLng(0.001, 0.001))
                .speedRange(120, 120)
                .build();
        NativeRouteSimulator sim = new NativeRouteSimulator(cfg.toEngineJson(0));
        boolean done = false;
        for (int i = 0; i < 100 && !done; i++) {
            done = sim.tick(5).done;
        }
        assertTrue("simulator should complete a 150 m route within 100 ticks", done);
    }

    @Test
    public void config_round_trips_through_json() {
        SpoofConfig original = SpoofConfig.defaults().toBuilder()
                .speedRange(20, 90)
                .durationMinutes(45)
                .updateIntervalSeconds(3)
                .enablePauses(true)
                .routePolyline(java.util.Arrays.asList(
                        new LatLng(0, 0), new LatLng(0.01, 0.01)))
                .directionSteps(java.util.Arrays.asList("Head north", "Turn right"))
                .build();
        SpoofConfig parsed = SpoofConfig.fromJson(original.toJson());
        assertEquals(original.minSpeedKmh, parsed.minSpeedKmh, 1e-9);
        assertEquals(original.maxSpeedKmh, parsed.maxSpeedKmh, 1e-9);
        assertEquals(original.durationMinutes, parsed.durationMinutes);
        assertEquals(original.updateIntervalSeconds, parsed.updateIntervalSeconds);
        assertEquals(original.enablePauses, parsed.enablePauses);
        assertEquals(2, parsed.routePolyline.size());
        assertEquals(2, parsed.directionSteps.size());
    }

    @Test
    public void simulator_follows_route_polyline() throws Exception {
        java.util.List<LatLng> poly = new java.util.ArrayList<>();
        poly.add(new LatLng(0, 0));
        poly.add(new LatLng(0.01, 0));
        poly.add(new LatLng(0.01, 0.01));
        SpoofConfig cfg = SpoofConfig.defaults().toBuilder()
                .start(new LatLng(0, 0))
                .end(new LatLng(0.01, 0.01))
                .routePolyline(poly)
                .speedRange(120, 120)
                .build();
        org.json.JSONObject session = cfg.toEngineJson(0);
        NativeRouteSimulator sim = new NativeRouteSimulator(session);
        NativeRouteSimulator.TickResult mid = sim.tick(5);
        assertTrue(mid.lat > 0 || mid.lon > 0);
    }

    @Test
    public void statusFromSession_reads_bearing_and_speed() throws Exception {
        org.json.JSONObject o = new org.json.JSONObject();
        o.put("lastLat", 1.0);
        o.put("lastLon", 2.0);
        o.put("progress", 0.5);
        o.put("lastBearing", 90);
        o.put("lastSpeedMps", 12.5);
        com.cybersec.smartroute.engine.SessionAdvancer.AdvanceResult r =
                com.cybersec.smartroute.engine.SessionAdvancer.statusFromSession(o);
        assertEquals(90f, r.bearing, 0.01f);
        assertEquals(12.5f, r.speedMps, 0.01f);
        assertEquals(0.5, r.progress, 1e-9);
    }

    @Test
    public void directions_downsample_caps_points() {
        java.util.List<LatLng> many = new java.util.ArrayList<>();
        for (int i = 0; i < 1000; i++) many.add(new LatLng(i * 0.001, 0));
        java.util.List<LatLng> out = com.cybersec.smartroute.util.DirectionsClient.downsample(many, 100);
        assertEquals(100, out.size());
    }
}
