package com.cybersec.smartroute.engine;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

/**
 * Helpers around the "Select mock location app" developer setting and
 * Android runtime permissions.
 */
public final class MockLocationPermissions {

    private MockLocationPermissions() {
    }

    /** True if the OS treats this app as the active mock-location provider. */
    @SuppressWarnings("deprecation")
    public static boolean isMockLocationEnabled(Context context) {
        try {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
                String mock = Settings.Secure.getString(
                        context.getContentResolver(),
                        Settings.Secure.ALLOW_MOCK_LOCATION);
                return "1".equals(mock) || "true".equalsIgnoreCase(mock);
            }
            AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) return false;
            int mode;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mode = ops.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_MOCK_LOCATION,
                        Process.myUid(),
                        context.getPackageName());
            } else {
                mode = ops.checkOp(
                        AppOpsManager.OPSTR_MOCK_LOCATION,
                        Process.myUid(),
                        context.getPackageName());
            }
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    public static void openDeveloperSettings(Context context) {
        Intent[] candidates = new Intent[] {
                new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS),
        };
        for (Intent intent : candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
                return;
            }
        }
    }

    public static void openAppSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", context.getPackageName(), null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
