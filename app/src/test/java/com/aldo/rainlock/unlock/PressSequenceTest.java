package com.aldo.rainlock.unlock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PressSequenceTest {
    @Test
    public void firesOnTheThirdPressInsideTheWindow() {
        PressSequence presses = new PressSequence(3, 2000);
        assertFalse(presses.press(0));
        assertFalse(presses.press(500));
        assertTrue(presses.press(1900));
    }

    @Test
    public void aPressAfterTheWindowStartsANewRun() {
        PressSequence presses = new PressSequence(3, 2000);
        assertFalse(presses.press(0));
        assertFalse(presses.press(1500));
        assertFalse(presses.press(2500));  // 2.5 s after the first: counts as press 1 of a new run
        assertFalse(presses.press(3000));
        assertTrue(presses.press(4000));
    }

    @Test
    public void countingStartsOverAfterFiring() {
        PressSequence taps = new PressSequence(2, 400);
        assertFalse(taps.press(0));
        assertTrue(taps.press(300));
        assertFalse(taps.press(350));
        assertTrue(taps.press(600));
    }

    @Test
    public void doubleTapNeedsBothTapsInsideTheGap() {
        PressSequence taps = new PressSequence(2, 400);
        assertFalse(taps.press(0));
        assertFalse(taps.press(500));
        assertTrue(taps.press(800));
    }

    @Test
    public void resetDropsAPartialRun() {
        PressSequence presses = new PressSequence(3, 2000);
        presses.press(0);
        presses.press(100);
        presses.reset();
        assertFalse(presses.press(200));
        assertFalse(presses.press(300));
        assertTrue(presses.press(400));
    }
}
