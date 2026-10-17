package com.pfkellogg.gainboost;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;

/**
 * Opening the app turns the boost on. On a tablet it then closes again: the
 * controls live in the Quick Settings tile (swipe down from the top). A TV
 * has no Quick Settings, so there it shows the Gain Boost window, worked with
 * the remote, and closes with it.
 */
public final class MainActivity extends Activity {


    static boolean isTv(Context ctx) {
        return ctx.getSystemService(UiModeManager.class).getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION
                || ctx.getPackageManager().hasSystemFeature("amazon.hardware.fire_tv");
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 0);
            return;  // carries on after the answer
        }
        startBoost();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        startBoost();
    }

    private void startBoost() {
        BoostService.start(this);
        if (!isTv(this)) {
            finish();
            return;
        }
        AlertDialog d = Controls.dialog(this, true, () -> BoostService.start(this),
                HotKey.enabled(this) ? "Hot key: press Mute twice quickly, in any app"
                        : !getPackageManager().hasSystemFeature("amazon.hardware.fire_tv") ?  // Fire OS: no accessibility services
                        "For a hot key (Mute twice), turn on Gain Boost in Settings > Accessibility" : null);
        d.setOnDismissListener(x -> finish());
        d.show();
    }
}
