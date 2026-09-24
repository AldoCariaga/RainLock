package com.aldo.rainlock.guard;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;

import java.util.Map;

/** Turns Circle to Search's hold-the-handle gesture off while locked. Needs the adb-granted permission (README.md). */
public final class SearchGestureGuard {
    private static final String TAG = "RainLock";
    private static final String STATE_FILE = "search_guard";
    private static final String ACTIVE = "active";
    private static final String SAVED = "saved:";  // prefix for the value each key had before we changed it
    private static final String ON = "1";

    private final Context context;
    private final ContentResolver resolver;
    private final SharedPreferences state;

    public SearchGestureGuard(Context context) {
        this.context = context.getApplicationContext();
        this.resolver = context.getContentResolver();
        this.state = context.getSharedPreferences(STATE_FILE, Context.MODE_PRIVATE);
    }

    public static boolean hasPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Turns the gesture off. Returns false when the permission hasn't been granted. */
    public boolean disable() {
        if (!hasPermission(context)) return false;
        if (!state.getBoolean(ACTIVE, false)) {
            SharedPreferences.Editor editor = state.edit().putBoolean(ACTIVE, true);
            for (String key : DeviceProfile.current().searchKeys) editor.putString(SAVED + key, read(key));
            editor.commit();  // on disk before any setting changes, so a crash still leads to a restore
        }
        for (String key : DeviceProfile.current().searchKeys) write(key, "0");
        return true;
    }

    /** Puts every key back as it was. Safe to call when nothing was changed. */
    public void restore() {
        if (!state.getBoolean(ACTIVE, false) || !hasPermission(context)) return;
        boolean allRestored = true;
        for (Map.Entry<String, ?> entry : state.getAll().entrySet()) {
            if (entry.getKey().startsWith(SAVED)) {
                allRestored &= write(entry.getKey().substring(SAVED.length()), (String) entry.getValue());
            }
        }
        if (allRestored) state.edit().clear().commit();
    }

    private String read(String key) {
        try {
            String value = Settings.Secure.getString(resolver, key);
            return value == null ? ON : value;  // the key only appears once the toggle is used; missing means on
        } catch (SecurityException e) {
            return ON;  // Android can hide the value from apps; the guard is only used when the gesture is on
        }
    }

    private boolean write(String key, String value) {
        try {
            return Settings.Secure.putString(resolver, key, value);
        } catch (SecurityException | IllegalArgumentException e) {
            Log.w(TAG, "Could not set " + key, e);
            return false;
        }
    }
}
