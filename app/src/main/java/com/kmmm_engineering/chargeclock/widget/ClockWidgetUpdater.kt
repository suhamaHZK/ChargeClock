package com.kmmm_engineering.chargeclock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.BatteryManager
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.kmmm_engineering.chargeclock.ChargeClockApp
import com.kmmm_engineering.chargeclock.MainActivity
import com.kmmm_engineering.chargeclock.R
import com.kmmm_engineering.chargeclock.data.UserSettings
import com.kmmm_engineering.chargeclock.discord.ThresholdAlertEvaluator
import com.kmmm_engineering.chargeclock.ui.batteryFilledBlocks
import com.kmmm_engineering.chargeclock.util.Formatters
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar

/**
 * Builds and pushes RemoteViews for both widget sizes.
 *
 * Time display uses [android.widget.TextClock] so minutes flip in the widget host without
 * the app process; this updater no longer pushes time via setTextViewText.
 * App [UserSettings.use24Hour] is forced by setting both format12Hour and format24Hour to
 * the same pattern (TextClock otherwise follows the system 12/24 setting).
 *
 * Update cadence (minSdk 29) — still needed for date / battery / Discord:
 * - android:updatePeriodMillis is floored to ~30 minutes by the system — safety net only.
 * - Primary: [WidgetAlarmScheduler] uses AlarmManager.setExactAndAllowWhileIdle at each
 *   minute boundary (SCHEDULE_EXACT_ALARM). Falls back to setAndAllowWhileIdle if exact
 *   alarms are denied. Also refreshes on TIME_CHANGED / TIMEZONE_CHANGED / BOOT_COMPLETED
 *   and when settings change via [updateAll].
 * - While the process is alive, [WidgetTimeTickRegistrar] also listens for
 *   ACTION_TIME_TICK (dynamic only; cannot be in the manifest).
 * - After each [updateAll], Discord battery thresholds are evaluated via
 *   [com.kmmm_engineering.chargeclock.discord.ThresholdAlertEvaluator].
 * Widgets never show seconds; text size scales to widget bounds (ignores displayScale).
 */
object ClockWidgetUpdater {

    enum class Kind { COMPACT, FULL }

    private val pendingDiscordLock = Any()
    @Volatile
    private var pendingDiscordJob: Job? = null

    fun updateAll(context: Context) {
        val appCtx = context.applicationContext
        val mgr = AppWidgetManager.getInstance(appCtx)
        val compactIds = mgr.getAppWidgetIds(
            ComponentName(appCtx, ClockWidgetCompactProvider::class.java),
        )
        val fullIds = mgr.getAppWidgetIds(
            ComponentName(appCtx, ClockWidgetFullProvider::class.java),
        )
        val settings = loadSettings(appCtx)
        val battery = readBattery(appCtx)
        if (compactIds.isNotEmpty() || fullIds.isNotEmpty()) {
            compactIds.forEach { id ->
                updateOne(appCtx, mgr, id, Kind.COMPACT, settings, battery.percent, battery.charging)
            }
            fullIds.forEach { id ->
                updateOne(appCtx, mgr, id, Kind.FULL, settings, battery.percent, battery.charging)
            }
            WidgetAlarmScheduler.scheduleNext(appCtx)
        }
        // Evaluate Discord thresholds even with no widgets (settings / TIME_TICK / alarm).
        maybeEvaluateDiscordThresholds(appCtx, battery.percent, battery.charging, settings)
    }

    /**
     * Await the most recent Discord evaluation launched by [updateAll].
     * Used by [WidgetUpdateReceiver] with goAsync so AlarmManager wakes outlive the webhook.
     */
    suspend fun awaitPendingDiscordAlerts(timeoutMs: Long = 15_000L) {
        val job = synchronized(pendingDiscordLock) { pendingDiscordJob }
        if (job != null) {
            withTimeoutOrNull(timeoutMs) { job.join() }
        }
    }

    private fun maybeEvaluateDiscordThresholds(
        context: Context,
        percent: Int,
        charging: Boolean,
        settings: UserSettings,
    ) {
        val app = context.applicationContext as? ChargeClockApp ?: return
        val job = app.applicationScope.launch {
            withTimeoutOrNull(15_000L) {
                ThresholdAlertEvaluator.evaluateAndNotify(
                    context = app,
                    percent = percent,
                    isCharging = charging,
                    settings = settings,
                )
            }
        }
        synchronized(pendingDiscordLock) {
            pendingDiscordJob = job
        }
    }

    fun updateIds(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        kind: Kind,
    ) {
        if (appWidgetIds.isEmpty()) return
        val appCtx = context.applicationContext
        val settings = loadSettings(appCtx)
        val battery = readBattery(appCtx)
        appWidgetIds.forEach { id ->
            updateOne(appCtx, appWidgetManager, id, kind, settings, battery.percent, battery.charging)
        }
        WidgetAlarmScheduler.scheduleNext(appCtx)
    }

    private fun loadSettings(context: Context): UserSettings {
        val app = context.applicationContext as? ChargeClockApp
        return if (app != null) {
            runBlocking { app.settingsRepository.getSettingsOnce() }
        } else {
            UserSettings()
        }
    }

