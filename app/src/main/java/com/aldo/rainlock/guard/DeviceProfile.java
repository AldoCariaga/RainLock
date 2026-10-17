package com.aldo.rainlock.guard;

import android.os.Build;

import java.util.List;

/** Which Circle to Search settings the app writes on this phone. */
public final class DeviceProfile {
    // Verified on a Galaxy S22+ (One UI 8, Android 16): either key alone blocks the gesture.
    private static final List<String> SAMSUNG_KEYS =
            List.of("touch_and_hold_to_search", "search_all_entrypoints_enabled");
    // Untested elsewhere: the brand-neutral one of the two Samsung keys.
    private static final List<String> OTHER_KEYS = List.of("search_all_entrypoints_enabled");

    public final List<String> searchKeys;

    private DeviceProfile(List<String> searchKeys) {
        this.searchKeys = searchKeys;
    }

    public static DeviceProfile current() {
        if (!hasCircleToSearch()) return new DeviceProfile(List.of());
        return new DeviceProfile("samsung".equalsIgnoreCase(Build.MANUFACTURER) ? SAMSUNG_KEYS : OTHER_KEYS);
    }

    /** Circle to Search shipped with Android 14, so older phones (e.g. a Redmi Note 11 on 13) don't have it. */
    public static boolean hasCircleToSearch() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
    }
}
