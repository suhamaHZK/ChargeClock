package com.kmmm_engineering.chargeclock.ui

import com.kmmm_engineering.chargeclock.ui.IdleDisplayPolicy.TapResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleDisplayPolicyTest {

    private val t0 = 1_759_492_582_000L

    private fun eval(
        now: Long,
        lastInteractionAt: Long = t0,
        brightUntil: Long = 0L,
        blankEnabled: Boolean = true,
        blankDelaySec: Int = 60,
        isCharging: Boolean = false,
        isPlugged: Boolean = false,
        batteryKnown: Boolean = true,
        overlayVisible: Boolean = false,
    ) = IdleDisplayPolicy.evaluate(
        now = now,
        lastInteractionAt = lastInteractionAt,
        brightUntil = brightUntil,
        blankEnabled = blankEnabled,
        blankDelaySec = blankDelaySec,
        isCharging = isCharging,
        isPlugged = isPlugged,
        batteryKnown = batteryKnown,
        overlayVisible = overlayVisible,
    )

    @Test
    fun blank_onlyWhenEnabledDischargingAndDelayElapsed() {
        // Before delay: idle (past 5 s) but not blank yet; next change at blank time.
        val before = eval(now = t0 + 59_999L)
        assertTrue(before.idle)
        assertFalse(before.blank)
        assertEquals(t0 + 60_000L, before.nextChangeAt)

        val after = eval(now = t0 + 60_000L)
        assertTrue(after.idle)
        assertTrue(after.blank)
        assertNull(after.nextChangeAt)

        // Disabled → never blank.
        val disabled = eval(now = t0 + 600_000L, blankEnabled = false)
        assertTrue(disabled.idle)
        assertFalse(disabled.blank)
        assertNull(disabled.nextChangeAt)

        // Battery not yet known → never blank.
        assertFalse(eval(now = t0 + 600_000L, batteryKnown = false).blank)
    }

    @Test
    fun delayZero_blanksRightAfterFiveSecondBrighten() {
        val during = eval(now = t0 + 4_999L, blankDelaySec = 0)
        assertFalse(during.idle)
        assertFalse(during.blank)
        assertEquals(t0 + IdleDisplayPolicy.BRIGHTEN_MS, during.nextChangeAt)

        val end = eval(now = t0 + 5_000L, blankDelaySec = 0)
        assertTrue(end.idle)
        assertTrue(end.blank)
        assertEquals(t0 + 5_000L, IdleDisplayPolicy.blankAt(t0, 0L, 0))
    }

    @Test
    fun laterBrightUntil_postponesIdleAndBlank() {
        val brightUntil = t0 + 8_000L
        assertFalse(eval(now = t0 + 7_000L, brightUntil = brightUntil, blankDelaySec = 0).idle)
        val s = eval(now = t0 + 8_000L, brightUntil = brightUntil, blankDelaySec = 0)
        assertTrue(s.idle)
        assertTrue(s.blank)
    }

    @Test
    fun pluggedOrCharging_cancelsBlank() {
        val plugged = eval(now = t0 + 600_000L, isPlugged = true, isCharging = true)
        assertTrue(plugged.idle)
        assertFalse(plugged.blank)
        assertNull(plugged.nextChangeAt)
        // Plugged but not charging (e.g. full / limited) must not blank either.
        assertFalse(eval(now = t0 + 600_000L, isPlugged = true, isCharging = false).blank)
        assertFalse(eval(now = t0 + 600_000L, isPlugged = false, isCharging = true).blank)
        assertTrue(IdleDisplayPolicy.isDischarging(isCharging = false, isPlugged = false))
        assertFalse(IdleDisplayPolicy.isDischarging(isCharging = true, isPlugged = true))
    }

    @Test
    fun overlay_neverIdleOrBlank() {
        val s = eval(now = t0 + 600_000L, blankDelaySec = 0, overlayVisible = true)
        assertFalse(s.idle)
        assertFalse(s.blank)
        assertNull(s.nextChangeAt)
    }

    @Test
    fun blankDelay_normalizesUnknownValues() {
        IdleDisplayPolicy.BLANK_DELAY_OPTIONS_SEC.forEach {
            assertEquals(it, IdleDisplayPolicy.normalizeBlankDelaySec(it))
        }
        assertEquals(listOf(0, 60, 120, 180, 300, 600), IdleDisplayPolicy.BLANK_DELAY_OPTIONS_SEC)
        assertEquals(60, IdleDisplayPolicy.normalizeBlankDelaySec(45))
        assertEquals(60, IdleDisplayPolicy.normalizeBlankDelaySec(-1))
    }

    @Test
    fun showSecondsNow_respectsSettingAndIdle() {
        assertTrue(IdleDisplayPolicy.showSecondsNow(showSeconds = true, hideSecondsWhenIdle = false, idle = true))
        assertTrue(IdleDisplayPolicy.showSecondsNow(showSeconds = true, hideSecondsWhenIdle = true, idle = false))
        assertFalse(IdleDisplayPolicy.showSecondsNow(showSeconds = true, hideSecondsWhenIdle = true, idle = true))
        assertFalse(IdleDisplayPolicy.showSecondsNow(showSeconds = false, hideSecondsWhenIdle = false, idle = false))
        assertFalse(IdleDisplayPolicy.showSecondsNow(showSeconds = false, hideSecondsWhenIdle = true, idle = false))
    }

    @Test
    fun classifyTap_wakeOnlyWhenBlank() {
        assertEquals(TapResult.WAKE_ONLY, IdleDisplayPolicy.classifyTap(blank = true))
        assertEquals(TapResult.NORMAL, IdleDisplayPolicy.classifyTap(blank = false))
    }

    @Test
    fun classifyTap_wakeGuardSwallowsFollowUpTap() {
        val wokeAt = t0
        assertEquals(TapResult.WAKE_ONLY, IdleDisplayPolicy.classifyTap(true, pressAt = t0 + 10_000L, wokeAt = wokeAt))
        assertEquals(TapResult.WAKE_ONLY, IdleDisplayPolicy.classifyTap(false, pressAt = t0 + 200L, wokeAt = wokeAt))
        assertEquals(
            TapResult.NORMAL,
            IdleDisplayPolicy.classifyTap(false, pressAt = t0 + IdleDisplayPolicy.WAKE_GUARD_MS, wokeAt = wokeAt),
        )
        // Never woken → normal.
        assertEquals(TapResult.NORMAL, IdleDisplayPolicy.classifyTap(false, pressAt = t0, wokeAt = 0L))
        // Press before the wake (stale) → normal.
        assertEquals(TapResult.NORMAL, IdleDisplayPolicy.classifyTap(false, pressAt = t0 - 1L, wokeAt = wokeAt))
    }

    @Test
    fun tickPeriod_secondOrMinuteAligned() {
        assertEquals(1_000L, IdleDisplayPolicy.tickPeriodMs(secondsVisible = true))
        assertEquals(60_000L, IdleDisplayPolicy.tickPeriodMs(secondsVisible = false))

        val minute = 1_759_492_560_000L
        assertEquals(60_000L, IdleDisplayPolicy.delayToNextTick(minute, 60_000L))
        assertEquals(1L, IdleDisplayPolicy.delayToNextTick(minute + 59_999L, 60_000L))
        assertEquals(22_000L, IdleDisplayPolicy.delayToNextTick(minute + 38_000L, 60_000L))
        assertEquals(1_000L, IdleDisplayPolicy.delayToNextTick(minute, 1_000L))
        assertEquals(750L, IdleDisplayPolicy.delayToNextTick(minute + 250L, 1_000L))
    }
}
