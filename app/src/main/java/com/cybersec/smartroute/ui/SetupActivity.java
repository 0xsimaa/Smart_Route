package com.cybersec.smartroute.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.cybersec.smartroute.BuildConfig;
import com.cybersec.smartroute.R;
import com.cybersec.smartroute.service.SpoofController;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class SetupActivity extends AppCompatActivity {

    private SpoofController controller;
    private LinearLayout stepsContainer;
    private View progressBar;
    private ImageView statusIcon;
    private TextView statusText;
    private MaterialButton btnContinue;

    private final androidx.activity.result.ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    res -> refresh());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);
        controller = SpoofController.get(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        stepsContainer = findViewById(R.id.stepsContainer);
        progressBar = findViewById(R.id.progressBar);
        statusIcon = findViewById(R.id.statusIcon);
        statusText = findViewById(R.id.statusText);
        btnContinue = findViewById(R.id.btnContinue);
        MaterialButton btnRecheck = findViewById(R.id.btnRecheck);
        btnRecheck.setOnClickListener(v -> {
            requestRuntimePermissions();
            refresh();
        });
        btnContinue.setOnClickListener(v -> finish());

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean ready = controller.checkMockReady() && hasMapsApiKey();
        renderSteps(controller.checkMockReady(), hasMapsApiKey());
        statusIcon.setImageResource(ready
                ? android.R.drawable.checkbox_on_background
                : android.R.drawable.ic_dialog_alert);
        statusText.setText(ready ? R.string.setup_status_ready : R.string.setup_status_not_ready);
        btnContinue.setVisibility(ready ? View.VISIBLE : View.GONE);
        progressBar.setVisibility(View.GONE);
    }

    private boolean hasMapsApiKey() {
        String key = BuildConfig.MAPS_API_KEY;
        return key != null && !key.isEmpty()
                && !"YOUR_GOOGLE_MAPS_API_KEY".equals(key);
    }

    private void renderSteps(boolean mockReady, boolean mapsKeyOk) {
        stepsContainer.removeAllViews();
        boolean permsOk = hasLocationPerm() && hasNotificationPerm();
        addStep(1,
                getString(R.string.setup_step1_title),
                getString(R.string.setup_step1_sub),
                /* done */ true, null, null);
        addStep(2,
                getString(R.string.setup_step2_title),
                getString(R.string.setup_step2_sub),
                mockReady,
                getString(R.string.action_open),
                v -> controller.openDeveloperSettings());
        addStep(3,
                getString(R.string.setup_step3_title),
                getString(R.string.setup_step3_sub),
                permsOk,
                getString(R.string.action_grant),
                v -> requestRuntimePermissions());
        addStep(4,
                getString(R.string.setup_step4_title),
                mapsKeyOk ? getString(R.string.setup_step4_sub)
                        : getString(R.string.setup_maps_missing),
                mapsKeyOk, null, null);
    }

    private void addStep(int number, String title, String subtitle,
                         boolean done, String actionLabel, View.OnClickListener onAction) {
        View row = LayoutInflater.from(this).inflate(
                R.layout.item_setup_step, stepsContainer, false);
        TextView numTv = row.findViewById(R.id.stepNumber);
        TextView titleTv = row.findViewById(R.id.stepTitle);
        TextView subTv = row.findViewById(R.id.stepSubtitle);
        MaterialButton btn = row.findViewById(R.id.stepAction);
        numTv.setText(String.valueOf(number));
        numTv.setBackgroundResource(done ? R.drawable.circle_step_done : R.drawable.circle_step_idle);
        titleTv.setText(title);
        subTv.setText(subtitle);
        if (actionLabel != null && onAction != null) {
            btn.setVisibility(View.VISIBLE);
            btn.setText(actionLabel);
            btn.setOnClickListener(onAction);
        }
        ((ViewGroup) stepsContainer).addView(row);
    }

    private boolean hasLocationPerm() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasNotificationPerm() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestRuntimePermissions() {
        List<String> needed = new ArrayList<>();
        if (!hasLocationPerm()) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (!hasNotificationPerm()) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!needed.isEmpty()) permissionLauncher.launch(needed.toArray(new String[0]));
    }
}
