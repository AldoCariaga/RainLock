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
import com.aldo.rainlock.guard.DeviceProfile;
import com.aldo.rainlock.guard.SearchGestureGuard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class SettingsFragment extends PreferenceFragmentCompat {
    private static final String CAT_MODE = "cat_mode";
    private static final String MODE_BASIC = "mode_basic";
    private static final String MODE_BETTER = "mode_better";
    private static final String MODE_BEST = "mode_best";
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

        onModeTap(MODE_BASIC, Mode.BASIC, () -> showSteps(R.string.mode_basic,
                search(R.string.basic_message, R.string.basic_message_no_search), R.string.open_overlay_settings,
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION));  // opens the list: Android 11+ ignores a package here
        onModeTap(MODE_BETTER, Mode.BETTER, () -> showSteps(R.string.mode_better,
                search(R.string.service_message, R.string.service_message_no_search), R.string.open_accessibility,
                Settings.ACTION_ACCESSIBILITY_SETTINGS));
        onModeTap(MODE_BEST, Mode.BEST, this::showGrantOptions);
        find(LOCK_NOW).setOnPreferenceClickListener(p -> {
            lockNow();
            return true;
        });

        keepOneUnlockMethod(Prefs.UNLOCK_VOLUME, Prefs.UNLOCK_SLIDE, Prefs.UNLOCK_DOUBLE_TAP);

        find(Prefs.TRIGGER_TILE).setOnPreferenceChangeListener((p, value) -> {
            setTileEnabled((Boolean) value);
            return true;
        });
        find(Prefs.GUARD_SEARCH).setVisible(DeviceProfile.hasCircleToSearch());

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
        if (granted && service != null) service.switchOffIfIdle();  // on by hand until an upgrade
        Mode mode = Mode.current(context);
        find(CAT_MODE).setTitle(mode == Mode.NONE ? R.string.cat_mode_pick : R.string.cat_mode);
        showModeRow(MODE_BASIC, Mode.BASIC, R.string.mode_basic,
                search(R.string.mode_basic_summary, R.string.mode_basic_summary_no_search), mode);
        showModeRow(MODE_BETTER, Mode.BETTER, R.string.mode_better,
                search(R.string.mode_better_summary, R.string.mode_better_summary_no_search), mode);
        showModeRow(MODE_BEST, Mode.BEST, R.string.mode_best,
                search(R.string.mode_best_summary, R.string.mode_best_summary_no_search), mode);
        // With the adb permission the app switches the service off between locks, so it can't stay on by hand
        find(MODE_BETTER).setVisible(!granted);
        find(LOCK_NOW).setEnabled(mode != Mode.NONE);

        // The guard writes whenever it can, in any mode
        TwoStatePreference guard = find(Prefs.GUARD_SEARCH);
        guard.setSummaryOn(granted ? R.string.guard_summary_on : R.string.guard_summary_no_permission);
        guard.setSummaryOff(granted ? R.string.guard_summary_off : R.string.guard_summary_no_permission);
    }

    /** A tap on a mode that's set up switches to it; otherwise it shows the steps, and the mode is used once ready. */
    private void onModeTap(String key, Mode row, Runnable showSteps) {
        find(key).setOnPreferenceClickListener(p -> {
            Context context = requireContext();
            boolean switching = row.isSetUp(context) && Mode.current(context) != row;
            Mode.pick(context, row);
            if (switching) refreshStatus();
            else showSteps.run();
            return true;
        });
    }

    private void showModeRow(String key, Mode row, int title, int summary, Mode current) {
        Preference preference = find(key);
        preference.setTitle(row == current ? getString(R.string.mode_in_use, getString(title)) : getString(title));
        boolean offer = row != current && row.isSetUp(requireContext());
        preference.setSummary(offer ? getString(R.string.mode_tap_to_use, getString(summary)) : getString(summary));
    }

    private void lockNow() {
        LockController.lock(requireContext());
        requireActivity().moveTaskToBack(true);  // back to the app underneath, e.g. Maps
    }

    /** Basic and Better Mode's steps: a settings screen to open, plus App info for a restricted setting. */
    private void showSteps(int title, int message, int openLabel, String openAction) {
        Context context = requireContext();
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(openLabel, (dialog, which) -> startActivity(new Intent(openAction)))
                .setNeutralButton(R.string.app_info, (dialog, which) ->  // where Allow restricted settings lives
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.getPackageName(), null))))
                .show();
    }

    private void showGrantOptions() {
        Context context = requireContext();
        String command = "adb shell pm grant " + context.getPackageName() + " "
                + Manifest.permission.WRITE_SECURE_SETTINGS;
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.mode_best)
                .setMessage(getString(search(R.string.grant_message, R.string.grant_message_no_search), command))
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

    /** Picks the text that mentions Circle to Search only on phones that have it. */
    private static int search(int withSearch, int withoutSearch) {
        return DeviceProfile.hasCircleToSearch() ? withSearch : withoutSearch;
    }

    private void toast(int message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private <T extends Preference> T find(String key) {
        return Objects.requireNonNull(findPreference(key), key);
    }
}
