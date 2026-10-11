package com.pfkellogg.gainboost;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Quick Settings tile (swipe down from the top). A tap opens a small window
 * over the panel: on / off and the gain slider, 0 to +24 dB. It sits on the
 * notification shade, so the app playing underneath (YouTube) keeps playing.
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
        int pad = dp(24);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, pad / 2);

        Switch on = new Switch(this);
        on.setText("Boost on");
        on.setTextSize(20);
        on.setChecked(BoostService.running);
        box.addView(on);

        TextView gain = new TextView(this);
        gain.setTextSize(32);
        gain.setGravity(Gravity.CENTER);
        gain.setPadding(0, dp(16), 0, 0);
        box.addView(gain);

        SeekBar bar = new SeekBar(this);
        bar.setMax(BoostService.MAX_DB);
        bar.setProgress(BoostService.savedDb(this));
        box.addView(bar, new LinearLayout.LayoutParams(dp(360), dp(56)));
        gain.setText(label(bar.getProgress()));

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int v, boolean user) {
                gain.setText(label(v));
                if (user) BoostService.setGain(BoostTile.this, v);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        on.setOnCheckedChangeListener((v, checked) -> {
            if (!checked) {
                stopService(new Intent(this, BoostService.class));
                return;
            }
            try {
                BoostService.start(this);  // allowed for a few seconds after a tile tap
            } catch (IllegalStateException late) {  // ForegroundServiceStartNotAllowedException
                startActivityAndCollapse(PendingIntent.getActivity(this, 0,
                        new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE));
            }
        });

        showDialog(new AlertDialog.Builder(this)
                .setTitle("Gain Boost")
                .setView(box)
                .setPositiveButton("Done", null)
                .create());
    }

    private static String label(int db) {
        return "+" + db + " dB" + (db == 12 ? "  (4×)" : db == 24 ? "  (16×)" : "");
    }

    private void update() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = BoostService.running;
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("Gain Boost");
        t.setSubtitle(on ? "+" + BoostService.savedDb(this) + " dB" : "Off");
        t.updateTile();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
