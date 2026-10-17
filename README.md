# Gain Boost

Make an Android tablet louder than its volume buttons go: up to **+24 dB** on
everything it plays (YouTube, KaraFun, music apps, and screen mirroring to a TV),
switched on and set from a **Quick Settings tile**.

Made for a tablet mirroring to a Fire TV, where even at full volume the TV
gets the sound too quietly. It also runs on a **Fire TV / Android TV**
(Android 7.1 and up): install the **-firetv** APK there, open **Gain Boost**
from Your Apps and set it with the remote (Down to the slider, Left / Right
for the gain). It has its own app ID (`com.pfkellogg.gainboost.firetv`)
because Fire OS saves a sideloaded app's "Your Apps" tile picture only once
per ID.

On a **Google TV / Android TV** (such as an onn. stick) there's also a
**hot key**: press **Mute twice quickly**, in any app, and the Gain Boost
window comes up over what's playing (see [TV hot key](#tv-hot-key)).

## Download

Get the APK from [**Releases**](../../releases/latest), open it on the tablet,
and allow installing from that source when Android asks. (It's a debug-signed
sideload build, not a Play Store app, so Play Protect may warn: choose
*Install anyway*.) Then add the tile (below) and tap it.

## Use

Swipe down from the top of the screen and tap **Gain Boost**. A small window
opens over whatever is playing (YouTube keeps playing):

- **Boost on**: switches the boost on or off.
- **Slider**: 0 to +24 dB, applied as you move it. +12 dB is 4× the level
  (the default), +24 dB is 16×.
- **Done**: closes the window.

The tile shows the gain when the boost is on ("+12 dB") and "Off" when it isn't.
Opening **Gain Boost** from the app list just turns the boost on.

While it's on, a "Gain Boost on" notification stays up: Android only keeps the
boost running while the app runs in the foreground.

### TV hot key

On a Google TV / Android TV, press **Mute twice quickly** on the remote, in
any app. The two presses cancel out on the TV, and the Gain Boost window comes
up over what's playing without stopping it (KaraFun, YouTube):

- **Left / Right**: gain down / up, 1 dB a press (turning it up also switches
  the boost on).
- **OK**: boost on / off.
- **Back** (or Up / Down): closes it. It also closes by itself after 8 seconds.

The hot key is an accessibility service, the only way an app sees the remote's
keys in every app. Turn it on once in **Settings > Accessibility > Gain Boost
hot key**; for a sideloaded app Android may grey that out as a "restricted
setting", so over adb is easier:

```
adb shell settings put secure enabled_accessibility_services com.pfkellogg.gainboost/com.pfkellogg.gainboost.HotKey
adb shell settings put secure accessibility_enabled 1
```

It only watches for the Mute double press (and, while its window is up, the
keys above); it reads nothing on the screen. Fire OS doesn't allow
accessibility services, so there's no hot key on a Fire TV.

### Adding the tile

Swipe down twice, tap the pencil (edit) icon and drag **Gain Boost** into the
panel. Or over adb:

```
adb shell cmd statusbar add-tile com.pfkellogg.gainboost/.BoostTile
```

## How it works

Android's **LoudnessEnhancer** audio effect: gain with a built-in limiter, so
loud parts are held back instead of clipping harshly. At high settings that
limiting can be heard as "pumping"; turn it down a few dB if music sounds
squashed.

- **All apps**: the effect goes on the global output mix (audio session 0).
  Android has deprecated that, but many devices still honour it.
- **Apps that announce their audio** (`ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`,
  what equalizer apps listen for) also get an effect on their own session, for
  devices where the global one doesn't work.
- The global effect sits on whichever output was playing when it was made, so
  it's made again whenever the output changes: mirroring starting or stopping,
  headphones, Bluetooth.

| File | What's in it |
|---|---|
| `BoostService.java` | Foreground service holding the effects; re-attaches them when the output changes |
| `BoostTile.java` | Quick Settings tile |
| `Controls.java` | The on / off + slider window, shared by the tile, the TV app and the hot key |
| `HotKey.java` | TV hot key: Mute twice opens the window over any app; the remote's keys work it |
| `MainActivity.java` | The app icon: turns the boost on (on a TV, and shows the window) |

Plain Java, no dependencies; `minSdk 25`. Tested on an Android 16 tablet, a
Fire TV Stick 4K (Fire OS 6) and an onn. Full HD stick (Google TV, Android 14).

## Build and install

Needs JDK 17+ and the Android SDK (platform 34):

```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/standard/debug/app-standard-debug.apk   # tablet, Google TV
adb install -r app/build/outputs/apk/firetv/debug/app-firetv-debug.apk       # Fire TV
```

## License

MIT
