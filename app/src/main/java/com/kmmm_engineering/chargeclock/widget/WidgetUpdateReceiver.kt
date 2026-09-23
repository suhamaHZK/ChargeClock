package com.kmmm_engineering.chargeclock.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kmmm_engineering.chargeclock.ChargeClockApp
import kotlinx.coroutines.launch

/** Alarm / time-change / boot → refresh all widgets, Discord thresholds, and reschedule. */
class WidgetUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appCtx = context.applicationContext
        val pending = goAsync()
        val app = appCtx as? ChargeClockApp
        if (app != null) {
            app.applicationScope.launch {
                try {
                    ClockWidgetUpdater.updateAll(appCtx)
                    ClockWidgetUpdater.awaitPendingDiscordAlerts()
                } finally {
                    pending.finish()
                }
            }
        } else {
            try {
                ClockWidgetUpdater.updateAll(appCtx)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TICK = "com.kmmm_engineering.chargeclock.action.WIDGET_TICK"
    }
}
