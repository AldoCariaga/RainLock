package com.aldo.rainlock;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.VibrationEffect;
import android.view.KeyEvent;

import com.aldo.rainlock.guard.SearchGestureGuard;
import com.aldo.rainlock.lock.LockOverlay;
import com.aldo.rainlock.unlock.PressSequence;

/** One lock: the overlay, the volume unlock and the Circle to Search guard. The service or LockController hosts it. */
final class LockSession {
    private static final long VOLUME_WINDOW_MS = 2000;

    private final Context context;
    private final SharedPreferences prefs;
    private final LockOverlay overlay;
    private final SearchGestureGuard searchGuard;
    private final PressSequence volumePresses = new PressSequence(3, VOLUME_WINDOW_MS);
    private final Runnable onUnlocked;

    /** {@code windowContext} must be able to add windows of {@code windowType}. */
    LockSession(Context windowContext, int windowType, Runnable onUnlocked) {
        this.context = windowContext;
        this.prefs = Prefs.get(windowContext);
        this.overlay = new LockOverlay(windowContext, prefs, windowType);
        this.searchGuard = new SearchGestureGuard(windowContext);
        this.onUnlocked = onUnlocked;
    }

    boolean isLocked() {
        return overlay.isShowing();
    }

    void lock() {
        if (isLocked()) return;
        if (prefs.getBoolean(Prefs.GUARD_SEARCH, true)) searchGuard.disable();
        volumePresses.reset();
        overlay.show(Prefs.slideUnlock(prefs), Prefs.doubleTapUnlock(prefs),
                prefs.getBoolean(Prefs.KEEP_SCREEN_ON, true), this::unlock, this::onKey);
        Haptics.play(context, VibrationEffect.EFFECT_HEAVY_CLICK);
    }

    void unlock() {
        if (!isLocked()) return;
        close();
        Haptics.play(context, VibrationEffect.EFFECT_DOUBLE_CLICK);
        onUnlocked.run();
    }

    /** Takes the lock down without the unlock buzz, for a service that's going away. */
    void close() {
        overlay.hide();
        searchGuard.restore();
    }

    /** Volume down unlocks after three presses. Returns whether the key was used. */
    boolean onKey(KeyEvent event) {
        if (!isLocked() || event.getKeyCode() != KeyEvent.KEYCODE_VOLUME_DOWN || !Prefs.volumeUnlock(prefs)) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (volumePresses.press(event.getEventTime())) unlock();
            else Haptics.play(context, VibrationEffect.EFFECT_TICK);
        }
        return true;  // while locked, volume down is reserved for unlocking
    }
}
