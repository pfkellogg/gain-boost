package com.pfkellogg.gainboost;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;

/**
 * Adds gain to everything the tablet plays, with Android's LoudnessEnhancer
 * (gain plus a limiter, so it doesn't clip harshly):
 *   - on the global output mix (audio session 0), which covers all apps on
 *     devices that still allow it, and
 *   - on each player session that apps announce (ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION,
 *     what equalizer apps listen for), for apps the global one misses.
 *
 * The global effect sits on whichever output was playing when it was made, so
 * it's made again whenever the output changes: screen mirroring to a TV
 * starting or stopping, headphones, Bluetooth.
 */
public final class BoostService extends Service {

    private static final String TAG = "GainBoost";
    static final int MAX_DB = 48, DEFAULT_DB = 12;
    /** What the tile shows. */
    static volatile boolean running;
    private static BoostService instance;
    static volatile String status = "Off";

    private int gainMb = DEFAULT_DB * 100;  // millibels: +12 dB = 4× the level
    private LoudnessEnhancer global;
    private final SparseArray<LoudnessEnhancer> sessions = new SparseArray<>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    /** Output changes come in bursts and take a moment to settle. */
    private static final long REATTACH_MS = 1500;

    /** False if the system skipped it (some tablets' battery managers do, from a tile). */
    static boolean start(Context ctx) {
        Intent i = new Intent(ctx, BoostService.class);
        return (Build.VERSION.SDK_INT >= 26 ? ctx.startForegroundService(i)
                : ctx.startService(i)) != null;  // Android 7 (Fire TV Stick)
    }

    static int savedDb(Context ctx) {
        return ctx.getSharedPreferences("boost", Context.MODE_PRIVATE).getInt("db", DEFAULT_DB);
    }

    /** Saves the gain, and applies it at once if the boost is on. Main thread. */
    static void setGain(Context ctx, int db) {
        ctx.getSharedPreferences("boost", Context.MODE_PRIVATE).edit().putInt("db", db).apply();
        if (instance != null) {
            instance.gainMb = db * 100;
            instance.applyAll();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter f = new IntentFilter(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        f.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        if (Build.VERSION.SDK_INT >= 26) registerReceiver(announced, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(announced, f);
        getSystemService(DisplayManager.class).registerDisplayListener(displays, ui);
        getSystemService(AudioManager.class).registerAudioDeviceCallback(devices, ui);
        gainMb = savedDb(this) * 100;
        instance = this;
    }

    /** Mirroring starts or stops: a display comes or goes. */
    private final DisplayManager.DisplayListener displays = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int id) {
            reattachSoon();
        }

        @Override
        public void onDisplayRemoved(int id) {
            reattachSoon();
        }

        @Override
        public void onDisplayChanged(int id) {
        }
    };

    private final AudioDeviceCallback devices = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
            reattachSoon();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
            reattachSoon();
        }
    };

    private void reattachSoon() {
        ui.removeCallbacks(reattach);
        ui.postDelayed(reattach, REATTACH_MS);
    }

    private final Runnable reattach = () -> {
        if (!running) return;
        if (global != null) global.release();
        global = null;
        onStartCommand(null, 0, 0);
        Log.i(TAG, "output changed: global effect made again");
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        running = true;
        if (global == null) {
            try {
                global = new LoudnessEnhancer(0);
            } catch (RuntimeException e) {
                Log.w(TAG, "global effect refused", e);
            }
        }
        applyAll();
        return START_STICKY;
    }

    private void applyAll() {
        apply(global);
        for (int i = 0; i < sessions.size(); i++) apply(sessions.valueAt(i));
        startForeground(1, notification());  // shows the new gain
        BoostTile.refresh(this);
        status = (global != null && global.hasControl() ? "On, +" + gainMb / 100 + " dB on all media"
                : global != null ? "On, but another app controls the global effect"
                : "On, but this tablet refuses a global effect; only apps that announce their audio are boosted")
                + (sessions.size() > 0 ? " (+" + sessions.size() + " app session" + (sessions.size() == 1 ? "" : "s") + ")" : "");
        Log.i(TAG, status);
    }

    private void apply(LoudnessEnhancer e) {
        if (e == null) return;
        try {
            e.setTargetGain(gainMb);
            e.setEnabled(true);
        } catch (RuntimeException ex) {
            Log.w(TAG, "couldn't set gain", ex);
        }
    }

    private final BroadcastReceiver announced = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent i) {
            int id = i.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1);
            if (id <= 0) return;
            if (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(i.getAction())) {
                if (sessions.get(id) != null) return;
                try {
                    LoudnessEnhancer e = new LoudnessEnhancer(id);
                    apply(e);
                    sessions.put(id, e);
                    Log.i(TAG, "boosting session " + id + " of " + i.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME));
                } catch (RuntimeException e) {
                    Log.w(TAG, "session " + id + " refused", e);
                }
            } else {
                LoudnessEnhancer e = sessions.get(id);
                if (e != null) e.release();
                sessions.remove(id);
            }
        }
    };

    @Override
    public void onDestroy() {
        instance = null;
        unregisterReceiver(announced);
        ui.removeCallbacks(reattach);
        getSystemService(DisplayManager.class).unregisterDisplayListener(displays);
        getSystemService(AudioManager.class).unregisterAudioDeviceCallback(devices);
        if (global != null) global.release();
        global = null;
        for (int i = 0; i < sessions.size(); i++) sessions.valueAt(i).release();
        sessions.clear();
        running = false;
        status = "Off";
        BoostTile.refresh(this);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification notification() {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager.class).createNotificationChannel(
                    new NotificationChannel("boost", "Gain Boost", NotificationManager.IMPORTANCE_LOW));
            b = new Notification.Builder(this, "boost");
        } else {
            b = new Notification.Builder(this);
        }
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return b
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle("Gain Boost on: +" + gainMb / 100 + " dB")
                .setContentText(MainActivity.isTv(this) ? "Change it in the Gain Boost app"
                        : "Change it from the Gain Boost tile: swipe down from the top")
                .setContentIntent(open)
                .setOngoing(true)
                .build();
    }
}
