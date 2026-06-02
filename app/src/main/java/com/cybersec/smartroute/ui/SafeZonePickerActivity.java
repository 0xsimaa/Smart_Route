package com.cybersec.smartroute.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.cybersec.smartroute.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

public class SafeZonePickerActivity extends AppCompatActivity {

    private LatLng selected;
    private Marker marker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safe_zone_picker);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        double lat = getIntent().getDoubleExtra(SettingsActivity.EXTRA_SAFE_LAT, 0.0);
        double lon = getIntent().getDoubleExtra(SettingsActivity.EXTRA_SAFE_LON, 0.0);
        selected = new LatLng(lat, lon);
        boolean placeholder = lat == 0.0 && lon == 0.0;

        SupportMapFragment frag = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.safeMapFragment);
        if (frag != null) {
            frag.getMapAsync(map -> {
                // If we have no real safe-zone yet, zoom out to a world view so
                // the user can pan to a sensible spot rather than landing on
                // Null Island at street zoom.
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                        selected, placeholder ? 2f : 12f));
                marker = map.addMarker(new MarkerOptions()
                        .position(selected)
                        .draggable(true)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET))
                        .title("Safe zone"));
                map.setOnMapClickListener(p -> {
                    selected = p;
                    if (marker != null) marker.setPosition(p);
                });
                map.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
                    @Override public void onMarkerDragStart(Marker m) {}
                    @Override public void onMarkerDrag(Marker m) {}
                    @Override public void onMarkerDragEnd(Marker m) {
                        selected = m.getPosition();
                    }
                });
            });
        }

        ExtendedFloatingActionButton fab = findViewById(R.id.fabConfirm);
        fab.setOnClickListener(v -> {
            Intent out = new Intent();
            out.putExtra(SettingsActivity.EXTRA_SAFE_LAT, selected.latitude);
            out.putExtra(SettingsActivity.EXTRA_SAFE_LON, selected.longitude);
            setResult(RESULT_OK, out);
            finish();
        });
    }
}
