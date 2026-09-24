package com.aldo.rainlock;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.aldo.rainlock.guard.SearchGestureGuard;

/** Switches the accessibility service on for a lock by writing its setting. Needs the adb-granted permission. */
final class ServiceSwitch {
    private static final String TAG = "RainLock";
    private static final String STATE_FILE = "service_switch";
    private static final String LOCK_REQUESTED_AT = "lock_requested_at";
    /** A service that takes longer than this to come on doesn't lock; the caller reports the failure. */
    static final long REQUEST_EXPIRES_MS = 5000;

    private ServiceSwitch() {
    }

    /** True when the app can switch the service on itself, so it only needs to be on while locked. */
    static boolean onDemand(Context context) {
        return SearchGestureGuard.hasPermission(context);
    }

    /** Records a lock request and switches the service on. Returns false when it can't. */
    static boolean switchOnToLock(Context context) {
        if (!onDemand(context)) return false;
        state(context).edit().putLong(LOCK_REQUESTED_AT, SystemClock.elapsedRealtime()).commit();
        ContentResolver resolver = context.getContentResolver();
        ComponentName service = new ComponentName(context, RainLockService.class);
        String enabled = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        try {
            if (!contains(enabled, service)) {
                String entry = service.flattenToString();
                Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        TextUtils.isEmpty(enabled) ? entry : enabled + ":" + entry);
            }
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            return true;
        } catch (SecurityException e) {
            Log.w(TAG, "Could not switch the accessibility service on", e);
            return false;
        }
    }

    /** Whether the service was just switched on to lock. Answers true once per request. */
    static boolean takeLockRequest(Context context) {
        SharedPreferences state = state(context);
        long requestedAt = state.getLong(LOCK_REQUESTED_AT, -1);
        state.edit().remove(LOCK_REQUESTED_AT).commit();
        long age = SystemClock.elapsedRealtime() - requestedAt;  // negative after a reboot
        return requestedAt >= 0 && age >= 0 && age < REQUEST_EXPIRES_MS;
    }

    /** Drops a request the service never picked up. Returns whether there was one. */
    static boolean cancelLockRequest(Context context) {
        SharedPreferences state = state(context);
        if (!state.contains(LOCK_REQUESTED_AT)) return false;
        state.edit().remove(LOCK_REQUESTED_AT).commit();
        return true;
    }

    private static boolean contains(String enabled, ComponentName service) {
        if (TextUtils.isEmpty(enabled)) return false;
        for (String entry : enabled.split(":")) {
            if (service.equals(ComponentName.unflattenFromString(entry))) return true;
        }
        return false;
    }

    private static SharedPreferences state(Context context) {
        return context.getSharedPreferences(STATE_FILE, Context.MODE_PRIVATE);
    }
}
