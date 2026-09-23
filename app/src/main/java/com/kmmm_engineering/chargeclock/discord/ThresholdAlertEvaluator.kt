package com.kmmm_engineering.chargeclock.discord

import android.content.Context
import com.kmmm_engineering.chargeclock.ChargeClockApp
import com.kmmm_engineering.chargeclock.R
import com.kmmm_engineering.chargeclock.data.UserSettings

/**
 * Shared Discord threshold-cross evaluation for MainActivity and widget updates.
 *
 * Uses [ThresholdFireTracker.restoreKeepingSeed] so a cross between the persisted last
 * sample and the current sample can fire (background / widget polling). Persists the
 * latch after every check; empty webhook or OFF thresholds (-1) never send.
 */
object ThresholdAlertEvaluator {

    /**
     * @param settings optional snapshot to avoid a second DataStore read when the caller
     * already has settings; webhook / thresholds are taken from this when non-null.
     */
    suspend fun evaluateAndNotify(
        context: Context,
        percent: Int,
        isCharging: Boolean,
        settings: UserSettings? = null,
    ) {
        val appCtx = context.applicationContext
        val app = appCtx as? ChargeClockApp ?: return
        val repo = app.settingsRepository
        val s = settings ?: repo.getSettingsOnce()

        val tracker = ThresholdFireTracker()
        tracker.restoreKeepingSeed(repo.loadThresholdAlertSnapshot())
        val kind = tracker.check(
            percent = percent,
            isCharging = isCharging,
            dischargeThreshold = s.discordDischargeThreshold,
            chargeThreshold = s.discordChargeThreshold,
        )
        repo.saveThresholdAlertSnapshot(tracker.snapshot())

        if (kind == null || s.discordWebhookUrl.isBlank()) return

        val msg = when (kind) {
            "discharge" -> appCtx.getString(R.string.discord_discharge_msg, percent)
            else -> appCtx.getString(R.string.discord_charge_msg, percent)
        }
        DiscordNotifier.send(s.discordWebhookUrl, msg)
    }
}
