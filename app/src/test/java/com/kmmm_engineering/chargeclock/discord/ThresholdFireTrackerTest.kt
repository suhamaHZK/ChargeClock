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
    fun batteryDroppedWhileDeadThenRestart_doesNotFire() {
        // Simulate: last live reading was 45% (above 20), process died, battery fell to 15%.
        val t = ThresholdFireTracker()
        t.restore(
            ThresholdAlertSnapshot(
                lastPercent = 45,
                lastCharging = false,
                dischargeArmed = true,
                chargeArmed = true,
                lastDischargeThreshold = 20,
                lastChargeThreshold = -1,
                seeded = true,
            ),
        )
        // First check after restore must re-seed from current 15% — no fire.
        assertNull(t.check(percent = 15, isCharging = false, dischargeThreshold = 20, chargeThreshold = -1))
        // Still on alert side: quiet
        assertNull(t.check(percent = 10, isCharging = false, dischargeThreshold = 20, chargeThreshold = -1))
        // Leave then re-cross while running: fire
        assertNull(t.check(percent = 25, isCharging = false, dischargeThreshold = 20, chargeThreshold = -1))
        assertEquals("discharge", t.check(percent = 20, isCharging = false, dischargeThreshold = 20, chargeThreshold = -1))
    }

    @Test
    fun crossWhileRunning_fires() {
        val t = ThresholdFireTracker()
        assertNull(t.check(percent = 50, isCharging = false, dischargeThreshold = 20, chargeThreshold = -1))
        assertNull(t.check(percent = 30, isCharging = false, dischargeThreshold = 20, chargeThreshold = -1))
        assertEquals("discharge", t.check(percent = 20, isCharging = false, dischargeThreshold = 20, chargeThreshold = -1))
        // Once per arm cycle
        assertNull(t.check(percent = 15, isCharging = false, dischargeThreshold = 20, chargeThreshold = -1))
    }

    @Test
    fun chargeCrossWhileRunning_fires() {
        val t = ThresholdFireTracker()
        assertNull(t.check(percent = 50, isCharging = true, dischargeThreshold = -1, chargeThreshold = 80))
        assertEquals("charge", t.check(percent = 80, isCharging = true, dischargeThreshold = -1, chargeThreshold = 80))
        assertNull(t.check(percent = 90, isCharging = true, dischargeThreshold = -1, chargeThreshold = 80))
    }

    @Test
    fun restorePersistedDisarmedState_reseedsQuiet() {
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
        // Restore forces unseeded → first check seeds from current, no fire
        assertNull(t.check(percent = 15, isCharging = false, dischargeThreshold = 20, chargeThreshold = -1))
        assertNull(t.check(percent = 10, isCharging = false, dischargeThreshold = 20, chargeThreshold = -1))
    }

    @Test
    fun thresholdSettingChange_reArms() {
        val t = ThresholdFireTracker()
        assertNull(t.check(percent = 15, isCharging = false, dischargeThreshold = 20, chargeThreshold = -1))
        // Change threshold while already below new threshold: re-arm then... wait,
        // after seed we're disarmed at 15 for thresh 20. Changing to 10 while at 15
        // (above new thresh) should re-arm; then drop to 10 fires.
        assertNull(t.check(percent = 15, isCharging = false, dischargeThreshold = 10, chargeThreshold = -1))
        assertEquals("discharge", t.check(percent = 10, isCharging = false, dischargeThreshold = 10, chargeThreshold = -1))
    }

    @Test
    fun restoreKeepingSeed_crossWhileDead_fires() {
        // Widget / background polling: last live reading 45%, process idle, battery fell to 15%.
        val t = ThresholdFireTracker()
        t.restoreKeepingSeed(
            ThresholdAlertSnapshot(
                lastPercent = 45,
                lastCharging = false,
                dischargeArmed = true,
                chargeArmed = true,
                lastDischargeThreshold = 20,
                lastChargeThreshold = -1,
                seeded = true,
            ),
        )
        assertEquals("discharge", t.check(percent = 15, isCharging = false, dischargeThreshold = 20, chargeThreshold = -1))
        // Disarmed after fire
        assertNull(t.check(percent = 10, isCharging = false, dischargeThreshold = 20, chargeThreshold = -1))
    }

    @Test
    fun restoreKeepingSeed_invalidLastPercent_reseedsQuiet() {
        val t = ThresholdFireTracker()
        t.restoreKeepingSeed(
            ThresholdAlertSnapshot(
                lastPercent = -1,
                lastCharging = false,
                dischargeArmed = true,
                chargeArmed = true,
                lastDischargeThreshold = 20,
                lastChargeThreshold = -1,
                seeded = true,
            ),
        )
        // Invalid lastPercent → treat as unseeded
        assertNull(t.check(percent = 15, isCharging = false, dischargeThreshold = 20, chargeThreshold = -1))
    }
}
