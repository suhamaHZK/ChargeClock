package com.kmmm_engineering.chargeclock.discord

/**
 * Threshold-cross Discord alerts.
 *
 * Fires only when entering the alert zone from outside by percent:
 * - Discharge: previously above [dischargeThreshold], now ≤ it (while discharging)
 * - Charge: previously below [chargeThreshold], now ≥ it (while charging)
 *
 * Plug/unplug at the same percent already in/out of the zone does not fire.
 * Jumps that skip the exact threshold (e.g. 79→81, 31→29) still fire.
 *
 * Restore modes:
 * - [restore]: forces unseeded so the first [check] seeds from the live battery without
 *   treating a pre-death gap as a cross (legacy / cold-start quiet mode).
 * - [restoreKeepingSeed]: if persisted lastPercent is valid and seeded, keeps seeded so a
 *   cross between the last persisted sample and the current sample CAN fire (widget /
 *   background polling via [ThresholdAlertEvaluator]).
 *
 * While running: real crosses fire once; leave the alert zone to re-arm; threshold
 * setting changes re-arm. OFF thresholds (-1) never fire. Empty webhook is handled
 * by the caller / notifier.
 */
data class ThresholdAlertSnapshot(
    val lastPercent: Int = -1,
    val lastCharging: Boolean = false,
    val dischargeArmed: Boolean = true,
    val chargeArmed: Boolean = true,
    val lastDischargeThreshold: Int = -1,
    val lastChargeThreshold: Int = -1,
    val seeded: Boolean = false,
)

class ThresholdFireTracker {
    private var lastPercent: Int = -1
    private var lastCharging: Boolean = false
    private var dischargeArmed: Boolean = true
    private var chargeArmed: Boolean = true
    private var lastDischargeThreshold: Int = -1
    private var lastChargeThreshold: Int = -1
    private var seeded: Boolean = false

    /**
     * Load persisted latch fields. Always forces [seeded]=false so the first [check]
     * after restore re-seeds from the live battery without treating a
     * pre-death lastPercent → current percent gap as a cross.
     */
    fun restore(snapshot: ThresholdAlertSnapshot) {
        applySnapshotFields(snapshot)
        // Quiet mode: never trust pre-death lastPercent for cross detection.
        seeded = false
    }

    /**
     * Load persisted latch fields. If [ThresholdAlertSnapshot.lastPercent] is valid and
     * the snapshot was seeded, keep [seeded]=true so a cross since the last sample can fire.
     * Used by background / widget polling.
     */
    fun restoreKeepingSeed(snapshot: ThresholdAlertSnapshot) {
        applySnapshotFields(snapshot)
        seeded = snapshot.seeded && snapshot.lastPercent >= 0
    }

    private fun applySnapshotFields(snapshot: ThresholdAlertSnapshot) {
        lastPercent = snapshot.lastPercent
        lastCharging = snapshot.lastCharging
        dischargeArmed = snapshot.dischargeArmed
        chargeArmed = snapshot.chargeArmed
        lastDischargeThreshold = snapshot.lastDischargeThreshold
        lastChargeThreshold = snapshot.lastChargeThreshold
    }

    fun snapshot(): ThresholdAlertSnapshot = ThresholdAlertSnapshot(
        lastPercent = lastPercent,
        lastCharging = lastCharging,
        dischargeArmed = dischargeArmed,
        chargeArmed = chargeArmed,
        lastDischargeThreshold = lastDischargeThreshold,
        lastChargeThreshold = lastChargeThreshold,
        seeded = seeded,
    )

    /**
     * @return "discharge", "charge", or null
     */
    fun check(
        percent: Int,
        isCharging: Boolean,
        dischargeThreshold: Int,
        chargeThreshold: Int,
    ): String? {
        // Threshold setting changed → re-arm that side (user intentionally retargeted).
        if (dischargeThreshold != lastDischargeThreshold) {
            lastDischargeThreshold = dischargeThreshold
            dischargeArmed = true
        }
        if (chargeThreshold != lastChargeThreshold) {
            lastChargeThreshold = chargeThreshold
            chargeArmed = true
        }

        if (!seeded) {
            seedFromCurrent(percent, isCharging, dischargeThreshold, chargeThreshold)
            return null
        }

        var fire: String? = null

        if (!isCharging) {
            chargeArmed = true
            if (dischargeThreshold < 0) {
                dischargeArmed = true
            } else if (percent <= dischargeThreshold) {
                // Fire only when entering the zone from outside by percent.
                if (dischargeArmed && lastPercent > dischargeThreshold) {
                    fire = "discharge"
                    dischargeArmed = false
                } else {
                    // Already on alert side (e.g. plug/unplug at same %) — stay quiet.
                    dischargeArmed = false
                }
            } else {
                dischargeArmed = true
            }
        } else {
            dischargeArmed = true
            if (chargeThreshold < 0) {
                chargeArmed = true
            } else if (percent >= chargeThreshold) {
                // Fire only when entering the zone from outside by percent.
                if (chargeArmed && lastPercent < chargeThreshold) {
                    fire = "charge"
                    chargeArmed = false
                } else {
                    // Already on alert side (e.g. plug/unplug at same %) — stay quiet.
                    chargeArmed = false
                }
            } else {
                chargeArmed = true
            }
        }

        lastPercent = percent
        lastCharging = isCharging
        return fire
    }

    private fun seedFromCurrent(
        percent: Int,
        isCharging: Boolean,
        dischargeThreshold: Int,
        chargeThreshold: Int,
    ) {
        // Already on the alert side → disarm that side so we do not notify until we leave and re-cross.
        dischargeArmed = when {
            dischargeThreshold < 0 -> true
            !isCharging && percent <= dischargeThreshold -> false
            else -> true
        }
        chargeArmed = when {
            chargeThreshold < 0 -> true
            isCharging && percent >= chargeThreshold -> false
            else -> true
        }
        lastPercent = percent
        lastCharging = isCharging
        lastDischargeThreshold = dischargeThreshold
        lastChargeThreshold = chargeThreshold
        seeded = true
    }
}
