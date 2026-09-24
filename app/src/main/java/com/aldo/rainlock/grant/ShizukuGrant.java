package com.aldo.rainlock.grant;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import androidx.annotation.Nullable;

import com.aldo.rainlock.R;

import rikka.shizuku.Shizuku;

/** Grants the one-time permission through Shizuku, which starts from Wireless debugging instead of a computer. */
public final class ShizukuGrant {
    public static final String PACKAGE = "moe.shizuku.privileged.api";
    private static final int REQUEST_CODE = 1;

    public enum Status { NOT_INSTALLED, NOT_RUNNING, READY }

    public interface Callback {
        /** {@code error} is null once the permission is granted. */
        void onResult(@Nullable String error);
    }

    private ShizukuGrant() {
    }

    public static Status status(Context context) {
        if (Shizuku.pingBinder() && !Shizuku.isPreV11()) return Status.READY;
        try {
            context.getPackageManager().getPackageInfo(PACKAGE, 0);
            return Status.NOT_RUNNING;
        } catch (PackageManager.NameNotFoundException e) {
            return Status.NOT_INSTALLED;
        }
    }

    /** Asks Shizuku for access if needed, then grants the permission. Calls back on the main thread. */
    public static void grant(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            runGrant(app, main, callback);
            return;
        }
        if (Shizuku.shouldShowRequestPermissionRationale()) {  // denied before; Shizuku won't ask again
            callback.onResult(app.getString(R.string.shizuku_denied));
            return;
        }
        Shizuku.addRequestPermissionResultListener(new Shizuku.OnRequestPermissionResultListener() {
            @Override
            public void onRequestPermissionResult(int requestCode, int grantResult) {
                if (requestCode != REQUEST_CODE) return;
                Shizuku.removeRequestPermissionResultListener(this);
                if (grantResult == PackageManager.PERMISSION_GRANTED) runGrant(app, main, callback);
                else callback.onResult(app.getString(R.string.shizuku_denied));
            }
        }, main);
        Shizuku.requestPermission(REQUEST_CODE);
    }

    private static void runGrant(Context app, Handler main, Callback callback) {
        Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(new ComponentName(app, GrantService.class))
                .processNameSuffix("grant")
                .daemon(false);
        Shizuku.bindUserService(args, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                IGrantService service = IGrantService.Stub.asInterface(binder);
                ServiceConnection connection = this;
                new Thread(() -> {  // pm grant takes a moment; keep it off the main thread
                    String error;
                    try {
                        error = service.grant(app.getPackageName(), Manifest.permission.WRITE_SECURE_SETTINGS);
                    } catch (RemoteException e) {
                        error = e.toString();
                    }
                    String result = error;
                    main.post(() -> {
                        Shizuku.unbindUserService(args, connection, true);  // ends the shell process
                        callback.onResult(result);
                    });
                }).start();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        });
    }
}
