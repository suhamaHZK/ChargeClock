package com.kmmm_engineering.chargeclock.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.kmmm_engineering.chargeclock.R
import com.kmmm_engineering.chargeclock.ui.theme.ClockFonts
import kotlin.math.ceil
import kotlin.math.max

/**
 * Renders widget clock strings to Bitmaps with DSEG typefaces.
 *
 * RemoteViews cannot apply custom [android:fontFamily] reliably on home-screen
 * widgets, so CLASSIC_DIGITAL / SEG14_DIGITAL themes draw into ImageViews instead.
 * Digital widgets follow [WidgetAlarmScheduler] / TIME_TICK cadence (not TextClock
 * host ticks); Bitmap time is redrawn on every [ClockWidgetUpdater] pass.
 */
object WidgetTextBitmap {

    fun typefaceDseg7(context: Context): Typeface =
        ResourcesCompat.getFont(context, R.font.dseg7_classic_mini_bold_italic_file)
            ?: Typeface.DEFAULT_BOLD

    fun typefaceDseg14(context: Context): Typeface =
        ResourcesCompat.getFont(context, R.font.dseg14_classic_mini_italic_file)
            ?: Typeface.DEFAULT

    /**
     * Single-typeface text bitmap. Pads for DSEG italic overhang / unusual metrics
     * so glyphs are not clipped at the edges.
     */
    fun createTextBitmap(
        text: String,
        typeface: Typeface,
        textSizePx: Float,
        color: Int,
    ): Bitmap {
        if (text.isEmpty()) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        val paint = basePaint(typeface, textSizePx, color)
        val fm = paint.fontMetrics
        val textW = paint.measureText(text)
        // Italic DSEG glyphs overhang past measureText advance; pad generously.
        val padX = max(2f, textSizePx * 0.2f)
        val padY = max(2f, textSizePx * 0.08f)
        val width = ceil(textW + padX * 2f).toInt().coerceAtLeast(1)
        val height = ceil(-fm.ascent + fm.descent + padY * 2f).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val baseline = padY - fm.ascent
        canvas.drawText(text, padX, baseline, paint)
        return bmp
    }

    /**
     * Classic mixed styling: digits and ':' → DSEG7; other chars → DSEG14
     * (mirrors [ClockFonts.classicAnnotated]).
     */
    fun createMixedClassicBitmap(
        context: Context,
        text: String,
        textSizePx: Float,
        color: Int,
    ): Bitmap {
        if (text.isEmpty()) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        val paint7 = basePaint(typefaceDseg7(context), textSizePx, color)
        val paint14 = basePaint(typefaceDseg14(context), textSizePx, color)
        val fm7 = paint7.fontMetrics
        val fm14 = paint14.fontMetrics
        val ascent = minOf(fm7.ascent, fm14.ascent)
        val descent = maxOf(fm7.descent, fm14.descent)

        var textW = 0f
        val advances = FloatArray(text.length)
        for (i in text.indices) {
            val paint = if (ClockFonts.isClassicSevenSegChar(text[i])) paint7 else paint14
            advances[i] = paint.measureText(text, i, i + 1)
            textW += advances[i]
        }

        val padX = max(2f, textSizePx * 0.2f)
        val padY = max(2f, textSizePx * 0.08f)
        val width = ceil(textW + padX * 2f).toInt().coerceAtLeast(1)
        val height = ceil(-ascent + descent + padY * 2f).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val baseline = padY - ascent
        var x = padX
        for (i in text.indices) {
            val paint = if (ClockFonts.isClassicSevenSegChar(text[i])) paint7 else paint14
            canvas.drawText(text, i, i + 1, x, baseline, paint)
            x += advances[i]
        }
        return bmp
    }

    private fun basePaint(typeface: Typeface, textSizePx: Float, color: Int): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.typeface = typeface
            this.textSize = textSizePx
            this.color = color
            this.style = Paint.Style.FILL
        }
}
