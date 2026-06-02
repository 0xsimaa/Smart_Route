package com.cybersec.smartroute.ui;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.cybersec.smartroute.BuildConfig;
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
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SpoofController.Listener {

    private SpoofController controller;

    private MaterialCardView setupHint;
    private View statusPill;
    private View statusDot;
    private TextView statusText;
    private TextView statusMessage;

    private MaterialButtonToggleGroup modeToggle;
    private MaterialButton modeStatic;
    private MaterialButton modeDynamic;

    private TextView txtStart;
    private TextView txtEnd;
    private TextView txtRouteMeta;

    private View tileCoords;
    private View tileSpeed;
    private View tileBearing;
    private View tileDistance;

    private LinearProgressIndicator progressBar;
    private TextView txtProgress;
    private TextView txtRemaining;

    private ExtendedFloatingActionButton fabStart;
    private MaterialButton btnPause;
    private MaterialButton btnStop;
    private MaterialButton btnExportGpx;
    private MaterialButton btnShareGpx;
    private MaterialButton btnSettings;
    private MaterialButton btnEditRoute;
    private MaterialButton btnSetup;

    private File lastGpxFile;

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
            } else if (id == R.id.action_about) {
                showAboutDialog();
                return true;
            }
            return false;
        });

        setupHint = findViewById(R.id.setupHint);
        statusPill = findViewById(R.id.statusPill);
        statusDot = findViewById(R.id.statusDot);
        statusText = findViewById(R.id.statusText);
        statusMessage = findViewById(R.id.statusMessage);

        modeToggle = findViewById(R.id.modeToggle);
        modeStatic = findViewById(R.id.modeStatic);
        modeDynamic = findViewById(R.id.modeDynamic);

        txtStart = findViewById(R.id.txtStart);
        txtEnd = findViewById(R.id.txtEnd);
        txtRouteMeta = findViewById(R.id.txtRouteMeta);

        tileCoords = findViewById(R.id.tileCoords);
        tileSpeed = findViewById(R.id.tileSpeed);
        tileBearing = findViewById(R.id.tileBearing);
        tileDistance = findViewById(R.id.tileDistance);
        decorateTile(tileCoords, R.string.metric_coords, R.drawable.ic_my_location);
        decorateTile(tileSpeed, R.string.metric_speed, R.drawable.ic_speed);
        decorateTile(tileBearing, R.string.metric_bearing, R.drawable.ic_compass);
        decorateTile(tileDistance, R.string.metric_distance, R.drawable.ic_distance);

        progressBar = findViewById(R.id.progressBar);
        txtProgress = findViewById(R.id.txtProgress);
        txtRemaining = findViewById(R.id.txtRemaining);

        fabStart = findViewById(R.id.fabStart);
        btnPause = findViewById(R.id.btnPause);
        btnStop = findViewById(R.id.btnStop);
        btnExportGpx = findViewById(R.id.btnExportGpx);
        btnShareGpx = findViewById(R.id.btnShareGpx);
        btnSettings = findViewById(R.id.btnSettings);
        btnEditRoute = findViewById(R.id.btnEditRoute);
        btnSetup = findViewById(R.id.btnSetup);

        fabStart.setOnClickListener(v -> {
            if (controller.isActive()) {
                if (controller.isPaused()) controller.resume();
                else controller.pause();
            } else {
                if (!controller.isMockAppReady()) {
                    startActivity(new Intent(this, SetupActivity.class));
                    return;
                }
                if (controller.getConfig().isUsingPlaceholderRoute()) {
                    showFirstRunDialog();
                    return;
                }
                controller.start();
            }
        });
        btnPause.setOnClickListener(v -> {
            if (controller.isPaused()) controller.resume();
            else controller.pause();
        });
        btnStop.setOnClickListener(v -> controller.stop(true));
        btnExportGpx.setOnClickListener(v -> exportGpx(false));
        btnShareGpx.setOnClickListener(v -> exportGpx(true));
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnEditRoute.setOnClickListener(v -> startActivity(new Intent(this, MapActivity.class)));
        btnSetup.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));

        modeStatic.setOnClickListener(v -> setMode(SpoofMode.STATIC_LOCATION));
        modeDynamic.setOnClickListener(v -> setMode(SpoofMode.DYNAMIC_PATH));

        // Long-press on coords tile to copy
        tileCoords.setOnLongClickListener(v -> {
            LatLng cur = controller.getCurrentPosition();
            if (cur == null) return true;
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("smart-route-coords",
                        formatLatLon(cur)));
                Snackbar.make(v, R.string.copied_to_clipboard, Snackbar.LENGTH_SHORT).show();
            }
            return true;
        });

        ensureRuntimePermissions();
    }

    private void decorateTile(View tile, int labelRes, int iconRes) {
        TextView label = tile.findViewById(R.id.metricLabel);
        ImageView icon = tile.findViewById(R.id.metricIcon);
        if (label != null) label.setText(labelRes);
        if (icon != null) icon.setImageResource(iconRes);
    }

    private void setTileValue(View tile, String value) {
        TextView v = tile.findViewById(R.id.metricValue);
        if (v != null) v.setText(value);
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

        // Mode toggle
        modeToggle.clearChecked();
        modeToggle.check(config.mode == SpoofMode.STATIC_LOCATION
                ? R.id.modeStatic : R.id.modeDynamic);
        modeStatic.setEnabled(!active);
        modeDynamic.setEnabled(!active);

        setupHint.setVisibility(controller.isMockAppReady() ? View.GONE : View.VISIBLE);

        // Status pill
        PrivacyStatus s = controller.getStatus();
        int pillColor;
        String label;
        if (paused && active) {
            pillColor = ContextCompat.getColor(this, R.color.status_fallback);
            label = getString(R.string.status_paused);
        } else {
            switch (s) {
                case ACTIVE:
                    pillColor = ContextCompat.getColor(this, R.color.status_active);
                    label = getString(R.string.status_active);
                    break;
                case FALLBACK:
                    pillColor = ContextCompat.getColor(this, R.color.status_fallback);
                    label = getString(R.string.status_fallback);
                    break;
                case LEAK_WARNING:
                    pillColor = ContextCompat.getColor(this, R.color.status_leak);
                    label = getString(R.string.status_leak_warning);
                    break;
                case ERROR:
                    pillColor = ContextCompat.getColor(this, R.color.status_error);
                    label = getString(R.string.status_error);
                    break;
                case IDLE:
                default:
                    pillColor = ContextCompat.getColor(this, R.color.status_idle);
                    label = getString(R.string.status_idle);
                    break;
            }
        }
        // Tint pill background while keeping pill shape
        android.graphics.drawable.GradientDrawable pillBg =
                (android.graphics.drawable.GradientDrawable)
                        ContextCompat.getDrawable(this, R.drawable.bg_status_pill);
        if (pillBg != null) {
            pillBg = (android.graphics.drawable.GradientDrawable) pillBg.mutate();
            pillBg.setColor(pillColor);
            statusPill.setBackground(pillBg);
        }
        statusText.setText(label);
        String msg = controller.getStatusMessage();
        statusMessage.setText(msg != null ? msg
                : (controller.isMockAppReady()
                        ? getString(R.string.hint_start_idle)
                        : getString(R.string.hint_setup_required)));

        // Route summary
        txtStart.setText(String.format(Locale.US, "Start: %.5f, %.5f",
                config.start.latitude, config.start.longitude));
        txtEnd.setText(String.format(Locale.US, "End: %.5f, %.5f",
                config.end.latitude, config.end.longitude));
        String shape = config.pathShape.name().toLowerCase();
        txtRouteMeta.setText(String.format(getString(R.string.route_meta_format),
                config.waypoints.size(), shape,
                config.minSpeedKmh, config.maxSpeedKmh,
                config.durationMinutes));

        // KPIs
        LatLng cur = controller.getCurrentPosition();
        setTileValue(tileCoords, cur == null ? "—" : formatLatLon(cur));
        setTileValue(tileSpeed, formatKmh(controller.getCurrentSpeedMps()));
        setTileValue(tileBearing, formatBearing(controller.getCurrentBearing()));
        setTileValue(tileDistance, formatKm(controller.getDistanceTraveledKm()));

        // Progress
        if (config.mode == SpoofMode.DYNAMIC_PATH) {
            progressBar.setVisibility(View.VISIBLE);
            txtProgress.setVisibility(View.VISIBLE);
            int pct = (int) Math.round(controller.getProgress() * 100);
            progressBar.setProgress(pct);
            if (active) {
                txtProgress.setText(String.format(getString(R.string.progress_format),
                        controller.getProgress() * 100));
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

        // Buttons & FAB
        if (active) {
            fabStart.setText(paused ? R.string.action_resume : R.string.action_pause);
            fabStart.setIconResource(paused ? R.drawable.ic_play : R.drawable.ic_pause);
        } else {
            fabStart.setText(R.string.action_start);
            fabStart.setIconResource(R.drawable.ic_play);
        }
        btnPause.setEnabled(active);
        btnPause.setText(paused ? R.string.action_resume : R.string.action_pause);
        btnPause.setIconResource(paused ? R.drawable.ic_play : R.drawable.ic_pause);
        btnStop.setEnabled(active);
        boolean hasTrail = !controller.getTrajectory().isEmpty();
        btnExportGpx.setEnabled(hasTrail);
        btnShareGpx.setEnabled(hasTrail || lastGpxFile != null);
    }

    private String formatLatLon(LatLng l) {
        return String.format(Locale.US, "%.5f, %.5f", l.latitude, l.longitude);
    }

    private String formatKmh(double mps) {
        return String.format(Locale.US, "%.1f km/h", mps * 3.6);
    }

    private String formatBearing(double deg) {
        return String.format(Locale.US, "%.0f°", ((deg % 360) + 360) % 360);
    }

    private String formatKm(double km) {
        return String.format(Locale.US, "%.2f km", km);
    }

    private void exportGpx(boolean shareAfter) {
        List<LatLng> points = controller.getTrajectory();
        if (points.isEmpty() && lastGpxFile == null) {
            Toast.makeText(this, R.string.no_trajectory_yet, Toast.LENGTH_SHORT).show();
            return;
        }
        File outFile = lastGpxFile;
        if (!points.isEmpty()) {
            String name = "smart_route_" + System.currentTimeMillis();
            String gpx = GpxExporter.build(name, points,
                    controller.getSessionStartMs() == 0
                            ? System.currentTimeMillis() : controller.getSessionStartMs(),
                    controller.getConfig().updateIntervalSeconds);
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) dir = getFilesDir();
            outFile = new File(dir, name + ".gpx");
            try (FileWriter w = new FileWriter(outFile)) {
                w.write(gpx);
            } catch (IOException e) {
                Toast.makeText(this, "GPX write failed: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
                return;
            }
            lastGpxFile = outFile;
        }
        if (shareAfter && outFile != null) {
            shareGpx(outFile);
        } else {
            Snackbar.make(findViewById(android.R.id.content),
                    String.format(getString(R.string.gpx_saved_at), outFile.getAbsolutePath()),
                    Snackbar.LENGTH_LONG).show();
        }
    }

    private void shareGpx(File file) {
        Uri uri = FileProvider.getUriForFile(
                this, getString(R.string.file_provider_authority), file);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/gpx+xml");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.putExtra(Intent.EXTRA_SUBJECT, "Smart Route GPX");
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Share GPX"));
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

    private void showFirstRunDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.first_run_title)
                .setIcon(R.drawable.ic_map)
                .setMessage(R.string.first_run_body)
                .setPositiveButton(R.string.first_run_open_map,
                        (d, w) -> startActivity(new Intent(this, MapActivity.class)))
                .setNegativeButton(R.string.action_close, null)
                .show();
    }

    private void showAboutDialog() {
        String body = String.format(getString(R.string.about_body), BuildConfig.VERSION_NAME);
        new AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setIcon(R.drawable.ic_info)
                .setMessage(body)
                .setPositiveButton(R.string.action_close, null)
                .show();
    }
}
