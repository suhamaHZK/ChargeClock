package com.kmmm_engineering.chargeclock.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kmmm_engineering.chargeclock.ChargeClockApp
import kotlinx.coroutines.launch

/**
 * Alarm / time-change / boot / package-replaced → refresh widgets and reschedule.
 * The next one-shot is armed before drawing so a crash cannot end the chain.
 */
class WidgetUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appCtx = context.applicationContext
        val pending = goAsync()
        // Survival alarm first. updateAll schedules again after a successful draw.
        runCatching { WidgetAlarmScheduler.scheduleNextIfPlaced(appCtx) }
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
