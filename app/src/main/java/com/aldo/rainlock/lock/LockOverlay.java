package com.aldo.rainlock.lock;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;

/** Adds and removes the full-screen lock window. Must be created from the accessibility service. */
public final class LockOverlay {
    private final Context service;
    private final SharedPreferences prefs;
    private final WindowManager windowManager;
    private LockView view;

    public LockOverlay(Context service, SharedPreferences prefs) {
        this.service = service;
        this.prefs = prefs;
        this.windowManager = service.getSystemService(WindowManager.class);
    }

    public boolean isShowing() {
        return view != null;
    }

    public void show(boolean slide, boolean doubleTap, boolean keepScreenOn, Runnable onUnlock) {
        if (view != null) return;
        view = new LockView(service, prefs, slide, doubleTap, onUnlock);
        windowManager.addView(view, layoutParams(keepScreenOn));
    }

    public void hide() {
        if (view == null) return;
        windowManager.removeViewImmediate(view);
        view = null;
    }

    private static WindowManager.LayoutParams layoutParams(boolean keepScreenOn) {
        // Focusable on purpose: only the focused window may put the navigation bar into immersive mode.
        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (keepScreenOn) flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        // Accessibility overlays sit above the status and navigation bars; ordinary app overlays sit below them.
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.setFitInsetsTypes(0);
        lp.setTitle("RainLock");
        return lp;
    }
}
