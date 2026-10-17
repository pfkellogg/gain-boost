package com.pfkellogg.gainboost;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/**
 * The Gain Boost window: on / off and the gain slider, 0 to +24 dB. Opened by
 * the Quick Settings tile on a tablet and by the app itself on a TV, where the
 * remote moves between them (left / right on the slider changes the gain).
 */
final class Controls {

    /** Tags on the switch and the slider, for the hot key to find them. */
    static final String ON = "on", GAIN = "gain";

    private Controls() {
    }

    /**
     * isOn: what the switch shows at first. turnOn starts the boost; how
     * depends on who opened the window. hint: a line of small text under the
     * slider, or null.
     */
    static AlertDialog dialog(Context ctx, boolean isOn, Runnable turnOn, String hint) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        Context c = builder.getContext();  // the dialog's theme, so text colours match it
        int pad = dp(ctx, 24);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, pad / 2);

        Switch on = new Switch(c);
        on.setText("Boost on");
        on.setTextSize(20);
        on.setChecked(isOn);
        on.setTag(ON);
        box.addView(on);

        TextView gain = new TextView(c);
        gain.setTextSize(32);
        gain.setGravity(Gravity.CENTER);
        gain.setPadding(0, dp(ctx, 16), 0, 0);
        box.addView(gain);
        // The dialog theme's main text colour: some TV themes give the switch dark text on a dark dialog.
        TypedArray a = c.obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
        ColorStateList primary = a.getColorStateList(0);
        a.recycle();
        if (primary != null) {
            on.setTextColor(primary);
            gain.setTextColor(primary);
        }

        SeekBar bar = new SeekBar(c);
        bar.setMax(BoostService.MAX_DB);
        bar.setKeyProgressIncrement(1);
        bar.setProgress(BoostService.savedDb(ctx));
        bar.setTag(GAIN);
        box.addView(bar, new LinearLayout.LayoutParams(dp(ctx, 360), dp(ctx, 56)));
        if (hint != null) {
            TextView h = new TextView(c);
            h.setText(hint);
            h.setTextSize(14);
            h.setGravity(Gravity.CENTER);
            if (primary != null) h.setTextColor(primary.withAlpha(0xb0));
            box.addView(h);
        }
        gain.setText(label(bar.getProgress()));

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int v, boolean user) {
                gain.setText(label(v));
                if (user) BoostService.setGain(ctx, v);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        on.setOnCheckedChangeListener((v, checked) -> {
            if (checked) turnOn.run();
            else ctx.stopService(new Intent(ctx, BoostService.class));
        });

        return builder
                .setTitle("Gain Boost")
                .setView(box)
                .setPositiveButton("Done", null)
                .create();
    }

    private static String label(int db) {
        return "+" + db + " dB" + (db == 12 ? "  (4×)" : db == 24 ? "  (16×)" : "");
    }

    private static int dp(Context ctx, int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
