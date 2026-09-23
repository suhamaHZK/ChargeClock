package com.kmmm_engineering.chargeclock.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/** Schedules the next widget refresh at the upcoming minute boundary. */
object WidgetAlarmScheduler {
    private const val TAG = "WidgetAlarmScheduler"
    private const val REQUEST_CODE = 41001

    fun scheduleNext(context: Context) {
        val appCtx = context.applicationContext
        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(appCtx)
        val now = System.currentTimeMillis()
        val nextMinute = ((now / 60_000L) + 1L) * 60_000L
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMinute, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMinute, pi)
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMinute, pi)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm denied; using inexact", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMinute, pi)
        }
    }

    fun cancel(context: Context) {
        val appCtx = context.applicationContext
        val am = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(appCtx))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetUpdateReceiver::class.java).apply {
            action = WidgetUpdateReceiver.ACTION_TICK
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
