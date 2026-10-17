package com.aldo.rainlock;

import android.content.Context;
import android.provider.Settings;

import androidx.annotation.Nullable;

/** How RainLock locks: the mode picked in the app if it's set up, otherwise the best one that is. See README.md. */
public enum Mode {
    NONE,
    /** An ordinary "Display over other apps" overlay, no accessibility service. */
    BASIC,
    /** The accessibility service's overlay, with the service switched on by hand. */
    BETTER,
    /** The accessibility service, switched on only while locked, and Circle to Search blocked. Needs adb. */
    BEST;

    public static Mode current(Context context) {
        Mode picked = picked(context);
        if (picked != null && picked.isSetUp(context)) return picked;
        for (Mode mode : new Mode[] {BEST, BETTER, BASIC}) {
            if (mode.isSetUp(context)) return mode;
        }
        return NONE;
    }

    /** Remembers the pick. A mode that isn't set up yet is used once it is. */
    public static void pick(Context context, Mode mode) {
        Prefs.get(context).edit().putString(Prefs.MODE, mode.name()).apply();
    }

    public boolean isSetUp(Context context) {
        switch (this) {
            case BASIC:
                return Settings.canDrawOverlays(context);
            case BETTER:  // on by hand; with the adb permission it's only on mid-lock, which is Best
                return RainLockService.running() != null && !ServiceSwitch.onDemand(context);
            case BEST:
                return ServiceSwitch.onDemand(context);
            default:
                return false;
        }
    }

    /** The mode picked in the app, set up or not; null if none was. */
    @Nullable
    public static Mode picked(Context context) {
        String name = Prefs.get(context).getString(Prefs.MODE, null);
        if (name == null) return null;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
