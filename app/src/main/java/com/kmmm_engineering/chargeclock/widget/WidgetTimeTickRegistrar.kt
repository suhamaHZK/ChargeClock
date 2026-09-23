package com.kmmm_engineering.chargeclock.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * Dynamically registers [Intent.ACTION_TIME_TICK] for minute-flip widget refreshes while the
 * process is alive. Cannot be declared in the manifest. [WidgetAlarmScheduler] remains the
 * fallback when the process is dead.
 */
object WidgetTimeTickRegistrar {
    @Volatile
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action == Intent.ACTION_TIME_TICK) {
                ClockWidgetUpdater.updateAll(context.applicationContext)
            }
        }
    }

    fun register(context: Context) {
        if (registered) return
        synchronized(this) {
            if (registered) return
            val appCtx = context.applicationContext
            ContextCompat.registerReceiver(
                appCtx,
                receiver,
                IntentFilter(Intent.ACTION_TIME_TICK),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            registered = true
        }
    }
}
