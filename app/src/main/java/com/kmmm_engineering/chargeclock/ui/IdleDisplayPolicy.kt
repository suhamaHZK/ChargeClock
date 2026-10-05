package com.kmmm_engineering.chargeclock.ui

/**
 * Pure (Android-free) idle / blank-screen rules for the main clock.
 *
 * Timeline after an interaction at `lastInteractionAt`:
 *  - Until [idleStartAt] (end of the 5 s tap-brighten, or a later `brightUntil`) the clock is
 *    "active": readable brightness, seconds shown, 1 s ticks.
 *  - From [idleStartAt] it is "idle": dimmed, and seconds may be hidden
 *    ([UserSettings.hideSecondsWhenIdle]).
 *  - From [blankAt] it may be fully black ([UserSettings.blankWhenIdleDischarging]) while
 *    discharging. `blankAt = max(idleStartAt, lastInteractionAt + delay)`, so delay 0
 *    ("Immediately") equals the end of the 5 s brighten and e.g. 60 s means 60 s after
 *    the last interaction.
 *
 * "Discharging" = `!isPlugged && !isCharging`. [com.kmmm_engineering.chargeclock.battery.BatteryMonitor]
 * already reports isCharging=true whenever plugged, so this matches the Discord discharge
 * side; the extra isPlugged check guards against a plugged-but-not-charging state ever
 * blanking the screen.
 */
object IdleDisplayPolicy {

    /** Existing single-tap brighten / "recent interaction" window. */
    const val BRIGHTEN_MS: Long = 5_000L

    /** Default blank delay (1 min). */
    const val DEFAULT_BLANK_DELAY_SEC: Int = 60

    /** Dropdown choices in seconds: Immediately / 1 / 2 / 3 / 5 / 10 min. */
    val BLANK_DELAY_OPTIONS_SEC: List<Int> = listOf(0, 60, 120, 180, 300, 600)

    /** Coerce a stored value to a supported option (unknown → default). */
    fun normalizeBlankDelaySec(value: Int): Int =
        if (value in BLANK_DELAY_OPTIONS_SEC) value else DEFAULT_BLANK_DELAY_SEC

    fun isDischarging(isCharging: Boolean, isPlugged: Boolean): Boolean = !isCharging && !isPlugged

    fun idleStartAt(lastInteractionAt: Long, brightUntil: Long): Long =
        maxOf(lastInteractionAt + BRIGHTEN_MS, brightUntil)

    fun blankAt(lastInteractionAt: Long, brightUntil: Long, blankDelaySec: Int): Long =
        maxOf(
            idleStartAt(lastInteractionAt, brightUntil),
            lastInteractionAt + blankDelaySec.coerceAtLeast(0) * 1_000L,
        )

    data class State(
        /** Past the tap-brighten window (and no overlay). */
        val idle: Boolean,
        /** Main clock must be drawn pure black. */
        val blank: Boolean,
        /** Next wall-clock time at which [idle]/[blank] may change, or null if not time-driven. */
        val nextChangeAt: Long?,
    )

    /**
     * @param overlayVisible settings screen or unlock slider is shown → never idle/blank.
     * @param batteryKnown false until the first battery sample (default status would look
     *   like "discharging").
     */
    fun evaluate(
        now: Long,
        lastInteractionAt: Long,
        brightUntil: Long,
        blankEnabled: Boolean,
        blankDelaySec: Int,
        isCharging: Boolean,
        isPlugged: Boolean,
        batteryKnown: Boolean,
        overlayVisible: Boolean,
    ): State {
        if (overlayVisible) return State(idle = false, blank = false, nextChangeAt = null)
        val idleAt = idleStartAt(lastInteractionAt, brightUntil)
        val idle = now >= idleAt
        val blankCandidate = blankEnabled && batteryKnown && isDischarging(isCharging, isPlugged)
        val blankTime = blankAt(lastInteractionAt, brightUntil, blankDelaySec)
        val blank = blankCandidate && now >= blankTime
        val next = when {
            !idle -> idleAt
            blankCandidate && !blank -> blankTime
            else -> null
        }
        return State(idle = idle, blank = blank, nextChangeAt = next)
    }

    /** Whether the seconds field should be shown right now. */
    fun showSecondsNow(showSeconds: Boolean, hideSecondsWhenIdle: Boolean, idle: Boolean): Boolean =
        showSeconds && !(hideSecondsWhenIdle && idle)

    /** Clock tick period: 1 s while seconds are visible, otherwise minute-aligned 60 s. */
    fun tickPeriodMs(secondsVisible: Boolean): Long = if (secondsVisible) 1_000L else 60_000L

    /** Delay until the next aligned tick boundary (never 0). */
    fun delayToNextTick(now: Long, periodMs: Long): Long {
        val rem = now % periodMs
        return if (rem == 0L) periodMs else periodMs - rem
    }

    enum class TapResult { WAKE_ONLY, NORMAL }

    /** A tap (single or double) on a black screen only wakes it. */
    fun classifyTap(blank: Boolean): TapResult = if (blank) TapResult.WAKE_ONLY else TapResult.NORMAL

    /**
     * After the black screen was woken at [wokeAt], presses within [WAKE_GUARD_MS] (e.g. the
     * 2nd tap of a double-tap on the black screen) still count as wake-only.
     */
    const val WAKE_GUARD_MS: Long = 500L

    fun classifyTap(blank: Boolean, pressAt: Long, wokeAt: Long): TapResult = when {
        blank -> TapResult.WAKE_ONLY
        wokeAt > 0L && pressAt >= wokeAt && pressAt - wokeAt < WAKE_GUARD_MS -> TapResult.WAKE_ONLY
        else -> TapResult.NORMAL
    }
}
