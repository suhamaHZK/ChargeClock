package com.kmmm_engineering.chargeclock.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle

class ClockWidgetCompactProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        ClockWidgetUpdater.updateIds(
            context.applicationContext,
            appWidgetManager,
            appWidgetIds,
            ClockWidgetUpdater.Kind.COMPACT,
        )
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        ClockWidgetUpdater.updateIds(
            context.applicationContext,
            appWidgetManager,
            intArrayOf(appWidgetId),
            ClockWidgetUpdater.Kind.COMPACT,
        )
    }

    override fun onEnabled(context: Context) {
        WidgetAlarmScheduler.scheduleNext(context.applicationContext)
    }

    override fun onDisabled(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val fullLeft = mgr.getAppWidgetIds(
            ComponentName(context, ClockWidgetFullProvider::class.java),
        )
        if (fullLeft.isEmpty()) {
            WidgetAlarmScheduler.cancel(context.applicationContext)
        }
    }
}
