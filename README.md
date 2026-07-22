# Volume Control

A system-wide audio processor for Android that keeps all your phone's audio at a consistent volume level. No more getting blasted when you switch from a quiet podcast to a loud video.

It runs quietly in the background with a persistent notification so it survives app swipes and reboots.

## What it does

**Equalizer** - Puts a ceiling and a floor on your phone's volume. Anything above the max dB gets squashed down, anything below the min dB gets boosted up. You pick the range with two sliders in the app.

**Volume Scale** - A master volume knob that lives in your notification bar. Goes from 50% (half volume) to 500% (five times louder than stock Android lets you go). Use the + and - buttons on the notification to adjust it anytime.

**Test Sounds** - Built-in tone generator so you can hear the effects in action:
- *Constant tone* - steady 440 Hz hum, great for testing the volume scale slider
- *Sweep test* - ramps from barely audible to full blast over 10 seconds, so you can hear the equalizer clamping the extremes

## How to build

You need Java 17+ and the Android SDK.

Run the build script:

```bash
./build.sh
```

It figures out Gradle setup itself, installs what it needs, and drops the APK into `Output/VolumeControl.apk`.

If it can't find your SDK, set `ANDROID_HOME`:

```bash
export ANDROID_HOME="~/Android/Sdk"
./build.sh
```

## How to use

1. Install the APK on your phone (Pixel 10 Pro or similar, Android 15+)
2. Open the app and grant notification permission when asked
3. Flip the "Enable Audio Processing" switch on
4. Set your equalizer range with the two sliders
5. Pull down your notification shade - the volume scale slider is right there
6. Hit the test tone buttons to hear what's happening

The notification sticks around permanently while the service is on. Tap it to get back to the app.

## Requirements

- Android 10+ (API 29)
- Notification permission (prompted on launch)
- Runs best on Android 15 / Pixel devices
