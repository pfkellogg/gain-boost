package com.pfkellogg.gainboost;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Quick Settings tile (swipe down from the top). A tap opens the Gain Boost
 * window (Controls) over the panel. It sits on the notification shade, so the
 * app playing underneath (YouTube) keeps playing.
 */
public final class BoostTile extends TileService {

    /** Asks the tile to redraw (on / off or the gain changed). */
    static void refresh(Context ctx) {
        TileService.requestListeningState(ctx, new ComponentName(ctx, BoostTile.class));
    }

    @Override
    public void onStartListening() {
        update();
    }

    @Override
    public void onClick() {
        showDialog(Controls.dialog(this, BoostService.running, () -> {
            boolean started;
            try {
                started = BoostService.start(this);  // allowed for a few seconds after a tile tap
            } catch (IllegalStateException late) {  // ForegroundServiceStartNotAllowedException
                started = false;
            }
            if (!started) {  // the app's screen may start it
                startActivityAndCollapse(PendingIntent.getActivity(this, 0,
                        new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE));
            }
        }));
    }

    private void update() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = BoostService.running;
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("Gain Boost");
        if (Build.VERSION.SDK_INT >= 29) t.setSubtitle(on ? "+" + BoostService.savedDb(this) + " dB" : "Off");
        t.updateTile();
    }
}
