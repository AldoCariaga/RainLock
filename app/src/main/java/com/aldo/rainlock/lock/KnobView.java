package com.aldo.rainlock.lock;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.aldo.rainlock.R;

/** The lock knob: a half-disc docked on a screen edge, a full disc while being moved, plus the hold-to-move ring. */
@SuppressLint("ViewConstructor")
final class KnobView extends View {
    static final int DOCK_LEFT = 0;
    static final int DOCK_RIGHT = 1;
    static final int FLOATING = 2;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable icon;
    private final int iconSize;
    private final float ringRadius;
    private final Path shape = new Path();
    private final RectF bounds = new RectF();
    private final RectF ringBounds = new RectF();
    private final float[] radii = new float[8];
    private int dock = DOCK_LEFT;
    private float progress;

    KnobView(Context context) {
        super(context);
        float density = getResources().getDisplayMetrics().density;
        fill.setColor(0xB3000000);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(density);
        outline.setColor(0x80FFFFFF);
        ringTrack.setStyle(Paint.Style.STROKE);
        ringTrack.setStrokeWidth(2.5f * density);
        ringTrack.setColor(0x40FFFFFF);
        ring.set(ringTrack);
        ring.setColor(0xFFFFFFFF);
        ring.setStrokeCap(Paint.Cap.ROUND);
        icon = context.getDrawable(R.drawable.ic_lock);
        iconSize = Math.round(20 * density);
        ringRadius = 15 * density;
    }

    void setDock(int dock) {
        if (this.dock == dock) return;
        this.dock = dock;
        rebuildShape();
        invalidate();
    }

    /** Hold-to-move progress from 0 to 1; 0 hides the ring. */
    void setProgress(float progress) {
        this.progress = progress;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildShape();
    }

    private void rebuildShape() {
        float inset = outline.getStrokeWidth() / 2;
        bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
        shape.reset();
        if (dock == FLOATING) {
            shape.addOval(bounds, Path.Direction.CW);
            return;
        }
        // Corner radii run top-left, top-right, bottom-right, bottom-left; the edge side stays flat.
        float r = bounds.height() / 2;
        boolean roundRight = dock == DOCK_LEFT;
        for (int corner = 0; corner < 4; corner++) {
            boolean rightCorner = corner == 1 || corner == 2;
            float radius = rightCorner == roundRight ? r : 0;
            radii[corner * 2] = radius;
            radii[corner * 2 + 1] = radius;
        }
        shape.addRoundRect(bounds, radii, Path.Direction.CW);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawPath(shape, fill);
        canvas.drawPath(shape, outline);

        int cx = getWidth() / 2;
        int cy = getHeight() / 2;
        icon.setBounds(cx - iconSize / 2, cy - iconSize / 2, cx + iconSize / 2, cy + iconSize / 2);
        icon.draw(canvas);

        if (progress > 0) {
            ringBounds.set(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius);
            canvas.drawOval(ringBounds, ringTrack);
            canvas.drawArc(ringBounds, -90, 360 * progress, false, ring);
        }
    }
}
