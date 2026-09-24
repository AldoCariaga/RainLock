package com.aldo.rainlock;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.StatusBarManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.TwoStatePreference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.aldo.rainlock.grant.ShizukuGrant;
import com.aldo.rainlock.guard.SearchGestureGuard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class SettingsFragment extends PreferenceFragmentCompat {
    private static final String CAT_SETUP = "cat_setup";
    private static final String STATUS_SERVICE = "status_service";
    private static final String STATUS_GUARD = "status_guard";
    private static final String LOCK_NOW = "lock_now";
    private static final String ADD_TILE = "add_tile";
    private static final String KNOB_RESET = "knob_reset";

    private final ActivityResultLauncher<String> bluetoothPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    this.<TwoStatePreference>find(Prefs.TRIGGER_BLUETOOTH).setChecked(true);
                    loadBluetoothDevices();
                } else {
                    toast(R.string.bluetooth_denied);
                }
            });

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        find(STATUS_SERVICE).setOnPreferenceClickListener(p -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return true;
        });
        find(STATUS_GUARD).setOnPreferenceClickListener(p -> {
            showGrantOptions();
            return true;
        });
        find(LOCK_NOW).setOnPreferenceClickListener(p -> {
            lockNow();
            return true;
        });

        keepOneUnlockMethod(Prefs.UNLOCK_VOLUME, Prefs.UNLOCK_SLIDE, Prefs.UNLOCK_DOUBLE_TAP);

        find(Prefs.TRIGGER_TILE).setOnPreferenceChangeListener((p, value) -> {
            setTileEnabled((Boolean) value);
            return true;
        });
        Preference addTile = find(ADD_TILE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addTile.setOnPreferenceClickListener(p -> {
                requestAddTile();
                return true;
            });
        } else {
            addTile.setVisible(false);
        }

        find(Prefs.TRIGGER_BLUETOOTH).setOnPreferenceChangeListener((p, value) -> {
            if (!(Boolean) value || hasBluetoothPermission()) return true;
            bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT);
            return false;  // switched on once the permission comes back granted
        });

        find(KNOB_RESET).setOnPreferenceClickListener(p -> {
            Prefs.get(requireContext()).edit().remove(Prefs.KNOB_RIGHT).remove(Prefs.KNOB_Y).apply();
            toast(R.string.knob_position_reset);
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshStatus();
        loadBluetoothDevices();
    }

    private void refreshStatus() {
        Context context = requireContext();
        RainLockService service = RainLockService.running();
        // The service normally restores Circle to Search; if it isn't running, do it here.
        if (service == null) new SearchGestureGuard(context).restore();

        boolean granted = SearchGestureGuard.hasPermission(context);
        // Setup only shows what's left to do; without the permission, turning the service on by hand also works
        find(CAT_SETUP).setVisible(!granted);
        find(STATUS_SERVICE).setVisible(service == null);
        find(LOCK_NOW).setEnabled(RainLockService.canLock(context));
        find(Prefs.GUARD_SEARCH).setSummary(granted ? R.string.guard_summary : R.string.guard_summary_no_permission);
    }

    private void lockNow() {
        RainLockService.requestLock(requireContext());
        requireActivity().moveTaskToBack(true);  // back to the app underneath, e.g. Maps
    }

    private void showGrantOptions() {
        Context context = requireContext();
        String command = "adb shell pm grant " + context.getPackageName() + " "
                + Manifest.permission.WRITE_SECURE_SETTINGS;
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.grant_title)
                .setMessage(getString(R.string.grant_message, command))
                .setPositiveButton(R.string.grant_shizuku, (dialog, which) -> grantWithShizuku())
                .setNeutralButton(R.string.copy_command, (dialog, which) -> context.getSystemService(ClipboardManager.class)
                        .setPrimaryClip(ClipData.newPlainText("adb", command)))
                .show();  // no Cancel: tapping outside closes it, and two buttons fit on one row
    }

    /** Takes the user through whichever Shizuku step is next: install it, start it, or grant. */
    private void grantWithShizuku() {
        Context context = requireContext();
        switch (ShizukuGrant.status(context)) {
            case NOT_INSTALLED:
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=" + ShizukuGrant.PACKAGE)));
                toast(R.string.shizuku_install);
                break;
            case NOT_RUNNING:
                Intent open = context.getPackageManager().getLaunchIntentForPackage(ShizukuGrant.PACKAGE);
                if (open != null) startActivity(open);
                toast(R.string.shizuku_start);
                break;
            case READY:
                toast(R.string.shizuku_granting);
                ShizukuGrant.grant(context, error -> {
                    if (!isAdded()) return;
                    if (error == null) {
                        toast(R.string.grant_done);
                        refreshStatus();
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.grant_failed, error), Toast.LENGTH_LONG).show();
                    }
                });
                break;
        }
    }

    /** Refuses to switch off the last unlock method. */
    private void keepOneUnlockMethod(String... keys) {
        for (String key : keys) {
            find(key).setOnPreferenceChangeListener((p, value) -> {
                if ((Boolean) value) return true;
                for (String other : keys) {
                    if (!other.equals(key) && this.<TwoStatePreference>find(other).isChecked()) return true;
                }
                toast(R.string.need_one_unlock);
                return false;
            });
        }
    }

    private void setTileEnabled(boolean enabled) {
        Context context = requireContext();
        context.getPackageManager().setComponentEnabledSetting(
                new ComponentName(context, LockTileService.class),
                enabled ? PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private void requestAddTile() {
        Context context = requireContext().getApplicationContext();
        context.getSystemService(StatusBarManager.class).requestAddTileService(
                new ComponentName(context, LockTileService.class),
                getString(R.string.tile_label),
                Icon.createWithResource(context, R.drawable.ic_tile),
                context.getMainExecutor(),
                result -> {
                    if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                        Toast.makeText(context, R.string.tile_already_added, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private boolean hasBluetoothPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")  // guarded by hasBluetoothPermission()
    private void loadBluetoothDevices() {
        BluetoothManager manager = requireContext().getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !hasBluetoothPermission()) return;
        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        List<BluetoothDevice> devices = bonded == null ? new ArrayList<>() : new ArrayList<>(bonded);
        devices.sort(Comparator.comparing(d -> String.valueOf(d.getName())));

        CharSequence[] names = new CharSequence[devices.size()];
        CharSequence[] addresses = new CharSequence[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            BluetoothDevice device = devices.get(i);
            names[i] = device.getName() != null ? device.getName() : device.getAddress();
            addresses[i] = device.getAddress();
        }
        ListPreference list = find(Prefs.BLUETOOTH_DEVICE);
        list.setEntries(names);
        list.setEntryValues(addresses);
    }

    private void toast(int message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private <T extends Preference> T find(String key) {
        return Objects.requireNonNull(findPreference(key), key);
    }
}
