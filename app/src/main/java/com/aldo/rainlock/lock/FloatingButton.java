package com.aldo.rainlock.lock;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.ImageView;

import com.aldo.rainlock.Prefs;
import com.aldo.rainlock.R;

/** Draggable bubble that locks on tap and remembers where it was dropped. */
public final class FloatingButton {
    private final Context service;
    private final WindowManager windowManager;
    private final SharedPreferences prefs;
    private final Runnable onTap;
    private ImageView view;

    public FloatingButton(Context service, SharedPreferences prefs, Runnable onTap) {
        this.service = service;
        this.windowManager = service.getSystemService(WindowManager.class);
        this.prefs = prefs;
        this.onTap = onTap;
    }

    public void show() {
        if (view != null) return;
        float density = service.getResources().getDisplayMetrics().density;
        int size = Math.round(52 * density);
        int pad = Math.round(13 * density);
        Rect screen = windowManager.getCurrentWindowMetrics().getBounds();
        int maxX = screen.width() - size;
        int maxY = screen.height() - size;

        view = new ImageView(service);
        view.setBackgroundResource(R.drawable.bg_knob);
        view.setImageResource(R.drawable.ic_lock_open);
        view.setPadding(pad, pad, pad, pad);
        view.setAlpha(0.85f);
        view.setContentDescription(service.getString(R.string.floating_description));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = clamp(prefs.getInt(Prefs.FLOATING_X, maxX - Math.round(8 * density)), maxX);
        lp.y = clamp(prefs.getInt(Prefs.FLOATING_Y, Math.round(screen.height() * 0.35f)), maxY);
        view.setOnTouchListener(new DragToMove(lp, maxX, maxY));
        windowManager.addView(view, lp);
    }

    public void hide() {
        if (view == null) return;
        windowManager.removeViewImmediate(view);
        view = null;
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(value, max));
    }

    /** A drag moves the bubble; a touch that never passes the slop is a tap. */
    private final class DragToMove implements View.OnTouchListener {
        private final WindowManager.LayoutParams lp;
        private final int maxX;
        private final int maxY;
        private final int slop = ViewConfiguration.get(service).getScaledTouchSlop();
        private float downRawX;
        private float downRawY;
        private int startX;
        private int startY;
        private boolean moved;

        DragToMove(WindowManager.LayoutParams lp, int maxX, int maxY) {
            this.lp = lp;
            this.maxX = maxX;
            this.maxY = maxY;
        }

        @SuppressLint("ClickableViewAccessibility")  // the tap is handled here, on ACTION_UP
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = e.getRawX();
                    downRawY = e.getRawY();
                    startX = lp.x;
                    startY = lp.y;
                    moved = false;
                    break;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - downRawX;
                    float dy = e.getRawY() - downRawY;
                    if (!moved && Math.hypot(dx, dy) > slop) moved = true;
                    if (moved) {
                        lp.x = clamp(startX + Math.round(dx), maxX);
                        lp.y = clamp(startY + Math.round(dy), maxY);
                        windowManager.updateViewLayout(v, lp);
                    }
                    break;
                }
                case MotionEvent.ACTION_UP:
                    if (moved) prefs.edit().putInt(Prefs.FLOATING_X, lp.x).putInt(Prefs.FLOATING_Y, lp.y).apply();
                    else onTap.run();
                    break;
                default:
                    break;
            }
            return true;
        }
    }
}
