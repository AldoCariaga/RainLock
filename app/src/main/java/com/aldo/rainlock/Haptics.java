package com.aldo.rainlock;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/** Short buzzes so the lock state can be felt through gloves. */
final class Haptics {
    private Haptics() {
    }

    static void play(Context context, int predefinedEffect) {
        VibratorManager manager = context.getSystemService(VibratorManager.class);
        Vibrator vibrator = manager == null ? null : manager.getDefaultVibrator();
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createPredefined(predefinedEffect));
        }
    }
}
