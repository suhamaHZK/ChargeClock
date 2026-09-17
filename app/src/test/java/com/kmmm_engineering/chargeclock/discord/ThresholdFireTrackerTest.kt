package com.kmmm_engineering.chargeclock.discord

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThresholdFireTrackerTest {

    @Test
    fun coldStartWhileAlreadyBelowDischarge_doesNotFire() {
        val t = ThresholdFireTracker()
        // First observation while already at/below threshold: seed only
        assertNull(t.check(percent = 15, isCharging = false, dischargeThreshold = 20, chargeThreshold = -1))
        // Still on alert side: still quiet
        assertNull(t.check(percent = 14, isCharging = false, dischargeThreshold = 20, chargeThreshold = -1))
        // Leave zone then re-enter: fire
        assertNull(t.check(percent = 25, isCharging = false, dischargeThreshold = 20, chargeThreshold = -1))
        assertEquals("discharge", t.check(percent = 20, isCharging = false, dischargeThreshold = 20, chargeThreshold = -1))
    }

    @Test
    fun coldStartWhileAlreadyAboveCharge_doesNotFire() {
        val t = ThresholdFireTracker()
        assertNull(t.check(percent = 90, isCharging = true, dischargeThreshold = -1, chargeThreshold = 80))
        assertNull(t.check(percent = 95, isCharging = true, dischargeThreshold = -1, chargeThreshold = 80))
        assertNull(t.check(percent = 70, isCharging = true, dischargeThreshold = -1, chargeThreshold = 80))
        assertEquals("charge", t.check(percent = 80, isCharging = true, dischargeThreshold = -1, chargeThreshold = 80))
    }

    @Test
    fun offThresholdNeverFires() {
        val t = ThresholdFireTracker()
        assertNull(t.check(percent = 5, isCharging = false, dischargeThreshold = -1, chargeThreshold = -1))
        assertNull(t.check(percent = 1, isCharging = false, dischargeThreshold = -1, chargeThreshold = -1))
    }

    @Test
    fun restorePersistedDisarmedState_staysQuiet() {
        val t = ThresholdFireTracker()
        t.restore(
            ThresholdAlertSnapshot(
                lastPercent = 15,
                lastCharging = false,
                dischargeArmed = false,
                chargeArmed = true,
                lastDischargeThreshold = 20,
                lastChargeThreshold = -1,
                seeded = true,
            ),
        )
        assertNull(t.check(percent = 15, isCharging = false, dischargeThreshold = 20, chargeThreshold = -1))
        assertNull(t.check(percent = 10, isCharging = false, dischargeThreshold = 20, chargeThreshold = -1))
    }
}
