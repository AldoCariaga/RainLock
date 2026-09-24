package com.aldo.rainlock;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.VibrationEffect;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;

import com.aldo.rainlock.guard.SearchGestureGuard;
import com.aldo.rainlock.lock.FloatingButton;
import com.aldo.rainlock.lock.LockOverlay;
import com.aldo.rainlock.unlock.PressSequence;

/** Owns the lock. An accessibility service: only its overlays cover the system bars, and only it sees volume keys. */
public final class RainLockService extends AccessibilityService
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final long VOLUME_WINDOW_MS = 2000;

    @Nullable
    private static RainLockService running;  // touched on the main thread only

    private final PressSequence volumePresses = new PressSequence(3, VOLUME_WINDOW_MS);
    private SharedPreferences prefs;
    private LockOverlay overlay;
    private FloatingButton floatingButton;
    private SearchGestureGuard searchGuard;
    private boolean bluetoothListening;

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice device =
                    IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
            String chosen = prefs.getString(Prefs.BLUETOOTH_DEVICE, null);
            if (device == null || chosen == null || !chosen.equals(device.getAddress())) return;
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) lock();
            else if (prefs.getBoolean(Prefs.BLUETOOTH_AUTO_UNLOCK, true)) unlock();
        }
    };

    @Nullable
    public static RainLockService running() {
        return running;
    }

    @Override
    protected void onServiceConnected() {
        prefs = Prefs.get(this);
        overlay = new LockOverlay(this, prefs);
        floatingButton = new FloatingButton(this, prefs, this::lock);
        searchGuard = new SearchGestureGuard(this);
        searchGuard.restore();  // a lock cut short by a crash or reboot left the gesture off
        prefs.registerOnSharedPreferenceChangeListener(this);
        running = this;
        applyTriggers();
    }

    public boolean isLocked() {
        return overlay != null && overlay.isShowing();
    }

    public void lock() {
        if (isLocked()) return;
        if (prefs.getBoolean(Prefs.GUARD_SEARCH, true)) searchGuard.disable();
        floatingButton.hide();
        volumePresses.reset();
        overlay.show(Prefs.slideUnlock(prefs), Prefs.doubleTapUnlock(prefs),
                prefs.getBoolean(Prefs.KEEP_SCREEN_ON, true), this::unlock);
        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE);
        Haptics.play(this, VibrationEffect.EFFECT_HEAVY_CLICK);
    }

    public void unlock() {
        if (!isLocked()) return;
        overlay.hide();
        searchGuard.restore();
        applyTriggers();
        Haptics.play(this, VibrationEffect.EFFECT_DOUBLE_CLICK);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (!isLocked() || event.getKeyCode() != KeyEvent.KEYCODE_VOLUME_DOWN || !Prefs.volumeUnlock(prefs)) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (volumePresses.press(event.getEventTime())) unlock();
            else Haptics.play(this, VibrationEffect.EFFECT_TICK);
        }
        return true;  // while locked, volume down is reserved for unlocking
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        applyTriggers();
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

    private void shutDown() {
        if (running != this) return;
        running = null;
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        overlay.hide();
        floatingButton.hide();
        searchGuard.restore();
        setBluetoothListening(false);
    }

    /** Shows or hides each lock trigger to match the settings. */
    private void applyTriggers() {
        if (prefs.getBoolean(Prefs.TRIGGER_FLOATING, false) && !isLocked()) floatingButton.show();
        else floatingButton.hide();
        setBluetoothListening(prefs.getBoolean(Prefs.TRIGGER_BLUETOOTH, false)
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED);
    }

    private void setBluetoothListening(boolean listen) {
        if (listen == bluetoothListening) return;
        if (listen) {
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
            filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            ContextCompat.registerReceiver(this, bluetoothReceiver, filter, ContextCompat.RECEIVER_EXPORTED);
        } else {
            unregisterReceiver(bluetoothReceiver);
        }
        bluetoothListening = listen;
    }
}
