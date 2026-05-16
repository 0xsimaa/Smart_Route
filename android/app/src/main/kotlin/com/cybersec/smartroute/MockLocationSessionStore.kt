package com.cybersec.smartroute

import android.content.Context
import org.json.JSONObject

/**
 * Persists active mock session so the foreground service survives process death.
 */
class MockLocationSessionStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveSession(json: JSONObject) {
        prefs.edit()
            .putString(KEY_SESSION, json.toString())
            .putBoolean(KEY_ACTIVE, true)
            .apply()
    }

    fun loadSession(): JSONObject? {
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return null
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_SESSION)
            .putBoolean(KEY_ACTIVE, false)
            .apply()
    }

    fun isSessionActive(): Boolean = prefs.getBoolean(KEY_ACTIVE, false)

    companion object {
        private const val PREFS = "smart_route_session"
        private const val KEY_SESSION = "session_json"
        private const val KEY_ACTIVE = "session_active"
    }
}
