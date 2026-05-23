package com.cybersec.smartroute.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.cybersec.smartroute.R;
import com.cybersec.smartroute.engine.NativeRouteSimulator;
import com.cybersec.smartroute.model.LatLng;
import com.cybersec.smartroute.model.SpoofConfig;
import com.cybersec.smartroute.service.SpoofController;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class MapActivity extends AppCompatActivity {

    private SpoofController controller;
    private GoogleMap map;
    private Marker startMarker;
    private Marker endMarker;
    private final List<Marker> waypointMarkers = new ArrayList<>();
    private Polyline preview;
    private Polyline live;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        controller = SpoofController.get(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_clear_waypoints) {
                SpoofConfig c = controller.getConfig().toBuilder()
                        .waypoints(new ArrayList<>()).build();
                controller.updateConfig(c);
                drawAll();
                return true;
            } else if (id == R.id.action_add_waypoint) {
                addWaypointAtCenter();
                return true;
            }
            return false;
        });

        ExtendedFloatingActionButton fab = findViewById(R.id.fabSave);
        fab.setOnClickListener(v -> finish());

        FragmentManager fm = getSupportFragmentManager();
        SupportMapFragment frag = (SupportMapFragment) fm.findFragmentById(R.id.mapFragment);
        if (frag != null) {
            frag.getMapAsync(googleMap -> {
                map = googleMap;
                map.getUiSettings().setMyLocationButtonEnabled(false);
                map.getUiSettings().setZoomControlsEnabled(true);
                drawAll();
                fitToRoute();
            });
        }
    }

    private void drawAll() {
        if (map == null) return;
        map.clear();
        startMarker = null;
        endMarker = null;
        waypointMarkers.clear();
        preview = null;
        live = null;

        SpoofConfig c = controller.getConfig();

        startMarker = map.addMarker(new MarkerOptions()
                .position(c.start.toGms())
                .draggable(true)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                .title("Start"));
        endMarker = map.addMarker(new MarkerOptions()
                .position(c.end.toGms())
                .draggable(true)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                .title("End"));
        for (int i = 0; i < c.waypoints.size(); i++) {
            LatLng w = c.waypoints.get(i);
            Marker m = map.addMarker(new MarkerOptions()
                    .position(w.toGms())
                    .draggable(true)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    .title("Waypoint " + (i + 1)));
            waypointMarkers.add(m);
        }

        // Preview polyline (current config, not active session).
        List<com.google.android.gms.maps.model.LatLng> previewPoints = previewPath(c, 60);
        preview = map.addPolyline(new PolylineOptions()
                .addAll(previewPoints)
                .width(8f)
                .color(0xFF0B61A4));

        // Live trajectory (already injected fixes)
        List<LatLng> trail = controller.getTrajectory();
        if (trail.size() > 1) {
            PolylineOptions lp = new PolylineOptions().width(6f).color(0xFF0F8E50);
            for (LatLng p : trail) lp.add(p.toGms());
            live = map.addPolyline(lp);
        }

        // Drag handlers
        map.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override public void onMarkerDragStart(Marker m) {}
            @Override public void onMarkerDrag(Marker m) {}
            @Override public void onMarkerDragEnd(Marker m) {
                onMarkerMoved(m);
            }
        });
    }

    private void onMarkerMoved(Marker m) {
        SpoofConfig c = controller.getConfig();
        SpoofConfig.Builder b = c.toBuilder();
        if (m == startMarker) {
            b.start(LatLng.fromGms(m.getPosition()));
        } else if (m == endMarker) {
            b.end(LatLng.fromGms(m.getPosition()));
        } else {
            int idx = waypointMarkers.indexOf(m);
            if (idx >= 0) {
                List<LatLng> wps = new ArrayList<>(c.waypoints);
                wps.set(idx, LatLng.fromGms(m.getPosition()));
                b.waypoints(wps);
            }
        }
        controller.updateConfig(b.build());
        drawAll();
    }

    private void addWaypointAtCenter() {
        if (map == null) return;
        LatLngBounds bounds = map.getProjection().getVisibleRegion().latLngBounds;
        com.google.android.gms.maps.model.LatLng c =
                new com.google.android.gms.maps.model.LatLng(
                        (bounds.northeast.latitude + bounds.southwest.latitude) / 2,
                        (bounds.northeast.longitude + bounds.southwest.longitude) / 2);
        SpoofConfig cfg = controller.getConfig();
        List<LatLng> wps = new ArrayList<>(cfg.waypoints);
        wps.add(LatLng.fromGms(c));
        controller.updateConfig(cfg.toBuilder().waypoints(wps).build());
        drawAll();
    }

    private void fitToRoute() {
        if (map == null) return;
        SpoofConfig c = controller.getConfig();
        LatLngBounds.Builder b = new LatLngBounds.Builder();
        b.include(c.start.toGms());
        b.include(c.end.toGms());
        for (LatLng w : c.waypoints) b.include(w.toGms());
        try {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 200));
        } catch (Exception ignored) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(c.start.toGms(), 10f));
        }
    }

    private static List<com.google.android.gms.maps.model.LatLng> previewPath(SpoofConfig config, int steps) {
        // Use the simulator with progress fed manually for an accurate preview
        // (ensures curved paths and waypoints render the same as during a session).
        org.json.JSONObject session = config.toEngineJson(0);
        List<com.google.android.gms.maps.model.LatLng> out = new ArrayList<>(steps + 1);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            try { session.put("progress", t); } catch (org.json.JSONException ignored) {}
            NativeRouteSimulator sim = new NativeRouteSimulator(session);
            // Feed delta=0 so only positionAt(progress) is reported
            NativeRouteSimulator.TickResult r = sim.tick(0);
            out.add(new com.google.android.gms.maps.model.LatLng(r.lat, r.lon));
        }
        return out;
    }
}
