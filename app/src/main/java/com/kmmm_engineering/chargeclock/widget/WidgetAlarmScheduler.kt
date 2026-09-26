package com.kmmm_engineering.chargeclock.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.kmmm_engineering.chargeclock.MainActivity

/**
 * Schedules the next widget refresh at the upcoming minute boundary
 * `((now / 60_000) + 1) * 60_000`.
 *
 * **Interactive (screen on)** — ChargeClock’s usual charging-desk case:
 * prefer [AlarmManager.setExact] (`RTC_WAKEUP`), not
 * [AlarmManager.setExactAndAllowWhileIdle] and not setAlarmClock-only.
 * Some OEMs delay minute-cadence [AlarmManager.setAlarmClock] by ~20–35s, which
 * makes DSEG widgets late when the process is cold / TIME_TICK rarely runs at :00.
 * Interactive [AlarmManager.setExact] is therefore the primary on-time path for DSEG
 * widgets. Requires exact-alarm capability; on API 31+ we check
 * [AlarmManager.canScheduleExactAlarms]. If false, still try setAlarmClock and log —
 * do **not** silently fall through to inexact alarms.
 *
 * **Non-interactive (screen off)**:
 * use [AlarmManager.setAlarmClock] first (Doze-safe), then fall back to
 * [AlarmManager.setExactAndAllowWhileIdle] if exact is allowed, else
 * [AlarmManager.setAndAllowWhileIdle].
 *
 * [showIntent] opens [MainActivity] when the user taps a system next-alarm affordance
 * from setAlarmClock.
 *
 * While the process is alive, [WidgetTimeTickRegistrar] also arms a Handler to the
 * next minute boundary as a precision backup (AlarmManager must still be correct for
 * the process-dead case).
 */
object WidgetAlarmScheduler {
    private const val TAG = "WidgetAlarmScheduler"
    private const val REQUEST_CODE_TICK = 41001
    private const val REQUEST_CODE_SHOW = 41002

    fun scheduleNext(context: Context) {
        val appCtx = context.applicationContext
        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val tickPi = tickPendingIntent(appCtx)
        val showPi = showPendingIntent(appCtx)
        val now = System.currentTimeMillis()
        val nextMinute = ((now / 60_000L) + 1L) * 60_000L

        try {
            am.cancel(tickPi)
        } catch (e: Exception) {
            Log.w(TAG, "cancel before reschedule failed", e)
        }

        if (isInteractive(appCtx)) {
            scheduleInteractive(am, nextMinute, tickPi, showPi)
        } else {
            scheduleNonInteractive(am, nextMinute, tickPi, showPi)
        }
    }

    fun cancel(context: Context) {
        val appCtx = context.applicationContext
        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(tickPendingIntent(appCtx))
    }

    /**
     * Screen on: [AlarmManager.setExact] primary. If exact capability is missing or
     * setExact throws, try setAlarmClock and log — never silently use inexact.
     */
    private fun scheduleInteractive(
        am: AlarmManager,
        triggerAt: Long,
        tickPi: PendingIntent,
        showPi: PendingIntent,
    ) {
        val canExact = canScheduleExact(am)
        if (canExact) {
            try {
                Log.i(TAG, "Interactive: setExact @ $triggerAt")
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, tickPi)
                return
            } catch (e: SecurityException) {
                Log.w(TAG, "setExact SecurityException; trying setAlarmClock", e)
            } catch (e: Exception) {
                Log.w(TAG, "setExact failed; trying setAlarmClock", e)
            }
        } else {
            Log.w(
                TAG,
                "Interactive but canScheduleExactAlarms=false; trying setAlarmClock (not silent inexact)",
            )
        }
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showPi), tickPi)
            Log.i(TAG, "Interactive fallback: setAlarmClock @ $triggerAt")
        } catch (e: Exception) {
            Log.e(TAG, "Interactive: setExact unavailable and setAlarmClock failed", e)
        }
    }

    /**
     * Screen off: setAlarmClock first (Doze-safe), then exact-while-idle / while-idle.
     */
    private fun scheduleNonInteractive(
        am: AlarmManager,
        triggerAt: Long,
        tickPi: PendingIntent,
        showPi: PendingIntent,
    ) {
        try {
            Log.i(TAG, "Non-interactive: setAlarmClock @ $triggerAt")
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showPi), tickPi)
            return
        } catch (e: Exception) {
            Log.w(TAG, "setAlarmClock failed; falling back", e)
        }
        try {
            if (canScheduleExact(am)) {
                Log.i(TAG, "Non-interactive fallback: setExactAndAllowWhileIdle")
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, tickPi)
            } else {
                Log.i(TAG, "Non-interactive fallback: setAndAllowWhileIdle (exact denied)")
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, tickPi)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact while-idle denied; using setAndAllowWhileIdle", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, tickPi)
        }
    }

    private fun canScheduleExact(am: AlarmManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun isInteractive(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isInteractive
    }

    private fun tickPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetUpdateReceiver::class.java).apply {
            action = WidgetUpdateReceiver.ACTION_TICK
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_TICK,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun showPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_SHOW,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
