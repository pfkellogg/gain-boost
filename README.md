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
| `BoostTile.java` | Quick Settings tile and its on / off + slider window |
| `MainActivity.java` | The app icon: turns the boost on and closes |

Plain Java, no dependencies; `minSdk 26`. Tested on an Android 16 tablet.

## Build and install

Needs JDK 17+ and the Android SDK (platform 34):

```
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## License

MIT
