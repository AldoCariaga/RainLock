package com.aldo.rainlock;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile: tap to lock. With the service off, the tap opens setup instead. */
public final class LockTileService extends TileService {
    @Override
    public void onStartListening() {
        Tile tile = getQsTile();
        if (tile == null) return;
        RainLockService service = RainLockService.running();
        tile.setState(service != null && service.isLocked() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setSubtitle(getString(service == null ? R.string.tile_needs_setup : R.string.tile_ready));
        tile.updateTile();
    }

    @SuppressWarnings("deprecation")  // the Intent overload is the only one before Android 14
    @Override
    public void onClick() {
        RainLockService service = RainLockService.running();
        if (service != null) {
            service.lock();
            return;
        }
        Intent intent = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(intent);
        }
    }
}
