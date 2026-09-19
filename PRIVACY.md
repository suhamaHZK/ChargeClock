# プライバシーポリシー / Privacy Policy — 充電時計（Charge Clock）

最終更新 / Last updated: 2026-09-20

---

## 日本語

充電時計（Charge Clock）プライバシーポリシー

最終更新: 2026-09-20
開発者: kmmm_engineering（プレースホルダ — 必要に応じて編集）

本ポリシーは、充電時計が情報をどのように扱うかを説明します。

1. 端末内のみに保存されるデータ
言語、待機輝度、文字色、表示形式、Discord Webhook URL、バッテリー通知閾値、横表示、充電器抜去時終了、自動スリープなど、設定内容は Android DataStore / 端末内の設定としてローカルに保存されます。開発者が運営するサーバへこれらの設定を送信することはありません。

2. Discord Webhook（任意）
Discord Webhook URL を入力した場合、バッテリー閾値の到達通知およびテスト送信のメッセージを、そのユーザー指定 URL へ HTTP POST します。本アプリは独自の通知サーバを運営しません。Webhook URL とメッセージ内容は、ユーザーの設定どおり Discord（第三者）へ送信されます。Webhook 欄を空にすればこの機能は無効です。Discord 側で受け取るデータについては Discord のプライバシーポリシーが適用されます。

3. アカウント・広告・解析なし
本アプリはアカウント登録を必要としません。広告 SDK や解析／トラッキング SDK は含まれていません。

4. ネットワーク権限
INTERNET 権限は、Webhook URL を設定したとき（またはテスト送信時）の Discord への送信にのみ使用します。その他の機能はオフラインで動作します。

5. データの販売なし
個人データを販売しません。設定は端末内に留まり、ユーザーが制御する Webhook へ通知を送る場合を除き外部へ送られません。

6. お問い合わせ
本ポリシーに関するお問い合わせ: [CONTACT_EMAIL_PLACEHOLDER]
公開時に実在の連絡先へ置き換えてください。

---

## English

Charge Clock (充電時計) Privacy Policy

Last updated: 2026-09-20
Developer: kmmm_engineering (placeholder — edit as needed)

This policy describes how Charge Clock handles information.

1. Data stored on this device only
Settings you choose (language, idle brightness, text colors, display formats, Discord webhook URL, battery alert thresholds, landscape mode, exit-on-unplug, auto-sleep, and related preferences) are saved locally on your device using Android DataStore / local preferences. The app does not upload these settings to a developer-operated server.

2. Discord webhook (optional)
If you enter a Discord webhook URL, the app may HTTP POST alert messages (battery threshold crossings and the optional test send) to that URL you provide. The app does not run its own notification server. The webhook URL and message content are sent to Discord (a third party) as configured by you. Leave the webhook field empty to disable this feature. Discord’s own privacy policy applies to data received by Discord.

3. No accounts, ads, or analytics
Charge Clock does not require an account. It does not include advertising SDKs or analytics / tracking SDKs.

4. Network permission
The INTERNET permission is used only to send Discord webhook requests when you have configured a webhook URL (or tap test send). Other features work offline.

5. No sale of data
We do not sell your personal data. Settings stay on your device unless you choose to send alerts to a webhook you control.

6. Contact
Questions about this policy: [CONTACT_EMAIL_PLACEHOLDER]
Replace the placeholder with a real contact address when publishing.
