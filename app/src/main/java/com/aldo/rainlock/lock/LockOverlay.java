package com.aldo.rainlock.lock;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.util.function.Predicate;

/** Adds and removes the full-screen lock window, from a context that may add windows of its type. */
public final class LockOverlay {
    private final Context context;
    private final SharedPreferences prefs;
    private final WindowManager windowManager;
    private final int type;
    private LockView view;

    /** {@code type} is TYPE_ACCESSIBILITY_OVERLAY from the service, or TYPE_APPLICATION_OVERLAY in Basic Mode. */
    public LockOverlay(Context context, SharedPreferences prefs, int type) {
        this.context = context;
        this.prefs = prefs;
        this.windowManager = context.getSystemService(WindowManager.class);
        this.type = type;
    }

    public boolean isShowing() {
        return view != null;
    }

    public void show(boolean slide, boolean doubleTap, boolean keepScreenOn, Runnable onUnlock,
            Predicate<KeyEvent> onKey) {
        if (view != null) return;
        // Only accessibility overlays sit above the status bar; an ordinary one hides it so rain can't pull it down.
        boolean hideStatusBar = type != WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        view = new LockView(context, prefs, slide, doubleTap, hideStatusBar, onUnlock, onKey);
        windowManager.addView(view, layoutParams(type, keepScreenOn));
    }

    public void hide() {
        if (view == null) return;
        windowManager.removeViewImmediate(view);
        view = null;
    }

    private static WindowManager.LayoutParams layoutParams(int type, boolean keepScreenOn) {
        // Focusable on purpose: only the focused window may hide the system bars, and it gets the volume keys.
        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (keepScreenOn) flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        // Accessibility overlays sit above the status and navigation bars; ordinary app overlays sit below them.
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                type, flags, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.setFitInsetsTypes(0);
        lp.setTitle("RainLock");
        return lp;
    }
}
