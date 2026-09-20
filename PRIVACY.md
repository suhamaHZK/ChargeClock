# Privacy Policy — Charge Clock (充電時計)

Last updated: 2026-09-20

This document is an optional repository template for GitHub Pages or store listings. The app does not ship an in-app privacy screen.

## Summary

1. **On-device settings only** — Preferences (language, brightness, colors, formats, Discord webhook URL, battery alert thresholds, landscape mode, exit-on-unplug, auto-sleep, and related options) are stored locally with Android DataStore. They are not uploaded to a developer-operated server.

2. **Optional Discord webhook** — If you enter a webhook URL, the app may HTTP POST alert messages (threshold crossings and the optional test send) to that URL. The app does not run its own notification server. Leave the webhook empty to disable this. Discord’s privacy policy applies to data Discord receives.

3. **No accounts, ads, or analytics** — No account registration; no advertising or analytics/tracking SDKs.

4. **Network permission** — `INTERNET` is used only for Discord webhook requests when a URL is configured (or for test send). Other features work offline.

5. **No sale of data** — Settings stay on the device unless you choose to send alerts to a webhook you control.

## Contact

Use the project’s public GitHub repository issues for questions about this policy.
