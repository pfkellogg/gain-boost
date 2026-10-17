package com.pfkellogg.gainboost;

import android.accessibilityservice.AccessibilityService;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.SeekBar;
import android.widget.Switch;

/**
 * The hot key on a TV: press Mute twice quickly, in any app, and the Gain
 * Boost window comes up over it (the two presses cancel out on the TV). The
 * window never takes focus, so the app underneath (KaraFun, YouTube) keeps
 * playing; while it's up this service takes the remote's keys for it:
 * Left / Right the gain, OK on / off, Back closes. It also closes by itself
 * after CLOSE_MS without a key.
 *
 * An accessibility service, the only way an app sees the remote's keys
 * everywhere. Google TV allows it; Fire OS doesn't.
 */
public final class HotKey extends AccessibilityService {

    private static final long DOUBLE_MS = 700, CLOSE_MS = 8000;
    static final String HINT = "◀ ▶ gain    OK on / off    Back close";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private long lastMute = -1;
    private AlertDialog shown;
    private SeekBar bar;
    private Switch on;

    /** Whether it's turned on in Settings > Accessibility. */
    static boolean enabled(Context ctx) {
        String list = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String me = new ComponentName(ctx, HotKey.class).flattenToString();
        return list != null && (":" + list + ":").contains(":" + me + ":");
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (shown != null) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down) step(code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (down && e.getRepeatCount() == 0) on.setChecked(!on.isChecked());
                    stay();
                    return true;
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (!down) close();
                    return true;
                default:
                    break;  // Mute, volume, Home and the rest work as usual
            }
        }
        if (code == KeyEvent.KEYCODE_VOLUME_MUTE && down && e.getRepeatCount() == 0) {
            long t = SystemClock.uptimeMillis();
            if (lastMute >= 0 && t - lastMute < DOUBLE_MS) {
                lastMute = -1;
                if (shown == null) open();
                else close();
            } else {
                lastMute = t;
            }
        }
        return false;  // the TV still gets Mute
    }

    private void open() {
        shown = Controls.dialog(this, BoostService.running, () -> BoostService.start(this), HINT);
        shown.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        // Never focused: the app playing underneath stays in front and keeps playing.
        shown.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        shown.setOnDismissListener(x -> {
            shown = null;
            ui.removeCallbacks(timeout);
        });
        shown.show();
        shown.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.GONE);  // can't be focused; the hint says how to close
        View root = shown.getWindow().getDecorView();
        bar = root.findViewWithTag(Controls.GAIN);
        on = root.findViewWithTag(Controls.ON);
        stay();
    }

    private void step(int by) {
        int db = Math.max(0, Math.min(BoostService.MAX_DB, bar.getProgress() + by));
        bar.setProgress(db);
        BoostService.setGain(this, db);
        if (!on.isChecked()) on.setChecked(true);  // turning it up means it should be on
        stay();
    }

    private void stay() {
        ui.removeCallbacks(timeout);
        ui.postDelayed(timeout, CLOSE_MS);
    }

    private final Runnable timeout = this::close;

    private void close() {
        if (shown != null) shown.dismiss();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }
}
