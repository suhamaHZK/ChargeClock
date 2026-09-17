package com.kmmm_engineering.chargeclock.discord

/**
 * Threshold-cross Discord alerts.
 *
 * Fires only when transitioning across a threshold:
 * - Discharge: previously above [dischargeThreshold], now ≤ it (while discharging)
 * - Charge: previously below [chargeThreshold], now ≥ it (while charging)
 *
 * Cold start / process death: either restore persisted state via [restore], or on the
 * first [check] seed from the current battery **without firing** so "already on the
 * alert side" does not re-notify.
 *
 * OFF thresholds (-1): never fire. Empty webhook is handled by the caller / notifier.
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

    fun restore(snapshot: ThresholdAlertSnapshot) {
        lastPercent = snapshot.lastPercent
        lastCharging = snapshot.lastCharging
        dischargeArmed = snapshot.dischargeArmed
        chargeArmed = snapshot.chargeArmed
        lastDischargeThreshold = snapshot.lastDischargeThreshold
        lastChargeThreshold = snapshot.lastChargeThreshold
        seeded = snapshot.seeded && snapshot.lastPercent >= 0
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
                if (dischargeArmed) {
                    // Crossing into (or re-entering while armed) the discharge alert zone.
                    // Require that we had a prior reading above the threshold when possible.
                    val crossed = lastPercent < 0 || lastPercent > dischargeThreshold || lastCharging
                    if (crossed) {
                        fire = "discharge"
                        dischargeArmed = false
                    } else {
                        // Still on alert side without having left — stay quiet.
                        dischargeArmed = false
                    }
                }
            } else {
                dischargeArmed = true
            }
        } else {
            dischargeArmed = true
            if (chargeThreshold < 0) {
                chargeArmed = true
            } else if (percent >= chargeThreshold) {
                if (chargeArmed) {
                    val crossed = lastPercent < 0 || lastPercent < chargeThreshold || !lastCharging
                    if (crossed) {
                        fire = "charge"
                        chargeArmed = false
                    } else {
                        chargeArmed = false
                    }
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
