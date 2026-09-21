# Charge Clock（充電時計）

充電中向けの全画面デジタル時計アプリです。

**対応OS:** Android 10（API 29）以上（compileSdk / targetSdk **36**）  
**applicationId:** `com.kmmm_engineering.chargeclock`  
**表示名:** 充電時計 / Charge Clock  
**最新ストア向けバージョン:** 1.0.0（versionCode 100）

## Features

- 没入型の黒背景フルスクリーン時計（日付＋曜日、時刻、バッテリー%）
- 充電中は稲妻アイコン、残量は 10 ブロックの角丸バーでも表示
- 待機輝度（デフォルト約 10%）、シングルタップで約 5 秒明るくする
- 数分ごとの表示位置シフト（有機 EL 焼け対策）
- ダブルタップ → スライドで設定（誤操作防止）
- 設定: 言語、輝度、文字色（プリセット＋カラーピッカー）、Discord Webhook／閾値、強制横画面、表示サイズ、12/24・秒、日付／月／曜日形式、充電器抜去で終了、自動スリープ など
- Discord は閾値をまたいだときのみ通知（テスト送信あり）
- 広告・課金なし
- **未実装（意図的）:** 充電開始での自動起動

## Build

```bash
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew :app:assembleRelease
```

- Release APK（R8）: `app/build/outputs/apk/release/`
- Play 用 AAB: `./gradlew :app:bundleRelease`（`keystore.properties` が必要。見本は `keystore.properties.example`）

Requires **JDK 17+** and **Android SDK 36**.

## Typical APK size

- **Release（minify）:** だいたい **約 4–5 MB**（Compose + Material3 前提）
- Jetpack Compose を使うと、古典的な View のみの超軽量時計アプリ（数百 KB 級）より大きくなりがちです

## Stack

- AGP 8.7.x / Kotlin 2.0.x / Compose BOM 2024.10.01
- DataStore Preferences / Material3 / material-icons-core
- [HoloColorPicker](https://github.com/LarsWerkman/HoloColorPicker) 1.5（設定の自由色選択）

## Design notes

See `design/REQUIREMENTS.md` and assets under `design/`.

## License

本リポジトリのソースは **MIT License** です。詳細は [`LICENSE`](LICENSE) を参照してください。

### サードパーティ

本アプリは次のライブラリも含みます。**ライセンスは本リポジトリの MIT とは別**です。

| ライブラリ | ライセンス | リンク |
|------------|------------|--------|
| HoloColorPicker (Lars Werkman) | Apache License 2.0 | https://github.com/LarsWerkman/HoloColorPicker |

アプリ内のクレジット／OSS 表記にも同趣旨の記載があります。AndroidX などその他の依存は各成果物のライセンスに従ってください。
