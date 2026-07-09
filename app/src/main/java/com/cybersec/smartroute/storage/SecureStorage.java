package com.cybersec.smartroute.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.cybersec.smartroute.model.SpoofConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Encrypted-at-rest config + audit log. Falls back to plain prefs when
 * Keystore initialisation fails (rare; keeps the app functional on
 * restored devices with corrupt master keys).
 */
public final class SecureStorage {

    private static final String TAG = "SecureStorage";
    private static final String PREFS = "smart_route_secure";
    private static final String KEY_CONFIG = "spoof_config_v2";
    private static final String KEY_AUDIT = "audit_log_v2";
    private static final String KEY_SESSION_ACTIVE = "session_active_v2";

    private final SharedPreferences prefs;

    public SecureStorage(Context context) {
        this.prefs = openPrefs(context);
    }

    private static SharedPreferences openPrefs(Context context) {
        Context app = context.getApplicationContext();
        try {
            MasterKey masterKey = new MasterKey.Builder(app)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    app,
                    PREFS,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | java.io.IOException ex) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable, using plain prefs", ex);
            return app.getSharedPreferences(PREFS + "_plain", Context.MODE_PRIVATE);
        }
    }

    public void saveConfig(SpoofConfig config) {
        prefs.edit().putString(KEY_CONFIG, config.toJson().toString()).apply();
    }

    public SpoofConfig loadConfig() {
        String raw = prefs.getString(KEY_CONFIG, null);
        if (raw == null) return null;
        try {
            return SpoofConfig.fromJson(new JSONObject(raw));
        } catch (JSONException ex) {
            return null;
        }
    }

    public void appendAudit(String message) {
        try {
            String raw = prefs.getString(KEY_AUDIT, null);
            JSONArray arr = raw == null ? new JSONArray() : new JSONArray(raw);
            String stamp = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date());
            arr.put(stamp + " | " + message);
            while (arr.length() > 200) arr.remove(0);
            prefs.edit().putString(KEY_AUDIT, arr.toString()).apply();
        } catch (JSONException ex) {
            Log.w(TAG, "appendAudit failed", ex);
        }
    }

    public List<String> readAudit() {
        List<String> out = new ArrayList<>();
        String raw = prefs.getString(KEY_AUDIT, null);
        if (raw == null) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                out.add(arr.optString(i, ""));
            }
        } catch (JSONException ex) {
            Log.w(TAG, "readAudit failed", ex);
        }
        return out;
    }

    public void setSessionActive(boolean active) {
        prefs.edit().putBoolean(KEY_SESSION_ACTIVE, active).commit();
    }

    public boolean wasSessionActive() {
        return prefs.getBoolean(KEY_SESSION_ACTIVE, false);
    }
}
