package com.aldo.rainlock;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.aldo.rainlock.guard.SearchGestureGuard;
import com.aldo.rainlock.lock.LockOverlay;
import com.aldo.rainlock.unlock.PressSequence;

/**
 * Owns the lock. An accessibility service: only its overlays cover the system bars, and only it sees volume keys.
 * With the adb permission it's on only while locked, so banking apps that flag accessibility services don't see it.
 */
public final class RainLockService extends AccessibilityService {
    private static final long VOLUME_WINDOW_MS = 2000;

    @Nullable
    private static RainLockService running;  // touched on the main thread only

    private final PressSequence volumePresses = new PressSequence(3, VOLUME_WINDOW_MS);
    private SharedPreferences prefs;
    private LockOverlay overlay;
    private SearchGestureGuard searchGuard;

    @Nullable
    public static RainLockService running() {
        return running;
    }

    public static boolean isLockedNow() {
        return running != null && running.isLocked();
    }

    /** Whether a trigger can lock: the service is on, or the app can switch it on. */
    public static boolean canLock(Context context) {
        return running != null || ServiceSwitch.onDemand(context);
    }

    /** Locks from any trigger. When the service is off it's switched on, and locks once connected. */
    public static void requestLock(Context context) {
        if (running != null) {
            running.lock();
            return;
        }
        Context app = context.getApplicationContext();
        if (!ServiceSwitch.switchOnToLock(app)) {
            Toast.makeText(app, R.string.lock_failed, Toast.LENGTH_LONG).show();
            return;
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (ServiceSwitch.cancelLockRequest(app)) Toast.makeText(app, R.string.lock_failed, Toast.LENGTH_LONG).show();
        }, ServiceSwitch.REQUEST_EXPIRES_MS);
    }

    @Override
    protected void onServiceConnected() {
        prefs = Prefs.get(this);
        overlay = new LockOverlay(this, prefs);
        searchGuard = new SearchGestureGuard(this);
        searchGuard.restore();  // a lock cut short by a crash or reboot left the gesture off
        running = this;
        if (ServiceSwitch.takeLockRequest(this)) lock();
        else if (ServiceSwitch.onDemand(this)) switchOff();  // back on after a crash, a reboot or from Settings
    }

    public boolean isLocked() {
        return overlay != null && overlay.isShowing();
    }

    public void lock() {
        if (isLocked()) return;
        if (prefs.getBoolean(Prefs.GUARD_SEARCH, true)) searchGuard.disable();
        volumePresses.reset();
        overlay.show(Prefs.slideUnlock(prefs), Prefs.doubleTapUnlock(prefs),
                prefs.getBoolean(Prefs.KEEP_SCREEN_ON, true), this::unlock);
        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE);
        Haptics.play(this, VibrationEffect.EFFECT_HEAVY_CLICK);
    }

    public void unlock() {
        if (!isLocked()) return;
        overlay.hide();
        searchGuard.restore();
        Haptics.play(this, VibrationEffect.EFFECT_DOUBLE_CLICK);
        if (ServiceSwitch.onDemand(this)) switchOff();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (!isLocked() || event.getKeyCode() != KeyEvent.KEYCODE_VOLUME_DOWN || !Prefs.volumeUnlock(prefs)) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (volumePresses.press(event.getEventTime())) unlock();
            else Haptics.play(this, VibrationEffect.EFFECT_TICK);
        }
        return true;  // while locked, volume down is reserved for unlocking
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        shutDown();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        shutDown();
        super.onDestroy();
    }

    /** Switches off between locks once the app can switch it back on itself, as right after the grant. */
    public void switchOffIfIdle() {
        if (!isLocked() && ServiceSwitch.onDemand(this)) switchOff();
    }

    /** Turns the service off until the next lock. */
    private void switchOff() {
        running = null;  // a lock asked for from now on switches the service back on afresh
        disableSelf();
    }

    private void shutDown() {
        if (running != this) return;
        running = null;
        overlay.hide();
        searchGuard.restore();
    }
}
