package com.aldo.rainlock;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick Settings tile: tap to lock. When the app can't lock yet, the tap opens setup instead. */
public final class LockTileService extends TileService {
    @Override
    public void onStartListening() {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(RainLockService.isLockedNow() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setSubtitle(getString(RainLockService.canLock(this) ? R.string.tile_ready : R.string.tile_needs_setup));
        tile.updateTile();
    }

    @SuppressWarnings("deprecation")  // the Intent overload is the only one before Android 14
    @Override
    public void onClick() {
        if (RainLockService.canLock(this)) {
            RainLockService.requestLock(this);
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
