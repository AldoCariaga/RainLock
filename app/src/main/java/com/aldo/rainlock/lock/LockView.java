package com.aldo.rainlock.lock;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import com.aldo.rainlock.Prefs;
import com.aldo.rainlock.R;
import com.aldo.rainlock.unlock.PressSequence;

import java.util.List;
import java.util.function.Predicate;

/** Full-screen touch sink with the lock knob. Only the knob reacts: unlock gestures, or hold-then-drag to move it. */
@SuppressLint("ViewConstructor")
final class LockView extends ViewGroup {
    private static final float TRACK_WIDTH = 0.75f;  // slide track, as a fraction of screen width
    private static final float SLIDE_DONE = 0.9f;    // share of the track a slide must cover
    private static final long TAP_MAX_MS = 300;
    private static final long DOUBLE_TAP_GAP_MS = 400;
    private static final long RING_DELAY_MS = 150;   // quick taps and slides never flash the ring
    private static final long HOLD_TO_MOVE_MS = 900;

    private final SharedPreferences prefs;
    private final boolean slide;
    private final boolean doubleTap;
    private final boolean hideStatusBar;
    private final Runnable onUnlock;
    private final Predicate<KeyEvent> onKey;
    private final KnobView knob;
    private final TextView track;
    private final GradientDrawable trackBackground = new GradientDrawable();
    private final ValueAnimator ringFill = ValueAnimator.ofFloat(0, 1);
    private final int knobHeight;
    private final int dockWidth;
    private final int topMargin;
    private final int bottomReserve;
    private final int hitSlop;
    private final int trackInnerPad;
    private final int trackOuterPad;
    private final int touchSlop;
    private final PressSequence taps = new PressSequence(2, DOUBLE_TAP_GAP_MS);
    private final Runnable startMoving = this::startMoving;

    private boolean onRight;
    private float knobY;  // knob centre, as a fraction of screen height
    private int pointerId = MotionEvent.INVALID_POINTER_ID;
    private float downX;
    private float downY;
    private long downAt;
    private boolean dragging;
    private boolean moving;

