# Charge Clock（充電時計）

Fullscreen charging-oriented digital clock for Android 10+ (API 29).

**applicationId:** `com.kmmm_engineering.chargeclock`  
**Display name:** 充電時計 / Charge Clock

## Features (MVP)

- Immersive black fullscreen clock: date + weekday, time (seconds on by default), battery %
- Idle window brightness with 5s brighten on single tap; settings opens at readable (system) brightness with live idle-slider preview; restore system brightness on pause
- OLED pixel shift every few minutes
- Double-tap → rounded slide-to-settings (EN: `Slide to settings >>` / JA: `スライドで設定 >>`)
- Settings via DataStore (language, brightness, text color, Discord webhook/thresholds as dropdowns, landscape, scale, 12/24, seconds, date format dropdown, month/weekday labels, exit on unplug, auto-sleep)
- Discord webhook notification once per threshold crossing
- No ads, no IAP
- **Not in MVP:** auto-start on plug-in

## Build

```bash
export ANDROID_HOME=/workspace/Android/Sdk
./gradlew :app:assembleRelease
```

Release APK (R8 minify + resource shrink, arm64-v8a / armeabi-v7a):  
`app/build/outputs/apk/release/app-release-unsigned.apk`

Debug (unminified, larger): `./gradlew :app:assembleDebug`

Requires JDK 17+ and Android SDK 35.

## Typical APK size

After dropping `material-icons-extended`, removing unused Navigation/ViewModel deps, and enabling R8:

- **Release (minified):** typically **~3–6 MB** (Compose + Material3 baseline; not comparable to ~200 KB classic View apps)
- **Debug (unminified):** previously ~58 MB mainly due to `material-icons-extended` and unshrunk DEX

A ~200 KB “Battery Clock” style app usually uses the Android View system without Compose. Jetpack Compose + Material3 alone commonly add multiple MB even with aggressive R8.

## Stack

AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01, DataStore Preferences, Material3, material-icons-core (not extended).

## Design

See `design/REQUIREMENTS.md` and `design/unlock-slider.svg`.

## License

Private repository. All rights reserved.
