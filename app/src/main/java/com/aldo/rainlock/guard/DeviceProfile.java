package com.aldo.rainlock.guard;

import android.content.ContentResolver;
import android.os.Build;
import android.provider.Settings;

import java.util.List;

/** What the app detects about this phone, and which Circle to Search settings it uses there. */
public final class DeviceProfile {
    // Verified on a Galaxy S22+ (One UI 8, Android 16): either key alone blocks the gesture.
    private static final List<String> SAMSUNG_KEYS =
            List.of("touch_and_hold_to_search", "search_all_entrypoints_enabled");
    // Untested elsewhere: the brand-neutral one of the two Samsung keys.
    private static final List<String> OTHER_KEYS = List.of("search_all_entrypoints_enabled");

    public final String manufacturer;
    public final String model;
    public final String androidVersion;
    public final List<String> searchKeys;
    public final boolean searchKeysVerified;

    private DeviceProfile(String manufacturer, String model, String androidVersion,
                          List<String> searchKeys, boolean searchKeysVerified) {
        this.manufacturer = manufacturer;
        this.model = model;
        this.androidVersion = androidVersion;
        this.searchKeys = searchKeys;
        this.searchKeysVerified = searchKeysVerified;
    }

    public static DeviceProfile current() {
        boolean samsung = "samsung".equalsIgnoreCase(Build.MANUFACTURER);
        return new DeviceProfile(Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE,
                samsung ? SAMSUNG_KEYS : OTHER_KEYS, samsung);
    }

    /** 0 = 3 buttons, 1 = 2 buttons, 2 = gestures, -1 when the phone won't say. */
    public static int navigationMode(ContentResolver resolver) {
        try {
            return Settings.Secure.getInt(resolver, "navigation_mode", -1);
        } catch (SecurityException e) {
            return -1;
        }
    }
}