    private data class BatterySnap(val percent: Int, val charging: Boolean)

    private fun readBattery(context: Context): BatterySnap {
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).toInt().coerceIn(0, 100)
        } else {
            0
        }
        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0
        return BatterySnap(pct, charging)
    }

    private fun updateOne(
        context: Context,
        mgr: AppWidgetManager,
        appWidgetId: Int,
        kind: Kind,
        settings: UserSettings,
        percent: Int,
        charging: Boolean,
    ) {
        val options = mgr.getAppWidgetOptions(appWidgetId)
        val views = when (kind) {
            Kind.COMPACT -> buildCompact(context, settings, percent, options)
            Kind.FULL -> buildFull(context, settings, percent, charging, options)
        }
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            appWidgetId,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, pi)
        mgr.updateAppWidget(appWidgetId, views)
    }

    private fun withoutSeconds(settings: UserSettings): UserSettings =
        settings.copy(showSeconds = false)

    /**
     * Force app 12/24 preference onto TextClock: both format slots get the same pattern so
     * the system 12/24 setting cannot pick a different style.
     */
    private fun applyWidgetTimeFormats(views: RemoteViews, settings: UserSettings) {
        val pattern = if (settings.use24Hour) "HH:mm" else "h:mm a"
        views.setCharSequence(R.id.widget_time, "setFormat12Hour", pattern)
        views.setCharSequence(R.id.widget_time, "setFormat24Hour", pattern)
    }

    private fun buildCompact(
        context: Context,
        settings: UserSettings,
        percent: Int,
        options: Bundle,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_clock_compact)
        val color = (settings.textColorArgb and 0xFFFFFFFFL).toInt()
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40)
        val timeSp = when {
            minH >= 70 -> 32f
            minH >= 50 -> 28f
            else -> 22f
        }
        val battSp = (timeSp * 0.5f).coerceAtLeast(11f)
        applyWidgetTimeFormats(views, settings)
        views.setTextColor(R.id.widget_time, color)
        views.setTextViewTextSize(R.id.widget_time, TypedValue.COMPLEX_UNIT_SP, timeSp)
        views.setTextViewText(R.id.widget_battery, context.getString(R.string.battery_percent, percent))
        views.setTextColor(R.id.widget_battery, color)
        views.setTextViewTextSize(R.id.widget_battery, TypedValue.COMPLEX_UNIT_SP, battSp)
        return views
    }

    private fun buildFull(
        context: Context,
        settings: UserSettings,
        percent: Int,
        charging: Boolean,
        options: Bundle,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_clock_full)
        val color = (settings.textColorArgb and 0xFFFFFFFFL).toInt()
        val cal = Calendar.getInstance()
        val ws = withoutSeconds(settings)
        val dateText = Formatters.formatDate(cal, ws)
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val scale = when {
            minH >= 160 && minW >= 300 -> 1.15f
            minH >= 120 -> 1.0f
            else -> 0.85f
        }
        views.setTextViewText(R.id.widget_date, dateText)
        views.setTextColor(R.id.widget_date, color)
        views.setTextViewTextSize(R.id.widget_date, TypedValue.COMPLEX_UNIT_SP, 14f * scale)
        applyWidgetTimeFormats(views, settings)
        views.setTextColor(R.id.widget_time, color)
        views.setTextViewTextSize(R.id.widget_time, TypedValue.COMPLEX_UNIT_SP, 36f * scale)
        views.setTextViewText(R.id.widget_battery, context.getString(R.string.battery_percent, percent))
        views.setTextColor(R.id.widget_battery, color)
        views.setTextViewTextSize(R.id.widget_battery, TypedValue.COMPLEX_UNIT_SP, 20f * scale)
        views.setViewVisibility(R.id.widget_bolt, if (charging) View.VISIBLE else View.GONE)
        views.setInt(R.id.widget_bolt, "setColorFilter", color)

        val density = context.resources.displayMetrics.density
        val barW = (minW.coerceAtLeast(120) * density).toInt()
        val barH = (18 * density * scale).toInt().coerceAtLeast(12)
        views.setImageViewBitmap(
            R.id.widget_battery_bar,
            drawBatteryBar(barW, barH, percent, color, density * scale),
        )
        return views
    }

    private fun drawBatteryBar(
        widthPx: Int,
        heightPx: Int,
        percent: Int,
        color: Int,
        density: Float,
    ): Bitmap {
        val w = widthPx.coerceAtLeast(80)
        val h = heightPx.coerceAtLeast(10)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val filled = batteryFilledBlocks(percent)
        val gap = 3f * density
        val blockW = (w - gap * 9) / 10f
        val corner = 2.5f * density
        val stroke = (1.5f * density).coerceAtLeast(1f)
        for (i in 0 until 10) {
            val left = i * (blockW + gap)
            val rect = RectF(left, 0f, left + blockW, h.toFloat())
            if (i < filled) {
                paint.style = Paint.Style.FILL
                paint.color = color
                canvas.drawRoundRect(rect, corner, corner, paint)
            } else {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = stroke
                paint.color = color
                val inset = stroke / 2f
                canvas.drawRoundRect(
                    RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset),
                    corner,
                    corner,
                    paint,
                )
            }
        }
        return bmp
    }
}
