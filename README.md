# Charge Clock（充電時計）

Fullscreen charging-oriented digital clock for Android 10+ (API 29).

**applicationId:** `com.kmmm_engineering.chargeclock`  
**Display name:** 充電時計 / Charge Clock

## Features (MVP)

- Immersive black fullscreen clock: date + weekday, time (seconds on by default), battery %
- Idle window brightness with 5s brighten on single tap; restore system brightness on pause
- OLED pixel shift every few minutes
- Double-tap → rounded slide-to-settings (`スライドで設定 >>`)
- Settings via DataStore (language, brightness, text color, Discord webhook/thresholds, landscape, scale, 12/24, seconds, date/month/weekday formats, exit on unplug, auto-sleep)
- Discord webhook notification once per threshold crossing
- No ads, no IAP
- **Not in MVP:** auto-start on plug-in

## Build

```bash
export ANDROID_HOME=/workspace/Android/Sdk
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Requires JDK 17+ and Android SDK 35.

## Stack

Mirrors Plain Editor: AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01, DataStore Preferences, Material3.

## Design

See `design/REQUIREMENTS.md` and `design/unlock-slider.svg`.

## License

Private repository. All rights reserved.
