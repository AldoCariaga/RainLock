package com.aldo.rainlock.unlock;

/** Fires when enough presses land inside a window timed from the first one. Drives volume x3 and double-tap. */
public final class PressSequence {
    private final int required;
    private final long windowMs;
    private int count;
    private long firstAtMs;

    public PressSequence(int required, long windowMs) {
        this.required = required;
        this.windowMs = windowMs;
    }

    /** Returns true when this press completes the sequence; counting then starts over. */
    public boolean press(long nowMs) {
        if (count == 0 || nowMs - firstAtMs > windowMs) {
            count = 0;
            firstAtMs = nowMs;
        }
        if (++count < required) return false;
        count = 0;
        return true;
    }

    public void reset() {
        count = 0;
    }
}
