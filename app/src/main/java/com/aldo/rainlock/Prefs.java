package com.aldo.rainlock;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

/** Preference keys shared by the settings screen and the service. Defaults live in preferences.xml. */
public final class Prefs {
    public static final String UNLOCK_VOLUME = "unlock_volume";
    public static final String UNLOCK_SLIDE = "unlock_slide";
    public static final String UNLOCK_DOUBLE_TAP = "unlock_double_tap";
    public static final String TRIGGER_TILE = "trigger_tile";
    public static final String TRIGGER_FLOATING = "trigger_floating";
    public static final String TRIGGER_BLUETOOTH = "trigger_bluetooth";
    public static final String BLUETOOTH_DEVICE = "bluetooth_device";
    public static final String BLUETOOTH_AUTO_UNLOCK = "bluetooth_auto_unlock";
    public static final String GUARD_SEARCH = "guard_search";
    public static final String KEEP_SCREEN_ON = "keep_screen_on";
    public static final String FLOATING_X = "floating_x";
    public static final String FLOATING_Y = "floating_y";
    public static final String KNOB_RIGHT = "knob_right";
    public static final String KNOB_Y = "knob_y";

    private Prefs() {
    }

    public static SharedPreferences get(Context context) {
        PreferenceManager.setDefaultValues(context, R.xml.preferences, false);
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static boolean slideUnlock(SharedPreferences prefs) {
        return prefs.getBoolean(UNLOCK_SLIDE, true);
    }

    public static boolean doubleTapUnlock(SharedPreferences prefs) {
        return prefs.getBoolean(UNLOCK_DOUBLE_TAP, false);
    }

    /** Stays on when every method is off, so a lock can always be opened. */
    public static boolean volumeUnlock(SharedPreferences prefs) {
        return prefs.getBoolean(UNLOCK_VOLUME, true) || (!slideUnlock(prefs) && !doubleTapUnlock(prefs));
    }
}
