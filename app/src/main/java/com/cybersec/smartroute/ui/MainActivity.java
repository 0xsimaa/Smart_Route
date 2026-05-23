package com.cybersec.smartroute.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.cybersec.smartroute.R;
import com.cybersec.smartroute.model.LatLng;
import com.cybersec.smartroute.model.PrivacyStatus;
import com.cybersec.smartroute.model.SpoofConfig;
import com.cybersec.smartroute.model.SpoofMode;
import com.cybersec.smartroute.service.SpoofController;
import com.cybersec.smartroute.util.GpxExporter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SpoofController.Listener {

    private SpoofController controller;

    private TextView statusBanner;
    private MaterialCardView setupHint;
    private TextView txtCoords;
    private MaterialButtonToggleGroup modeToggle;
    private MaterialButton modeStatic;
    private MaterialButton modeDynamic;
    private LinearProgressIndicator progressBar;
    private TextView txtProgress;
    private TextView txtRemaining;
    private MaterialButton btnStart;
    private MaterialButton btnPause;
    private MaterialButton btnStop;
    private MaterialButton btnMap;
    private MaterialButton btnSettings;
    private MaterialButton btnExportGpx;
    private MaterialButton btnSetup;

    private final androidx.activity.result.ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> controller.checkMockReady());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        controller = SpoofController.get(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_setup) {
                startActivity(new Intent(this, SetupActivity.class));
                return true;
            } else if (id == R.id.action_audit) {
                showAuditDialog();
                return true;
            }
            return false;
        });

        statusBanner = findViewById(R.id.statusBanner);
        setupHint = findViewById(R.id.setupHint);
        txtCoords = findViewById(R.id.txtCoords);
        progressBar = findViewById(R.id.progressBar);
        txtProgress = findViewById(R.id.txtProgress);
        txtRemaining = findViewById(R.id.txtRemaining);
        btnStart = findViewById(R.id.btnStart);
        btnPause = findViewById(R.id.btnPause);
        btnStop = findViewById(R.id.btnStop);
        btnMap = findViewById(R.id.btnMap);
        btnSettings = findViewById(R.id.btnSettings);
        btnExportGpx = findViewById(R.id.btnExportGpx);
        btnSetup = findViewById(R.id.btnSetup);
        modeToggle = findViewById(R.id.modeToggle);
        modeStatic = findViewById(R.id.modeStatic);
        modeDynamic = findViewById(R.id.modeDynamic);

        btnStart.setOnClickListener(v -> {
            if (!controller.isMockAppReady()) {
                startActivity(new Intent(this, SetupActivity.class));
                return;
            }
            controller.start();
        });
        btnPause.setOnClickListener(v -> {
            if (controller.isPaused()) controller.resume();
            else controller.pause();
        });
        btnStop.setOnClickListener(v -> controller.stop(true));
        btnMap.setOnClickListener(v -> startActivity(new Intent(this, MapActivity.class)));
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnExportGpx.setOnClickListener(v -> exportGpx());
        btnSetup.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));

        modeStatic.setOnClickListener(v -> setMode(SpoofMode.STATIC_LOCATION));
        modeDynamic.setOnClickListener(v -> setMode(SpoofMode.DYNAMIC_PATH));

        ensureRuntimePermissions();
    }

    private void setMode(SpoofMode mode) {
        if (controller.isActive()) {
            renderState();
            return;
        }
        SpoofConfig c = controller.getConfig().toBuilder().mode(mode).build();
        controller.updateConfig(c);
    }

    private void ensureRuntimePermissions() {
        java.util.List<String> needed = new java.util.ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!needed.isEmpty()) {
            permissionLauncher.launch(needed.toArray(new String[0]));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        controller.addListener(this);
        controller.checkMockReady();
        renderState();
    }

    @Override
    protected void onStop() {
        controller.removeListener(this);
        super.onStop();
    }

    @Override
    public void onStateChanged(SpoofController c) {
        renderState();
    }

    private void renderState() {
        SpoofConfig config = controller.getConfig();
        boolean active = controller.isActive();
        boolean paused = controller.isPaused();

        // Mode toggle (without re-triggering listeners)
        modeToggle.clearChecked();
        modeToggle.check(config.mode == SpoofMode.STATIC_LOCATION
                ? R.id.modeStatic : R.id.modeDynamic);
        modeStatic.setEnabled(!active);
        modeDynamic.setEnabled(!active);

        setupHint.setVisibility(controller.isMockAppReady() ? View.GONE : View.VISIBLE);

        // Status banner
        PrivacyStatus s = controller.getStatus();
        String msg = controller.getStatusMessage();
        switch (s) {
            case ACTIVE:
                statusBanner.setBackgroundColor(ContextCompat.getColor(this, R.color.status_active));
                statusBanner.setTextColor(Color.WHITE);
                statusBanner.setText(msg != null ? msg : getString(R.string.status_active));
                break;
            case FALLBACK:
                statusBanner.setBackgroundColor(ContextCompat.getColor(this, R.color.status_fallback));
                statusBanner.setTextColor(Color.WHITE);
                statusBanner.setText(msg != null ? msg : getString(R.string.status_fallback));
                break;
            case LEAK_WARNING:
                statusBanner.setBackgroundColor(ContextCompat.getColor(this, R.color.status_leak));
                statusBanner.setTextColor(Color.WHITE);
                statusBanner.setText(msg != null ? msg : getString(R.string.status_leak_warning));
                break;
            case ERROR:
                statusBanner.setBackgroundColor(ContextCompat.getColor(this, R.color.status_error));
                statusBanner.setTextColor(Color.WHITE);
                statusBanner.setText(msg != null ? msg : getString(R.string.status_error));
                break;
            case IDLE:
            default:
                statusBanner.setBackgroundResource(R.drawable.bg_status_banner);
                statusBanner.setTextColor(ContextCompat.getColor(this, R.color.md_theme_on_background));
                statusBanner.setText(msg != null ? msg : getString(R.string.status_idle));
                break;
        }

        // Coordinates
        LatLng cur = controller.getCurrentPosition();
        if (cur == null) {
            txtCoords.setText(R.string.not_broadcasting);
        } else {
            txtCoords.setText(String.format(Locale.US, "%.5f, %.5f",
                    cur.latitude, cur.longitude));
        }

        // Progress
        if (config.mode == SpoofMode.DYNAMIC_PATH) {
            progressBar.setVisibility(View.VISIBLE);
            txtProgress.setVisibility(View.VISIBLE);
            int pct = (int) Math.round(controller.getProgress() * 100);
            progressBar.setProgress(pct);
            if (active) {
                txtProgress.setText(String.format(
                        getString(R.string.progress_format), controller.getProgress() * 100));
            } else {
                txtProgress.setText(String.format(Locale.US,
                        "Duration: %d min", config.durationMinutes));
            }
            Long remainMs = controller.getRemainingMs();
            if (remainMs != null) {
                long m = remainMs / 60000L;
                long s2 = (remainMs / 1000L) % 60;
                txtRemaining.setVisibility(View.VISIBLE);
                txtRemaining.setText(String.format(getString(R.string.remaining_format), m, s2));
            } else {
                txtRemaining.setVisibility(View.GONE);
            }
        } else {
            progressBar.setVisibility(View.GONE);
            txtProgress.setVisibility(View.GONE);
            txtRemaining.setVisibility(View.GONE);
        }

        // Buttons
        btnStart.setEnabled(!active);
        btnPause.setEnabled(active);
        btnPause.setText(paused ? R.string.action_resume : R.string.action_pause);
        btnStop.setEnabled(active);
        btnExportGpx.setEnabled(!controller.getTrajectory().isEmpty());
    }

    private void exportGpx() {
        List<LatLng> points = controller.getTrajectory();
        if (points.isEmpty()) {
            Toast.makeText(this, "No trajectory to export yet", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = "smart_route_" + System.currentTimeMillis();
        String gpx = GpxExporter.build(name, points,
                controller.getSessionStartMs() == 0
                        ? System.currentTimeMillis() : controller.getSessionStartMs(),
                controller.getConfig().updateIntervalSeconds);
        File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) dir = getFilesDir();
        File outFile = new File(dir, name + ".gpx");
        try (FileWriter w = new FileWriter(outFile)) {
            w.write(gpx);
        } catch (IOException e) {
            Toast.makeText(this, "GPX write failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            return;
        }
        Snackbar.make(findViewById(android.R.id.content),
                "GPX saved: " + outFile.getAbsolutePath(),
                Snackbar.LENGTH_LONG).show();
    }

    private void showAuditDialog() {
        List<String> logs = controller.readAuditLog();
        StringBuilder sb = new StringBuilder();
        if (logs.isEmpty()) {
            sb.append(getString(R.string.audit_empty));
        } else {
            for (int i = logs.size() - 1; i >= 0; i--) {
                sb.append(logs.get(i)).append("\n");
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.audit_title)
                .setMessage(sb.toString())
                .setPositiveButton(R.string.action_close, null)
                .show();
    }

    @SuppressWarnings("unused")
    private String formatRelative(long epochMs) {
        return DateUtils.getRelativeTimeSpanString(epochMs).toString();
    }
}
