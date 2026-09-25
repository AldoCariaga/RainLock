package com.aldo.rainlock;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.core.content.IntentCompat;

/** Locks when the chosen Bluetooth device connects. Declared in the manifest, so it works with the service off. */
public final class BluetoothTrigger extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = Prefs.get(context);
        if (!prefs.getBoolean(Prefs.TRIGGER_BLUETOOTH, false)) return;
        BluetoothDevice device =
                IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        String chosen = prefs.getString(Prefs.BLUETOOTH_DEVICE, null);
        if (device == null || chosen == null || !chosen.equals(device.getAddress())) return;
        // Both are protected broadcasts, so checking the action keeps other apps from faking them
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) {
            LockController.lock(context);
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())
                && prefs.getBoolean(Prefs.BLUETOOTH_AUTO_UNLOCK, true)) {
            LockController.unlock();
        }
    }
}
