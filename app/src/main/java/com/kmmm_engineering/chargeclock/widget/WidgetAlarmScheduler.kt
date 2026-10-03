package com.kmmm_engineering.chargeclock.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.kmmm_engineering.chargeclock.MainActivity

/**
 * Re-arms a one-shot widget tick at the next minute boundary
 * ([nextMinuteBoundary]).
 *
 * Bitmap themes (Classic / 14SEG) do not use TextClock, so if this chain stops
 * the home widgets freeze (time and battery together).
 *
 * **Survival (always):** [AlarmManager.setAlarmClock]. It is Doze-safe, has no
 * while-idle quota, and still fires after process death. If it throws, fall
 * back to [AlarmManager.setExactAndAllowWhileIdle] when exact alarms are
 * allowed, else [AlarmManager.setAndAllowWhileIdle]. The next tick is armed
 * again from [com.kmmm_engineering.chargeclock.ChargeClockApp] and from the
 * tick receiver *before* drawing, so a crash cannot drop the only alarm.
 *
 * **Interactive precision:** while the screen is on and exact alarms are
 * allowed, also [AlarmManager.setExact] on a separate PendingIntent.
 * [setExact] alone is deferred in idle; because the following tick was only
 * armed when that alarm fired, a deferred exact alarm froze widgets for a
 * Doze maintenance window (~15–30 min). The survival alarm is what keeps
 * the chain alive across screen-off. Some OEMs deliver setAlarmClock ~20–35s
 * late; [WidgetTimeTickRegistrar] still hits :00 while the process is alive.
 *
 * Screen-off scheduling cancels the precision [setExact] so it is not left
 * deferred. [showIntent] opens [MainActivity] from the system alarm affordance.
 */
object WidgetAlarmScheduler {
    private const val TAG = "WidgetAlarmScheduler"
    private const val REQUEST_CODE_TICK = 41001
    private const val REQUEST_CODE_SHOW = 41002
    private const val REQUEST_CODE_EXACT = 41003

    enum class ArmPlan {
        /** setAlarmClock (or while-idle fallback) only. */
        SURVIVAL_ONLY,

        /** Survival plus interactive setExact. */
        SURVIVAL_AND_EXACT,
    }

    /** Upcoming minute boundary `((now / 60_000) + 1) * 60_000`. */
    fun nextMinuteBoundary(nowMillis: Long): Long =
        ((nowMillis / 60_000L) + 1L) * 60_000L

    fun armPlan(interactive: Boolean, canExact: Boolean): ArmPlan =
        if (interactive && canExact) ArmPlan.SURVIVAL_AND_EXACT else ArmPlan.SURVIVAL_ONLY

    fun scheduleNext(context: Context, forceSurvivalOnly: Boolean = false) {
        val appCtx = context.applicationContext
        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val tickPi = tickPendingIntent(appCtx)
        val exactPi = exactPendingIntent(appCtx)
        val showPi = showPendingIntent(appCtx)
        val nextMinute = nextMinuteBoundary(System.currentTimeMillis())
        val plan = if (forceSurvivalOnly) {
            ArmPlan.SURVIVAL_ONLY
        } else {
            armPlan(isInteractive(appCtx), canScheduleExact(am))
        }

        // Replace in place. Do not cancel the survival PendingIntent first:
        // a crash between cancel and set left widgets with no alarm at all.
        scheduleSurvival(am, nextMinute, tickPi, showPi)

        if (plan == ArmPlan.SURVIVAL_AND_EXACT) {
            scheduleInteractiveExact(am, nextMinute, exactPi)
        } else {
            try {
                am.cancel(exactPi)
            } catch (e: Exception) {
                Log.w(TAG, "cancel precision alarm failed", e)
            }
        }
    }

    /**
     * Arm only when a widget is still placed. Never cancels: a transient empty
     * id list during startup must not wipe a good alarm ([onDisabled] cancels).
     */
    fun scheduleNextIfPlaced(context: Context, forceSurvivalOnly: Boolean = false) {
        val appCtx = context.applicationContext
        val placed = try {
            hasPlacedWidgets(appCtx)
        } catch (e: Exception) {
            Log.w(TAG, "widget id query failed; arming anyway", e)
            true
        }
        if (placed) scheduleNext(appCtx, forceSurvivalOnly)
    }

    fun cancel(context: Context) {
        val appCtx = context.applicationContext
        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(tickPendingIntent(appCtx))
        am.cancel(exactPendingIntent(appCtx))
    }

    private fun hasPlacedWidgets(appCtx: Context): Boolean {
        val mgr = AppWidgetManager.getInstance(appCtx)
        val compact = mgr.getAppWidgetIds(
            ComponentName(appCtx, ClockWidgetCompactProvider::class.java),
        )
        val full = mgr.getAppWidgetIds(
            ComponentName(appCtx, ClockWidgetFullProvider::class.java),
        )
        return compact.isNotEmpty() || full.isNotEmpty()
    }

    private fun scheduleSurvival(
        am: AlarmManager,
        triggerAt: Long,
        tickPi: PendingIntent,
        showPi: PendingIntent,
    ) {
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showPi), tickPi)
            Log.i(TAG, "Survival: setAlarmClock @ $triggerAt")
            return
        } catch (e: Exception) {
            Log.w(TAG, "setAlarmClock failed; falling back", e)
        }
        try {
            if (canScheduleExact(am)) {
                Log.i(TAG, "Survival fallback: setExactAndAllowWhileIdle @ $triggerAt")
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, tickPi)
            } else {
                Log.i(TAG, "Survival fallback: setAndAllowWhileIdle (exact denied) @ $triggerAt")
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, tickPi)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact while-idle denied; using setAndAllowWhileIdle", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, tickPi)
        }
    }

    /** On-time path while the screen is on. Failure is non-fatal; survival stays armed. */
    private fun scheduleInteractiveExact(
        am: AlarmManager,
        triggerAt: Long,
        exactPi: PendingIntent,
    ) {
        try {
            Log.i(TAG, "Interactive precision: setExact @ $triggerAt")
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, exactPi)
        } catch (e: SecurityException) {
            Log.w(TAG, "setExact SecurityException; survival alarm still armed", e)
        } catch (e: Exception) {
            Log.w(TAG, "setExact failed; survival alarm still armed", e)
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

    private fun tickPendingIntent(context: Context): PendingIntent =
        broadcastPi(context, REQUEST_CODE_TICK)

    private fun exactPendingIntent(context: Context): PendingIntent =
        broadcastPi(context, REQUEST_CODE_EXACT)

    private fun broadcastPi(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, WidgetUpdateReceiver::class.java).apply {
            action = WidgetUpdateReceiver.ACTION_TICK
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
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
