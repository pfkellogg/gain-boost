package com.pfkellogg.gainboost;

import android.app.Activity;
import android.os.Bundle;

/**
 * Opening the app turns the boost on and closes again. The controls live in
 * the Quick Settings tile (swipe down from the top).
 */
public final class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 0);
            return;  // finishes after the answer
        }
        BoostService.start(this);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        BoostService.start(this);
        finish();
    }
}
