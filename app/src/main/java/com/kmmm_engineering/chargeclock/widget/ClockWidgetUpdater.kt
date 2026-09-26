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
import com.kmmm_engineering.chargeclock.data.ClockTheme
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
 * **DEFAULT theme:** time uses [android.widget.TextClock] so minutes flip in the
 * widget host without the app process. App [UserSettings.use24Hour] is forced by
 * setting both format12Hour and format24Hour to the same pattern.
 *
 * **CLASSIC_DIGITAL / SEG14_DIGITAL:** custom DSEG fonts cannot be applied via
 * `android:fontFamily` on RemoteViews. Time / date / battery (/ AM/PM) are drawn
 * as Bitmaps into ImageViews via [WidgetTextBitmap]. These digital widgets follow
 * the alarm cadence ([WidgetAlarmScheduler] at each :00 boundary, plus
 * [WidgetTimeTickRegistrar] TIME_TICK while the process is alive) — not TextClock
 * host ticks. Bitmap time is redrawn on every update so it stays in sync with
 * those alarms. Do not use TextView-for-time as the primary clock on digital themes
 * (minute skew vs OS was observed).
 *
 * Update cadence (minSdk 29) — date / battery / Discord / digital time:
 * - android:updatePeriodMillis is floored to ~30 minutes by the system — safety net only.
 * - Primary (cold process): [WidgetAlarmScheduler] — interactive screen →
 *   AlarmManager.setExact at each minute boundary; screen off → setAlarmClock
 *   (Doze-safe) with while-idle fallbacks. Some OEMs delay minute setAlarmClock
 *   (~20–35s); interactive setExact is the on-time path for DSEG. Also refreshes
 *   on TIME_CHANGED / TIMEZONE_CHANGED / BOOT_COMPLETED and settings via [updateAll].
 * - While the process is alive, [WidgetTimeTickRegistrar] listens for
 *   ACTION_TIME_TICK and arms a Handler to the next minute boundary (precision
 *   backup when AlarmManager is late). Both run updateAll off-main.
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

    private fun isDigitalTheme(theme: ClockTheme): Boolean =
        theme == ClockTheme.CLASSIC_DIGITAL || theme == ClockTheme.SEG14_DIGITAL

    private fun compactLayoutRes(theme: ClockTheme): Int = when (theme) {
        ClockTheme.DEFAULT -> R.layout.widget_clock_compact
        ClockTheme.CLASSIC_DIGITAL -> R.layout.widget_clock_compact_classic
        ClockTheme.SEG14_DIGITAL -> R.layout.widget_clock_compact_seg14
    }

    private fun fullLayoutRes(theme: ClockTheme): Int = when (theme) {
        ClockTheme.DEFAULT -> R.layout.widget_clock_full
        ClockTheme.CLASSIC_DIGITAL -> R.layout.widget_clock_full_classic
        ClockTheme.SEG14_DIGITAL -> R.layout.widget_clock_full_seg14
    }

    private fun spToPx(context: Context, sp: Float): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            context.resources.displayMetrics,
        )

    /**
     * DEFAULT only: force app 12/24 preference onto TextClock.
     * Must not be called for digital themes (widget_time is an ImageView).
     */
    private fun applyWidgetTimeFormats(views: RemoteViews, settings: UserSettings) {
        if (settings.clockTheme != ClockTheme.DEFAULT) return
        val pattern = if (settings.use24Hour) "HH:mm" else "h:mm a"
        views.setCharSequence(R.id.widget_time, "setFormat12Hour", pattern)
        views.setCharSequence(R.id.widget_time, "setFormat24Hour", pattern)
    }

    /** Classic digital: AM/PM as DSEG14 bitmap, or hide for 24h. */
    private fun applyClassicAmPmBitmap(
        context: Context,
        views: RemoteViews,
        settings: UserSettings,
        amPm: String?,
        amPmSp: Float,
        color: Int,
    ) {
        if (settings.clockTheme != ClockTheme.CLASSIC_DIGITAL) return
        if (amPm == null || settings.use24Hour) {
            views.setViewVisibility(R.id.widget_ampm, View.GONE)
            return
        }
        val bmp = WidgetTextBitmap.createTextBitmap(
            text = amPm,
            typeface = WidgetTextBitmap.typefaceDseg14(context),
            textSizePx = spToPx(context, amPmSp),
            color = color,
        )
        views.setImageViewBitmap(R.id.widget_ampm, bmp)
        views.setViewVisibility(R.id.widget_ampm, View.VISIBLE)
    }

    /**
     * Draw time (/ AM/PM) bitmaps for digital themes. Redrawn on every widget update
     * so displayed time matches [WidgetAlarmScheduler] / TIME_TICK cadence.
     */
    private fun applyDigitalTimeBitmaps(
        context: Context,
        views: RemoteViews,
        settings: UserSettings,
        timeSp: Float,
        amPmSp: Float,
        color: Int,
    ) {
        val cal = Calendar.getInstance()
        val parts = Formatters.formatTimeParts(cal, withoutSeconds(settings))
        val timePx = spToPx(context, timeSp)
        when (settings.clockTheme) {
            ClockTheme.CLASSIC_DIGITAL -> {
                // Digits + ':' only → DSEG7 (AM/PM is a separate ImageView).
                val bmp = WidgetTextBitmap.createTextBitmap(
                    text = parts.hourMinute,
                    typeface = WidgetTextBitmap.typefaceDseg7(context),
                    textSizePx = timePx,
                    color = color,
                )
                views.setImageViewBitmap(R.id.widget_time, bmp)
                applyClassicAmPmBitmap(context, views, settings, parts.amPm, amPmSp, color)
            }
            ClockTheme.SEG14_DIGITAL -> {
                // Match prior TextClock "h:mm a" / "HH:mm": AM/PM included in time string.
                val timeStr = if (parts.amPm != null) {
                    "${parts.hourMinute} ${parts.amPm}"
                } else {
                    parts.hourMinute
                }
                val bmp = WidgetTextBitmap.createTextBitmap(
                    text = timeStr,
                    typeface = WidgetTextBitmap.typefaceDseg14(context),
                    textSizePx = timePx,
                    color = color,
                )
                views.setImageViewBitmap(R.id.widget_time, bmp)
            }
            ClockTheme.DEFAULT -> Unit
        }
    }

    private fun digitalBatteryBitmap(
        context: Context,
        theme: ClockTheme,
        percent: Int,
        textSizePx: Float,
        color: Int,
    ): Bitmap {
        val text = context.getString(R.string.battery_percent, percent)
        return when (theme) {
            ClockTheme.CLASSIC_DIGITAL ->
                WidgetTextBitmap.createMixedClassicBitmap(context, text, textSizePx, color)
            ClockTheme.SEG14_DIGITAL ->
                WidgetTextBitmap.createTextBitmap(
                    text,
                    WidgetTextBitmap.typefaceDseg14(context),
                    textSizePx,
                    color,
                )
            ClockTheme.DEFAULT ->
                error("digitalBatteryBitmap is for digital themes only")
        }
    }

    private fun digitalDateBitmap(
        context: Context,
        settings: UserSettings,
        textSizePx: Float,
        color: Int,
    ): Bitmap {
        val cal = Calendar.getInstance()
        val dateText = Formatters.formatDate(cal, withoutSeconds(settings))
        return when (settings.clockTheme) {
            ClockTheme.CLASSIC_DIGITAL ->
                WidgetTextBitmap.createMixedClassicBitmap(context, dateText, textSizePx, color)
            ClockTheme.SEG14_DIGITAL ->
                WidgetTextBitmap.createTextBitmap(
                    dateText,
                    WidgetTextBitmap.typefaceDseg14(context),
                    textSizePx,
                    color,
                )
            ClockTheme.DEFAULT ->
                error("digitalDateBitmap is for digital themes only")
        }
    }

    private fun buildCompact(
        context: Context,
        settings: UserSettings,
        percent: Int,
        options: Bundle,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, compactLayoutRes(settings.clockTheme))
        val color = (settings.textColorArgb and 0xFFFFFFFFL).toInt()
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40)
        val timeSp = when {
            minH >= 70 -> 32f
            minH >= 50 -> 28f
            else -> 22f
        }
        val battSp = (timeSp * 0.5f).coerceAtLeast(11f)
        val amPmSp = (timeSp * 0.45f).coerceAtLeast(10f)

        if (isDigitalTheme(settings.clockTheme)) {
            applyDigitalTimeBitmaps(context, views, settings, timeSp, amPmSp, color)
            views.setImageViewBitmap(
                R.id.widget_battery,
                digitalBatteryBitmap(
                    context,
                    settings.clockTheme,
                    percent,
                    spToPx(context, battSp),
                    color,
                ),
            )
        } else {
            applyWidgetTimeFormats(views, settings)
            views.setTextColor(R.id.widget_time, color)
            views.setTextViewTextSize(R.id.widget_time, TypedValue.COMPLEX_UNIT_SP, timeSp)
            views.setTextViewText(R.id.widget_battery, context.getString(R.string.battery_percent, percent))
            views.setTextColor(R.id.widget_battery, color)
            views.setTextViewTextSize(R.id.widget_battery, TypedValue.COMPLEX_UNIT_SP, battSp)
        }
        return views
    }

    private fun buildFull(
        context: Context,
        settings: UserSettings,
        percent: Int,
        charging: Boolean,
        options: Bundle,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, fullLayoutRes(settings.clockTheme))
        val color = (settings.textColorArgb and 0xFFFFFFFFL).toInt()
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val scale = when {
            minH >= 160 && minW >= 300 -> 1.15f
            minH >= 120 -> 1.0f
            else -> 0.85f
        }
        val dateSp = 14f * scale
        val timeSp = 36f * scale
        val amPmSp = 18f * scale
        val battSp = 20f * scale

        if (isDigitalTheme(settings.clockTheme)) {
            views.setImageViewBitmap(
                R.id.widget_date,
                digitalDateBitmap(context, settings, spToPx(context, dateSp), color),
            )
            applyDigitalTimeBitmaps(context, views, settings, timeSp, amPmSp, color)
            views.setImageViewBitmap(
                R.id.widget_battery,
                digitalBatteryBitmap(
                    context,
                    settings.clockTheme,
                    percent,
                    spToPx(context, battSp),
                    color,
                ),
            )
        } else {
            val cal = Calendar.getInstance()
            val dateText = Formatters.formatDate(cal, withoutSeconds(settings))
            views.setTextViewText(R.id.widget_date, dateText)
            views.setTextColor(R.id.widget_date, color)
            views.setTextViewTextSize(R.id.widget_date, TypedValue.COMPLEX_UNIT_SP, dateSp)
            applyWidgetTimeFormats(views, settings)
            views.setTextColor(R.id.widget_time, color)
            views.setTextViewTextSize(R.id.widget_time, TypedValue.COMPLEX_UNIT_SP, timeSp)
            views.setTextViewText(R.id.widget_battery, context.getString(R.string.battery_percent, percent))
            views.setTextColor(R.id.widget_battery, color)
            views.setTextViewTextSize(R.id.widget_battery, TypedValue.COMPLEX_UNIT_SP, battSp)
        }

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