    LockView(Context context, SharedPreferences prefs, boolean slide, boolean doubleTap, boolean hideStatusBar,
            Runnable onUnlock, Predicate<KeyEvent> onKey) {
        super(context);
        this.prefs = prefs;
        this.slide = slide;
        this.doubleTap = doubleTap;
        this.hideStatusBar = hideStatusBar;
        this.onUnlock = onUnlock;
        this.onKey = onKey;
        onRight = prefs.getBoolean(Prefs.KNOB_RIGHT, false);
        knobY = prefs.getFloat(Prefs.KNOB_Y, 0.55f);
        float density = getResources().getDisplayMetrics().density;
        knobHeight = Math.round(48 * density);
        dockWidth = Math.round(40 * density);  // docks flush: the back swipe is excluded while locked
        topMargin = Math.round(8 * density);
        bottomReserve = Math.round(64 * density);  // stays clear of the home-swipe strip
        hitSlop = Math.round(8 * density);  // the knob takes touches a little outside its shape, for gloves
        trackInnerPad = dockWidth + Math.round(12 * density);
        trackOuterPad = Math.round(16 * density);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        trackBackground.setColor(0x99000000);
        track = new TextView(context);
        track.setBackground(trackBackground);
        track.setTextColor(0xFFFFFFFF);
        track.setTextSize(15);
        track.setVisibility(INVISIBLE);
        addView(track);

        knob = new KnobView(context);
        addView(knob);

        ringFill.setStartDelay(RING_DELAY_MS);
        ringFill.setDuration(HOLD_TO_MOVE_MS - RING_DELAY_MS);
        ringFill.setInterpolator(new LinearInterpolator());
        ringFill.addUpdateListener(a -> knob.setProgress((float) a.getAnimatedValue()));
        applySide();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Immersive mode: a bottom swipe then only reveals the bar instead of going home (README.md).
        WindowInsetsController bars = getWindowInsetsController();
        if (bars != null) {
            bars.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            bars.hide(hideStatusBar ? WindowInsets.Type.systemBars() : WindowInsets.Type.navigationBars());
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(startMoving);
        ringFill.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        setSystemGestureExclusionRects(List.of(new Rect(0, 0, w, h)));  // no back swipe from either edge
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(width, MeasureSpec.getSize(heightSpec));
        int heightSpecExact = MeasureSpec.makeMeasureSpec(knobHeight, MeasureSpec.EXACTLY);
        int knobWidth = moving ? knobHeight : dockWidth;
        int trackWidth = Math.max(dockWidth * 4, Math.round(width * TRACK_WIDTH));
        knob.measure(MeasureSpec.makeMeasureSpec(knobWidth, MeasureSpec.EXACTLY), heightSpecExact);
        track.measure(MeasureSpec.makeMeasureSpec(trackWidth, MeasureSpec.EXACTLY), heightSpecExact);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int top = clampTop(Math.round((b - t) * knobY) - knobHeight / 2, b - t);
        int dockLeft = onRight ? width - dockWidth : 0;
        // While moving, the knob is a full disc centred where the half-disc was.
        int knobLeft = moving
                ? Math.max(0, Math.min(dockLeft + dockWidth / 2 - knobHeight / 2, width - knobHeight))
                : dockLeft;
        int trackLeft = onRight ? width - track.getMeasuredWidth() : 0;
        knob.layout(knobLeft, top, knobLeft + knob.getMeasuredWidth(), top + knobHeight);
        track.layout(trackLeft, top, trackLeft + track.getMeasuredWidth(), top + knobHeight);
    }

    /** Basic Mode's volume unlock. With the service, its key filter gets volume down first. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return onKey.test(event) || super.dispatchKeyEvent(event);
    }

    @SuppressLint("ClickableViewAccessibility")  // a touch sink has no click to perform
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int index = e.getActionIndex();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                // The newest finger on the knob wins, so a raindrop resting on it can't lock the rider out.
                if (!moving && onKnob(e.getX(index), e.getY(index))) press(e, index);
                break;
            case MotionEvent.ACTION_MOVE: {
                int i = e.findPointerIndex(pointerId);
                if (i >= 0) follow(e.getX(i) - downX, e.getY(i) - downY);
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if (e.getPointerId(index) == pointerId) release(e.getEventTime());
                break;
            case MotionEvent.ACTION_CANCEL:
                if (pointerId != MotionEvent.INVALID_POINTER_ID) endPress();
                break;
            default:
                break;
        }
        return true;
    }

    private boolean onKnob(float x, float y) {
        return x >= knob.getLeft() - hitSlop && x <= knob.getRight() + hitSlop
                && y >= knob.getTop() - hitSlop && y <= knob.getBottom() + hitSlop;
    }

    private void press(MotionEvent e, int index) {
        stopHold();
        knob.animate().cancel();
        knob.setTranslationX(0);
        pointerId = e.getPointerId(index);
        downX = e.getX(index);
        downY = e.getY(index);
        downAt = e.getEventTime();
        dragging = false;
        track.setVisibility(VISIBLE);
        postDelayed(startMoving, HOLD_TO_MOVE_MS);
        ringFill.start();
    }

    private void follow(float dx, float dy) {
        if (moving) {
            knob.setTranslationX(dx);
            knob.setTranslationY(dy);
            return;
        }
        if (!dragging && Math.hypot(dx, dy) > touchSlop) {
            dragging = true;
            stopHold();  // a finger that moves early is a slide, never a hold
        }
        if (dragging && slide) {
            int direction = onRight ? -1 : 1;
            knob.setTranslationX(direction * Math.max(0, Math.min(direction * dx, maxTravel())));
        }
    }

    private void release(long at) {
        if (moving) {
            dropAt(knob.getLeft() + knob.getWidth() / 2f + knob.getTranslationX(),
                    knob.getTop() + knob.getHeight() / 2f + knob.getTranslationY());
            endPress();
            return;
        }
        boolean slid = slide && dragging && Math.abs(knob.getTranslationX()) >= maxTravel() * SLIDE_DONE;
        boolean tapped = !dragging && at - downAt <= TAP_MAX_MS;
        endPress();
        if (slid || (doubleTap && tapped && taps.press(at))) onUnlock.run();
    }

    private void startMoving() {
        if (pointerId == MotionEvent.INVALID_POINTER_ID || dragging) return;
        moving = true;
        stopHold();
        track.setVisibility(INVISIBLE);
        knob.setDock(KnobView.FLOATING);
        knob.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).start();
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        requestLayout();
    }

    /** Snaps the knob to the nearer side at the drop height, and remembers the spot. */
    private void dropAt(float centerX, float centerY) {
        onRight = centerX > getWidth() / 2f;
        int top = clampTop(Math.round(centerY) - knobHeight / 2, getHeight());
        knobY = (top + knobHeight / 2f) / getHeight();
        prefs.edit().putBoolean(Prefs.KNOB_RIGHT, onRight).putFloat(Prefs.KNOB_Y, knobY).apply();
        applySide();
    }

    private void endPress() {
        stopHold();
        pointerId = MotionEvent.INVALID_POINTER_ID;
        dragging = false;
        track.setVisibility(INVISIBLE);
        if (moving) {
            moving = false;
            knob.animate().cancel();
            knob.setTranslationX(0);
            knob.setTranslationY(0);
            knob.setScaleX(1);
            knob.setScaleY(1);
            knob.setDock(onRight ? KnobView.DOCK_RIGHT : KnobView.DOCK_LEFT);
            requestLayout();
        } else {
            knob.animate().translationX(0).setDuration(150).start();
        }
    }

    private void stopHold() {
        removeCallbacks(startMoving);
        ringFill.cancel();
        knob.setProgress(0);
    }

    /** Turns the knob, the track and the slide hint to face away from whichever edge the knob docks on. */
    private void applySide() {
        int hint = slide ? (onRight ? R.string.hint_slide_left : R.string.hint_slide)
                : doubleTap ? R.string.hint_double_tap
                : R.string.hint_volume;
        track.setText(hint);
        knob.setContentDescription(getContext().getText(hint));
        knob.setDock(onRight ? KnobView.DOCK_RIGHT : KnobView.DOCK_LEFT);
        float r = knobHeight / 2f;
        trackBackground.setCornerRadii(onRight
                ? new float[] {r, r, 0, 0, 0, 0, r, r}
                : new float[] {0, 0, r, r, r, r, 0, 0});
        track.setGravity(Gravity.CENTER_VERTICAL | (onRight ? Gravity.END : Gravity.START));
        track.setPadding(onRight ? trackOuterPad : trackInnerPad, 0, onRight ? trackInnerPad : trackOuterPad, 0);
    }

    private int clampTop(int top, int height) {
        return Math.max(topMargin, Math.min(top, height - bottomReserve - knobHeight));
    }

    private float maxTravel() {
        return track.getWidth() - dockWidth;
    }
}
