package com.kmmm_engineering.chargeclock.discord

import android.content.Context
import com.kmmm_engineering.chargeclock.ChargeClockApp
import com.kmmm_engineering.chargeclock.R
import com.kmmm_engineering.chargeclock.data.UserSettings

/**
 * Shared Discord threshold-cross evaluation for MainActivity and widget updates.
 *
 * The first [evaluateAndNotify] in this process uses [ThresholdFireTracker.restore]
 * so [ThresholdFireTracker.check] only seeds from the live battery and returns null
 * (a cross while the process was dead must not notify). Later calls in the same
 * process use [ThresholdFireTracker.restoreKeepingSeed] so a real cross since the
 * last in-process sample can fire. Charge and discharge share that gate.
 *
 * The process-first flag is not reset when settings change; threshold re-arm stays
 * inside [ThresholdFireTracker.check]. Load + check + save run under one mutex so
 * two concurrent first calls (activity + widget) cannot both cross against a stale
 * snapshot. Empty webhook or OFF thresholds (-1) never send.
 */
object ThresholdAlertEvaluator {

    private val session = ProcessThresholdSession()

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

        val kind = session.withProcessRestore { firstInProcess ->
            val tracker = ThresholdFireTracker()
            val snapshot = repo.loadThresholdAlertSnapshot()
            if (firstInProcess) {
                tracker.restore(snapshot)
            } else {
                tracker.restoreKeepingSeed(snapshot)
            }
            val fired = tracker.check(
                percent = percent,
                isCharging = isCharging,
                dischargeThreshold = s.discordDischargeThreshold,
                chargeThreshold = s.discordChargeThreshold,
            )
            repo.saveThresholdAlertSnapshot(tracker.snapshot())
            fired
        }

        if (kind == null || s.discordWebhookUrl.isBlank()) return

        val msg = when (kind) {
            "discharge" -> appCtx.getString(R.string.discord_discharge_msg, percent)
            else -> appCtx.getString(R.string.discord_charge_msg, percent)
        }
        DiscordNotifier.send(s.discordWebhookUrl, msg)
    }
}
