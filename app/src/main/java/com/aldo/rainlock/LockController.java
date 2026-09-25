package com.aldo.rainlock;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;

/** Locks and unlocks in whichever Mode is set up. Every trigger comes through here, on the main thread. */
public final class LockController {
    @SuppressLint("StaticFieldLeak")  // built on the application context, which lives as long as the app
    @Nullable
    private static LockSession basic;  // Basic Mode's lock; the other modes' lives in the service

    private LockController() {
    }

    public static boolean canLock(Context context) {
        return Mode.current(context) != Mode.NONE;
    }

    public static boolean isLocked() {
        RainLockService service = RainLockService.running();
        return (service != null && service.isLocked()) || (basic != null && basic.isLocked());
    }

    public static void lock(Context context) {
        switch (Mode.current(context)) {
            case BEST:
            case BETTER:
                RainLockService.requestLock(context);
                break;
            case BASIC:
                basicSession(context).lock();
                break;
            case NONE:
                Toast.makeText(context.getApplicationContext(), R.string.lock_not_set_up, Toast.LENGTH_LONG).show();
                break;
        }
    }

    public static void unlock() {
        RainLockService service = RainLockService.running();
        if (service != null) service.unlock();
        if (basic != null) basic.unlock();
    }

    private static LockSession basicSession(Context context) {
        if (basic == null) {
            Context app = context.getApplicationContext();
            Display display = app.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
            int type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            basic = new LockSession(app.createWindowContext(display, type, null), type, () -> { });
        }
        return basic;
    }
}
