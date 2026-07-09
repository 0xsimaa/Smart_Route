package com.cybersec.smartroute.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.cybersec.smartroute.R;
import com.cybersec.smartroute.model.AccelerationPattern;
import com.cybersec.smartroute.model.LatLng;
import com.cybersec.smartroute.model.PathShape;
import com.cybersec.smartroute.model.SpoofConfig;
import com.cybersec.smartroute.service.SpoofController;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.slider.Slider;

import java.util.Arrays;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    public static final String EXTRA_SAFE_LAT = "safe_lat";
    public static final String EXTRA_SAFE_LON = "safe_lon";

    private SpoofController controller;

    private RangeSlider sliderSpeed;
    private Slider sliderInterval;
    private Slider sliderDuration;
    private MaterialSwitch switchPauses;
    private MaterialButtonToggleGroup toggleShape;
    private Spinner spinnerAccel;
    private Slider sliderAutoReset;
    private TextView lblSpeed;
    private TextView lblInterval;
    private TextView lblDuration;
    private TextView lblAutoReset;
    private TextView txtSafeZone;
    private LatLng selectedSafeZone;

    private final androidx.activity.result.ActivityResultLauncher<Intent> safeZoneLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                Intent data = result.getData();
                double lat = data.getDoubleExtra(EXTRA_SAFE_LAT, selectedSafeZone.latitude);
                double lon = data.getDoubleExtra(EXTRA_SAFE_LON, selectedSafeZone.longitude);
                selectedSafeZone = new LatLng(lat, lon);
                renderLabels();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        controller = SpoofController.get(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        sliderSpeed = findViewById(R.id.sliderSpeed);
        sliderInterval = findViewById(R.id.sliderInterval);
        sliderDuration = findViewById(R.id.sliderDuration);
        switchPauses = findViewById(R.id.switchPauses);
        toggleShape = findViewById(R.id.toggleShape);
        spinnerAccel = findViewById(R.id.spinnerAccel);
        sliderAutoReset = findViewById(R.id.sliderAutoReset);
        lblSpeed = findViewById(R.id.lblSpeed);
        lblInterval = findViewById(R.id.lblInterval);
        lblDuration = findViewById(R.id.lblDuration);
        lblAutoReset = findViewById(R.id.lblAutoReset);
        txtSafeZone = findViewById(R.id.txtSafeZone);
        MaterialCardView safeZoneCard = findViewById(R.id.safeZoneCard);

        SpoofConfig c = controller.getConfig();
        selectedSafeZone = c.safeZone;
        sliderSpeed.setValues((float) c.minSpeedKmh, (float) c.maxSpeedKmh);
        sliderInterval.setValue(c.updateIntervalSeconds);
        sliderDuration.setValue(c.durationMinutes);
        switchPauses.setChecked(c.enablePauses);
        toggleShape.check(c.pathShape == PathShape.CURVED ? R.id.shapeCurved : R.id.shapeStraight);
        sliderAutoReset.setValue(c.autoResetMinutes == null ? 0 : c.autoResetMinutes);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                Arrays.asList("smooth", "constant", "sudden"));
        spinnerAccel.setAdapter(adapter);
        spinnerAccel.setSelection(adapter.getPosition(c.acceleration.wireName()));

        renderLabels();
        sliderSpeed.addOnChangeListener((s, value, fromUser) -> renderLabels());
        sliderInterval.addOnChangeListener((s, value, fromUser) -> renderLabels());
        sliderDuration.addOnChangeListener((s, value, fromUser) -> renderLabels());
        sliderAutoReset.addOnChangeListener((s, value, fromUser) -> renderLabels());

        safeZoneCard.setOnClickListener(v -> {
            Intent i = new Intent(this, SafeZonePickerActivity.class);
            i.putExtra(EXTRA_SAFE_LAT, selectedSafeZone.latitude);
            i.putExtra(EXTRA_SAFE_LON, selectedSafeZone.longitude);
            safeZoneLauncher.launch(i);
        });

        MaterialButton btnApply = findViewById(R.id.btnApply);
        btnApply.setOnClickListener(v -> apply());
    }

    private void renderLabels() {
        java.util.List<Float> speeds = sliderSpeed.getValues();
        int interval = (int) sliderInterval.getValue();
        int duration = (int) sliderDuration.getValue();
        int autoReset = (int) sliderAutoReset.getValue();
        lblSpeed.setText(String.format(Locale.US, "%.0f – %.0f km/h",
                speeds.get(0), speeds.get(1)));
        lblInterval.setText(String.format(getString(R.string.update_interval_label), interval));
        lblDuration.setText(String.format(getString(R.string.duration_label), duration));
        lblAutoReset.setText(String.format(getString(R.string.auto_reset_label),
                autoReset == 0 ? getString(R.string.auto_reset_off) : (autoReset + " min")));
        txtSafeZone.setText(String.format(Locale.US, "%.4f, %.4f",
                selectedSafeZone.latitude, selectedSafeZone.longitude));
    }

    private void apply() {
        java.util.List<Float> speeds = sliderSpeed.getValues();
        double minSpeed = speeds.get(0);
        double maxSpeed = speeds.get(1);
        int interval = (int) sliderInterval.getValue();
        int duration = (int) sliderDuration.getValue();
        int autoReset = (int) sliderAutoReset.getValue();
        AccelerationPattern accel = AccelerationPattern.fromWire(
                (String) spinnerAccel.getSelectedItem());
        PathShape shape = toggleShape.getCheckedButtonId() == R.id.shapeCurved
                ? PathShape.CURVED : PathShape.STRAIGHT;

        SpoofConfig newConfig = controller.getConfig().toBuilder()
                .speedRange(minSpeed, maxSpeed)
                .updateIntervalSeconds(interval)
                .durationMinutes(duration)
                .autoResetMinutes(autoReset == 0 ? null : autoReset)
                .acceleration(accel)
                .pathShape(shape)
                .enablePauses(switchPauses.isChecked())
                .safeZone(selectedSafeZone)
                .build();
        controller.updateConfig(newConfig);
        finish();
    }
}
