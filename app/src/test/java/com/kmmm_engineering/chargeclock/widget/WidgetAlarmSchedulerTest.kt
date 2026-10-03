package com.kmmm_engineering.chargeclock.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetAlarmSchedulerTest {

    @Test
    fun nextMinuteBoundary_isStrictlyAfterNow_andAligned() {
        val now = 1_759_492_582_000L // not on a minute boundary
        val next = WidgetAlarmScheduler.nextMinuteBoundary(now)
        assertEquals(0L, next % 60_000L)
        assertTrue(next > now)
        assertTrue(next - now <= 60_000L)
    }

    @Test
    fun nextMinuteBoundary_onExactMinute_waitsForTheFollowingOne() {
        val boundary = 1_759_492_560_000L
        assertEquals(0L, boundary % 60_000L)
        assertEquals(boundary + 60_000L, WidgetAlarmScheduler.nextMinuteBoundary(boundary))
        assertEquals(boundary + 60_000L, WidgetAlarmScheduler.nextMinuteBoundary(boundary + 1L))
        assertEquals(boundary + 60_000L, WidgetAlarmScheduler.nextMinuteBoundary(boundary + 59_999L))
    }

    @Test
    fun armPlan_screenOnWithExact_keepsPrecisionAlarm() {
        assertEquals(
            WidgetAlarmScheduler.ArmPlan.SURVIVAL_AND_EXACT,
            WidgetAlarmScheduler.armPlan(interactive = true, canExact = true),
        )
    }

    @Test
    fun armPlan_screenOffOrExactDenied_isSurvivalOnly() {
        assertEquals(
            WidgetAlarmScheduler.ArmPlan.SURVIVAL_ONLY,
            WidgetAlarmScheduler.armPlan(interactive = false, canExact = true),
        )
        assertEquals(
            WidgetAlarmScheduler.ArmPlan.SURVIVAL_ONLY,
            WidgetAlarmScheduler.armPlan(interactive = true, canExact = false),
        )
        assertEquals(
            WidgetAlarmScheduler.ArmPlan.SURVIVAL_ONLY,
            WidgetAlarmScheduler.armPlan(interactive = false, canExact = false),
        )
    }
}
