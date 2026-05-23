package com.cybersec.smartroute.storage;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Plain SharedPreferences slot for the active mock session. Kept separate
 * from the encrypted config store so the foreground service / boot receiver
 * can read it without touching keystore.
 */
public final class MockLocationSessionStore {

    private static final String PREFS = "smart_route_session";
    private static final String KEY_SESSION = "session_json";
    private static final String KEY_ACTIVE = "session_active";

    private final SharedPreferences prefs;

    public MockLocationSessionStore(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void saveSession(JSONObject session) {
        prefs.edit()
                .putString(KEY_SESSION, session.toString())
                .putBoolean(KEY_ACTIVE, true)
                .apply();
    }

    public JSONObject loadSession() {
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return null;
        String raw = prefs.getString(KEY_SESSION, null);
        if (raw == null) return null;
        try {
            return new JSONObject(raw);
        } catch (JSONException ex) {
            return null;
        }
    }

    public void clearSession() {
        prefs.edit()
                .remove(KEY_SESSION)
                .putBoolean(KEY_ACTIVE, false)
                .apply();
    }

    public boolean isSessionActive() {
        return prefs.getBoolean(KEY_ACTIVE, false);
    }
}
