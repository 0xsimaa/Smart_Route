package com.cybersec.smartroute.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import com.cybersec.smartroute.BuildConfig;
import com.cybersec.smartroute.R;
import com.cybersec.smartroute.engine.NativeRouteSimulator;
import com.cybersec.smartroute.model.LatLng;
import com.cybersec.smartroute.model.SpoofConfig;
import com.cybersec.smartroute.service.SpoofController;
import com.cybersec.smartroute.util.DirectionsClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int REQ_PLACES = 1001;

    private SpoofController controller;
    private GoogleMap map;
    private Marker startMarker;
    private Marker endMarker;
    private final List<Marker> waypointMarkers = new ArrayList<>();
    private Polyline preview;
    private Polyline roadRoute;
    private Polyline live;

    private List<LatLng> fetchedPolyline = new ArrayList<>();
    private List<String> fetchedSteps = new ArrayList<>();
    private boolean directionsFetchInFlight;

    private TextInputEditText searchInput;
    private MaterialCardView directionsCard;
    private TextView txtDirections;
    private FloatingActionButton fabMyLocation;
    private FusedLocationProviderClient fusedClient;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private final androidx.activity.result.ActivityResultLauncher<String[]> locationPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    res -> enableMyLocationIfPermitted());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        controller = SpoofController.get(this);
        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_clear_waypoints) {
                SpoofConfig c = controller.getConfig().toBuilder()
                        .waypoints(new ArrayList<>())
                        .routePolyline(new ArrayList<>())
                        .directionSteps(new ArrayList<>())
                        .build();
                controller.updateConfig(c);
                fetchedPolyline.clear();
                fetchedSteps.clear();
                drawAll();
                fetchDirectionsAsync();
                return true;
            } else if (id == R.id.action_add_waypoint) {
                addWaypointAtCenter();
                return true;
            } else if (id == R.id.action_search) {
                launchPlaceSearch();
                return true;
            }
            return false;
        });

        searchInput = findViewById(R.id.searchInput);
        directionsCard = findViewById(R.id.directionsCard);
        txtDirections = findViewById(R.id.txtDirections);
        fabMyLocation = findViewById(R.id.fabMyLocation);

        searchInput.setOnClickListener(v -> launchPlaceSearch());
        searchInput.setFocusable(false);
        fabMyLocation.setOnClickListener(v -> centerOnMyLocation());

        ExtendedFloatingActionButton fab = findViewById(R.id.fabSave);
        fab.setOnClickListener(v -> saveRouteAndFinish());

        initPlaces();

        FragmentManager fm = getSupportFragmentManager();
        SupportMapFragment frag = (SupportMapFragment) fm.findFragmentById(R.id.mapFragment);
        if (frag != null) frag.getMapAsync(this);

        SpoofConfig existing = controller.getConfig();
        fetchedPolyline = new ArrayList<>(existing.routePolyline);
        fetchedSteps = new ArrayList<>(existing.directionSteps);
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    private void initPlaces() {
        String key = BuildConfig.MAPS_API_KEY;
        if (key == null || key.isEmpty() || "YOUR_GOOGLE_MAPS_API_KEY".equals(key)) return;
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), key);
        }
    }

    private void launchPlaceSearch() {
        if (!Places.isInitialized()) {
            Snackbar.make(findViewById(android.R.id.content),
                    R.string.places_not_initialized, Snackbar.LENGTH_LONG).show();
            return;
        }
        try {
            List<Place.Field> fields = Arrays.asList(
                    Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS);
            Intent intent = new Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
                    .build(this);
            startActivityForResult(intent, REQ_PLACES);
        } catch (Exception e) {
            Snackbar.make(findViewById(android.R.id.content),
                    R.string.places_not_initialized, Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PLACES && resultCode == RESULT_OK && data != null) {
            Place place = Autocomplete.getPlaceFromIntent(data);
            if (place.getLatLng() != null && map != null) {
                com.google.android.gms.maps.model.LatLng p = place.getLatLng();
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(p, 15f));
                if (place.getName() != null) {
                    searchInput.setText(place.getName());
                } else if (place.getAddress() != null) {
                    searchInput.setText(place.getAddress());
                }
            }
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        map = googleMap;
        map.getUiSettings().setMyLocationButtonEnabled(false);
        map.getUiSettings().setZoomControlsEnabled(true);
        map.getUiSettings().setCompassEnabled(true);

        map.setOnMapLongClickListener(p -> {
            SpoofConfig cfg = controller.getConfig();
            List<LatLng> wps = new ArrayList<>(cfg.waypoints);
            wps.add(LatLng.fromGms(p));
            controller.updateConfig(cfg.toBuilder().waypoints(wps).build());
            drawAll();
            fetchDirectionsAsync();
        });

        map.setOnMapLoadedCallback(() -> {
            if (BuildConfig.MAPS_API_KEY == null || BuildConfig.MAPS_API_KEY.isEmpty()) {
                Snackbar.make(findViewById(android.R.id.content),
                        R.string.maps_key_error, Snackbar.LENGTH_INDEFINITE).show();
            }
        });

        ensureLocationPermissionAndEnable();
        drawAll();
        fitToRoute();

        if (controller.getConfig().isUsingPlaceholderRoute()) {
            centerOnMyLocation();
            Snackbar.make(findViewById(android.R.id.content), R.string.map_hint_drag_markers,
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.action_close, v -> {})
                    .show();
        }

        if (!fetchedPolyline.isEmpty()) {
            showDirections(fetchedSteps);
        } else if (!controller.getConfig().isUsingPlaceholderRoute()) {
            fetchDirectionsAsync();
        }
    }

    private void ensureLocationPermissionAndEnable() {
        if (hasLocationPermission()) {
            enableMyLocationIfPermitted();
        } else {
            locationPermLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void enableMyLocationIfPermitted() {
        if (map == null || !hasLocationPermission()) return;
        try {
            map.setMyLocationEnabled(true);
        } catch (SecurityException ignored) {
        }
    }

    private void centerOnMyLocation() {
        if (!hasLocationPermission()) {
            ensureLocationPermissionAndEnable();
            return;
        }
        try {
            fusedClient.getLastLocation().addOnSuccessListener(loc -> {
                if (loc != null && map != null) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                            new com.google.android.gms.maps.model.LatLng(
                                    loc.getLatitude(), loc.getLongitude()), 14f));
                }
            });
        } catch (SecurityException ignored) {
        }
    }

    /** BUG-002 fix: explicitly persist marker positions before finishing. */
    private void saveRouteAndFinish() {
        SpoofConfig.Builder b = controller.getConfig().toBuilder();
        if (startMarker != null) {
            b.start(LatLng.fromGms(startMarker.getPosition()));
        }
        if (endMarker != null) {
            b.end(LatLng.fromGms(endMarker.getPosition()));
        }
        List<LatLng> wps = new ArrayList<>();
        for (Marker m : waypointMarkers) {
            wps.add(LatLng.fromGms(m.getPosition()));
        }
        b.waypoints(wps);
        b.routePolyline(fetchedPolyline);
        b.directionSteps(fetchedSteps);
        SpoofConfig updated = b.build();
        if (updated.start.equals(SpoofConfig.DEFAULT_START)
                && updated.end.equals(SpoofConfig.DEFAULT_END)) {
            Snackbar.make(findViewById(android.R.id.content),
                    R.string.map_hint_drag_markers, Snackbar.LENGTH_LONG).show();
            return;
        }
        controller.updateConfig(updated);
        Snackbar.make(findViewById(android.R.id.content),
                R.string.route_saved, Snackbar.LENGTH_SHORT).show();
        finish();
    }

    private void drawAll() {
        if (map == null) return;
        map.clear();
        startMarker = null;
        endMarker = null;
        waypointMarkers.clear();
        preview = null;
        roadRoute = null;
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

        List<LatLng> polyline = !fetchedPolyline.isEmpty() ? fetchedPolyline : c.routePolyline;
        if (!polyline.isEmpty()) {
            PolylineOptions rp = new PolylineOptions().width(10f).color(0xFF1565C0);
            for (LatLng p : polyline) rp.add(p.toGms());
            roadRoute = map.addPolyline(rp);
        } else {
            List<com.google.android.gms.maps.model.LatLng> previewPoints = previewPath(c, 60);
            preview = map.addPolyline(new PolylineOptions()
                    .addAll(previewPoints)
                    .width(8f)
                    .color(0xFF0B61A4));
        }

        List<LatLng> trail = controller.getTrajectory();
        if (trail.size() > 1) {
            PolylineOptions lp = new PolylineOptions().width(6f).color(0xFF0F8E50);
            for (LatLng p : trail) lp.add(p.toGms());
            live = map.addPolyline(lp);
        }

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
        fetchedPolyline.clear();
        fetchedSteps.clear();
        drawAll();
        fetchDirectionsAsync();
    }

    private void fetchDirectionsAsync() {
        if (directionsFetchInFlight || map == null) return;
        SpoofConfig c = controller.getConfig();
        if (c.isUsingPlaceholderRoute()) return;

        String key = BuildConfig.MAPS_API_KEY;
        if (key == null || key.isEmpty() || "YOUR_GOOGLE_MAPS_API_KEY".equals(key)) return;

        directionsFetchInFlight = true;
        Snackbar.make(findViewById(android.R.id.content),
                R.string.directions_loading, Snackbar.LENGTH_SHORT).show();

        final LatLng origin = c.start;
        final LatLng dest = c.end;
        final List<LatLng> via = new ArrayList<>(c.waypoints);

        ioExecutor.execute(() -> {
            try {
                DirectionsClient.RouteResult result =
                        DirectionsClient.fetch(origin, dest, via, key);
                runOnUiThread(() -> applyDirectionsResult(result));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    directionsFetchInFlight = false;
                    Snackbar.make(findViewById(android.R.id.content),
                            R.string.directions_failed, Snackbar.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void applyDirectionsResult(DirectionsClient.RouteResult result) {
        directionsFetchInFlight = false;
        fetchedPolyline = new ArrayList<>(result.polyline);
        fetchedSteps = new ArrayList<>();
        for (DirectionsClient.DirectionStep step : result.steps) {
            fetchedSteps.add(step.instruction);
        }
        SpoofConfig c = controller.getConfig();
        controller.updateConfig(c.toBuilder()
                .routePolyline(fetchedPolyline)
                .directionSteps(fetchedSteps)
                .build());
        showDirections(fetchedSteps);
        drawAll();
        fitToRoute();
    }

    private void showDirections(List<String> steps) {
        if (steps == null || steps.isEmpty()) {
            directionsCard.setVisibility(View.GONE);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            sb.append(i + 1).append(". ").append(steps.get(i));
            if (i < steps.size() - 1) sb.append("\n\n");
        }
        txtDirections.setText(sb.toString());
        directionsCard.setVisibility(View.VISIBLE);
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
        fetchDirectionsAsync();
    }

    private void fitToRoute() {
        if (map == null) return;
        SpoofConfig c = controller.getConfig();
        List<LatLng> poly = !fetchedPolyline.isEmpty() ? fetchedPolyline : c.routePolyline;
        if (!poly.isEmpty()) {
            LatLngBounds.Builder b = new LatLngBounds.Builder();
            for (LatLng p : poly) b.include(p.toGms());
            try {
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 120));
                return;
            } catch (Exception ignored) {
            }
        }
        if (c.isUsingPlaceholderRoute()) return;
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
        org.json.JSONObject session = config.toEngineJson(0);
        List<com.google.android.gms.maps.model.LatLng> out = new ArrayList<>(steps + 1);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            try { session.put("progress", t); } catch (org.json.JSONException ignored) {}
            NativeRouteSimulator sim = new NativeRouteSimulator(session);
            NativeRouteSimulator.TickResult r = sim.tick(0);
            out.add(new com.google.android.gms.maps.model.LatLng(r.lat, r.lon));
        }
        return out;
    }
}
