package com.kmmm_engineering.chargeclock.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.util.Log
import androidx.core.content.ContextCompat
import com.kmmm_engineering.chargeclock.ChargeClockApp
import kotlinx.coroutines.launch

/**
 * While-alive precision backups for minute flips:
 * 1. Dynamic [Intent.ACTION_TIME_TICK] (cannot be declared in the manifest).
 * 2. A [Handler] armed to the next minute boundary so we still hit :00 when
 *    AlarmManager is late on some OEMs (observed ~20–35s delay on setAlarmClock).
 *
 * Both paths invoke [ClockWidgetUpdater.updateAll] off the main thread — DataStore
 * is read via runBlocking inside updateAll and must not block main.
 *
 * When the process dies, Handler / TIME_TICK die with it; [WidgetAlarmScheduler]
 * (interactive [android.app.AlarmManager.setExact]) must still be correct for the
 * cold-start case.
 */
object WidgetTimeTickRegistrar {
    private const val TAG = "WidgetTimeTick"

    @Volatile
    private var registered = false

    @Volatile
    private var appContext: Context? = null

    private var handler: Handler? = null

    private val minuteRunnable = Runnable {
        val ctx = appContext ?: return@Runnable
        scheduleUpdateOffMain(ctx)
        armMinuteHandler(ctx)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action == Intent.ACTION_TIME_TICK) {
                val appCtx = context.applicationContext
                scheduleUpdateOffMain(appCtx)
                armMinuteHandler(appCtx)
            }
        }
    }

    fun register(context: Context) {
        val appCtx = context.applicationContext
        synchronized(this) {
            if (!registered) {
                appContext = appCtx
                handler = Handler(appCtx.mainLooper)
                ContextCompat.registerReceiver(
                    appCtx,
                    receiver,
                    IntentFilter(Intent.ACTION_TIME_TICK),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                registered = true
            }
        }
        armMinuteHandler(appCtx)
    }

    /** Cancel-before-rearm so TIME_TICK + prior Handler never storm. */
    private fun armMinuteHandler(appCtx: Context) {
        val h = handler ?: Handler(appCtx.mainLooper).also { handler = it }
        h.removeCallbacks(minuteRunnable)
        val now = System.currentTimeMillis()
        val nextMinute = ((now / 60_000L) + 1L) * 60_000L
        val delayMs = (nextMinute - now).coerceAtLeast(1L)
        h.postDelayed(minuteRunnable, delayMs)
        Log.d(TAG, "Armed Handler for +${delayMs}ms (nextMinute=$nextMinute)")
    }

    private fun scheduleUpdateOffMain(appCtx: Context) {
        val app = appCtx as? ChargeClockApp
        if (app != null) {
            app.applicationScope.launch {
                ClockWidgetUpdater.updateAll(appCtx)
            }
        } else {
            Thread {
                ClockWidgetUpdater.updateAll(appCtx)
            }.start()
        }
    }
}
