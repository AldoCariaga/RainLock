package com.aldo.rainlock;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.aldo.rainlock.guard.SearchGestureGuard;

/**
 * Hosts the lock in Better and Best Mode: only its overlays cover the system bars, and only it sees volume keys
 * before the focused window. In Best Mode it's on only while locked, so banking apps don't see it.
 */
public final class RainLockService extends AccessibilityService {
    @Nullable
    private static RainLockService running;  // touched on the main thread only

    private LockSession session;

    @Nullable
    public static RainLockService running() {
        return running;
    }

    /** Locks now when the service is on. Otherwise switches it on, and it locks once connected. */
    static void requestLock(Context context) {
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
        session = new LockSession(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, () -> {
            if (ServiceSwitch.onDemand(this)) switchOff();
        });
        new SearchGestureGuard(this).restore();  // a lock cut short by a crash or reboot left the gesture off
        running = this;
        if (ServiceSwitch.takeLockRequest(this)) lock();
        else if (ServiceSwitch.onDemand(this)) switchOff();  // back on after a crash, a reboot or from Settings
    }

    public boolean isLocked() {
        return session != null && session.isLocked();
    }

    public void lock() {
        if (isLocked()) return;
        session.lock();
        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE);
    }

    public void unlock() {
        if (session != null) session.unlock();
    }

    /** Switches off between locks once the app can switch it back on itself, as right after the grant. */
    public void switchOffIfIdle() {
        if (!isLocked() && ServiceSwitch.onDemand(this)) switchOff();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        return session != null && session.onKey(event);
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

    /** Turns the service off until the next lock. */
    private void switchOff() {
        running = null;  // a lock asked for from now on switches the service back on afresh
        disableSelf();
    }

    private void shutDown() {
        if (running != this) return;
        running = null;
        session.close();
    }
}
