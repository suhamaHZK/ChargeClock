package com.kmmm_engineering.chargeclock.ui

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kmmm_engineering.chargeclock.R
import com.kmmm_engineering.chargeclock.data.ClockTheme
import com.kmmm_engineering.chargeclock.data.DisplayScale
import com.kmmm_engineering.chargeclock.data.UserSettings
import com.kmmm_engineering.chargeclock.ui.theme.ClockFonts
import com.kmmm_engineering.chargeclock.ui.theme.ThemedClockText
import com.kmmm_engineering.chargeclock.util.Formatters
import com.kmmm_engineering.chargeclock.util.TimeParts
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Outer padding around the clock column (matches Column padding below). */
private val ClockContentPadding = 24.dp

/**
 * Extra guard so OLED pixel-shift (±12px) does not clip after fit.
 * Combined with [ClockContentPadding] when computing available space.
 */
private val OledShiftGuard = 16.dp

@Composable
fun ClockScreen(
    settings: UserSettings,
    batteryPercent: Int,
    isCharging: Boolean,
    onSingleTap: () -> Unit,
    onOpenSettings: () -> Unit,
    onUnlockSliderVisibilityChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showSlider by remember { mutableStateOf(false) }
    // Lost-user temporary hint: show double_tap_hint for 5s after >=5 taps in 60s.
    val recentTapAts = remember { ArrayDeque<Long>() }
    var lostUserHintUntil by remember { mutableLongStateOf(0L) }
    var shiftX by remember { mutableIntStateOf(0) }
    var shiftY by remember { mutableIntStateOf(0) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Align updates to the next second (or minute when seconds are hidden).
    LaunchedEffect(settings.showSeconds) {
        while (true) {
            now = System.currentTimeMillis()
            val period = if (settings.showSeconds) 1_000L else 60_000L
            val rem = now % period
            val wait = if (rem == 0L) period else period - rem
            delay(wait.coerceAtLeast(1L))
        }
    }

    // OLED pixel shift every ~3 minutes, a few to ~15 px
    LaunchedEffect(Unit) {
        while (true) {
            delay(3 * 60 * 1000L)
            shiftX = Random.nextInt(-12, 13)
            shiftY = Random.nextInt(-12, 13)
        }
    }

    // Auto-hide slider after inactivity
    LaunchedEffect(showSlider) {
        onUnlockSliderVisibilityChange(showSlider)
        if (showSlider) {
            delay(8_000L)
            showSlider = false
        }
    }

    // Clear temporary lost-user hint when its window ends.
    LaunchedEffect(lostUserHintUntil) {
        val until = lostUserHintUntil
        if (until <= 0L) return@LaunchedEffect
        val remaining = until - System.currentTimeMillis()
        if (remaining > 0L) delay(remaining)
        if (lostUserHintUntil == until) {
            lostUserHintUntil = 0L
        }
    }

    val cal = remember(now) {
        Calendar.getInstance().apply { timeInMillis = now }
    }
    val dateText = Formatters.formatDate(cal, settings)
    val timeParts = Formatters.formatTimeParts(cal, settings)
    val textColor = Color(settings.textColorArgb)
    val userScale = settings.displayScale.factor
    val percentLabel = stringResource(R.string.battery_percent, batteryPercent)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (showSlider) {
                            showSlider = false
                        } else {
                            onSingleTap()
                            val tapAt = System.currentTimeMillis()
                            recentTapAts.addLast(tapAt)
                            while (recentTapAts.isNotEmpty() && tapAt - recentTapAts.first() > 60_000L) {
                                recentTapAts.removeFirst()
                            }
                            if (recentTapAts.size >= 5) {
                                // Extend 5s from now if already visible.
                                lostUserHintUntil = tapAt + 5_000L
                            }
                        }
                    },
                    onDoubleTap = {
                        // Open unlock slider only; do not mark settings-opened.
                        showSlider = true
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val fitScale = remember(
                settings.displayScale,
                settings.clockTheme,
                settings.use24Hour,
                settings.showSeconds,
                dateText,
                timeParts,
                isCharging,
                isLandscape,
                percentLabel,
                maxWidth,
                maxHeight,
                textMeasurer,
                density,
            ) {
                computeClockFitScale(
                    textMeasurer = textMeasurer,
                    density = density,
                    settings = settings,
                    dateText = dateText,
                    parts = timeParts,
                    isCharging = isCharging,
                    isLandscape = isLandscape,
                    userScale = userScale,
                    percentLabel = percentLabel,
                    maxWidth = maxWidth,
                    maxHeight = maxHeight,
                )
            }
            val scale = userScale * fitScale

            Column(
                modifier = Modifier
                    .offset { IntOffset(shiftX, shiftY) }
                    .padding(ClockContentPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ThemedClockText(
                    text = dateText,
                    theme = settings.clockTheme,
                    color = textColor,
                    fontSize = (22 * scale).sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.height(8.dp))
                ClockTimeBlock(
                    parts = timeParts,
                    settings = settings,
                    isLandscape = isLandscape,
                    textColor = textColor,
                    scale = scale,
                )
                Spacer(Modifier.height(12.dp))
                BatteryStatusBlock(
                    percent = batteryPercent,
                    isCharging = isCharging,
                    textColor = textColor,
                    scale = scale,
                    theme = settings.clockTheme,
                    percentLabel = percentLabel,
                )
            }
        }

        val showPersistentHint = !settings.settingsOpenedOnce && !showSlider
        val showLostUserHint = !showSlider && lostUserHintUntil > System.currentTimeMillis()
        if (showPersistentHint || showLostUserHint) {
            Text(
                text = stringResource(R.string.double_tap_hint),
                color = Color(0xFFAAAAAA),
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
            )
        }

        if (showSlider) {
            UnlockSlider(
                onUnlocked = {
                    showSlider = false
                    onOpenSettings()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 32.dp, vertical = 40.dp)
                    // Landscape: ~half width (centered). Portrait: full width within padding.
                    .fillMaxWidth(if (isLandscape) 0.5f else 1f),
            )
        }
    }
}

/**
 * Fit factor ≤ 1 so the clock column (date + time + battery) fits in available space.
 * Uses min(widthFit, heightFit); portrait typically binds on width, landscape on height.
 */
private fun computeClockFitScale(
    textMeasurer: TextMeasurer,
    density: Density,
    settings: UserSettings,
    dateText: String,
    parts: TimeParts,
    isCharging: Boolean,
    isLandscape: Boolean,
    userScale: Float,
    percentLabel: String,
    maxWidth: Dp,
    maxHeight: Dp,
): Float = with(density) {
    val availW = (maxWidth - ClockContentPadding * 2 - OledShiftGuard)
        .toPx()
        .coerceAtLeast(1f)
    val availH = (maxHeight - ClockContentPadding * 2 - OledShiftGuard)
        .toPx()
        .coerceAtLeast(1f)
    val (contentW, contentH) = estimateClockContentPx(
        textMeasurer = textMeasurer,
        density = this,
        settings = settings,
        dateText = dateText,
        parts = parts,
        isCharging = isCharging,
        isLandscape = isLandscape,
        scale = userScale,
        percentLabel = percentLabel,
    )
    min(1f, min(availW / contentW.coerceAtLeast(1f), availH / contentH.coerceAtLeast(1f)))
}

/**
 * Intrinsic width/height of the clock column at [scale] (before fit), matching layout spacers.
 */
private fun estimateClockContentPx(
    textMeasurer: TextMeasurer,
    density: Density,
    settings: UserSettings,
    dateText: String,
    parts: TimeParts,
    isCharging: Boolean,
    isLandscape: Boolean,
    scale: Float,
    percentLabel: String,
): Pair<Float, Float> = with(density) {
    val theme = settings.clockTheme
    val displayScale = settings.displayScale
    val isLargePlus = displayScale == DisplayScale.LARGE || displayScale == DisplayScale.XLARGE
    val isNormalPlus = displayScale != DisplayScale.SMALL
    val is12h = !settings.use24Hour
    val showSeconds = parts.seconds != null

    val secondsOnOwnLine = !isLandscape && showSeconds && isLargePlus
    val amPmOnOwnLine = is12h && !isLandscape && (
        isLargePlus || (isNormalPlus && showSeconds)
    )

    val timeScale = clockTimeScale(
        baseScale = scale,
        is12h = is12h,
        showSeconds = showSeconds,
        isLandscape = isLandscape,
        isLargePlus = isLargePlus,
    )

    // Date: CLASSIC uses Dseg14 (letters); SEG14 uses Dseg14; DEFAULT system.
    val dateSize = measureClockText(
        textMeasurer = textMeasurer,
        text = dateText,
        theme = theme,
        fontSize = (22 * scale).sp,
        fontWeight = FontWeight.Medium,
        classicFamily = ClockFonts.Dseg14,
    )

    val mainSize = (64 * timeScale).sp
    val secondsSize = if (secondsOnOwnLine) (40 * timeScale).sp else mainSize
    val amPmSize = (22 * timeScale).sp

    val hmSize = measureClockText(
        textMeasurer = textMeasurer,
        text = parts.hourMinute,
        theme = theme,
        fontSize = mainSize,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        classicFamily = ClockFonts.Dseg7,
    )

    val colonSecondsSize = if (!secondsOnOwnLine && parts.seconds != null) {
        measureClockText(
            textMeasurer = textMeasurer,
            text = ":${parts.seconds}",
            theme = theme,
            fontSize = secondsSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            classicFamily = ClockFonts.Dseg7,
        )
    } else {
        null
    }

    val inlineAmPmSize = if (!amPmOnOwnLine && parts.amPm != null) {
        measureClockText(
            textMeasurer = textMeasurer,
            text = parts.amPm,
            theme = theme,
            fontSize = amPmSize,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            classicFamily = ClockFonts.Dseg14,
        )
    } else {
        null
    }

    val ownAmPmSize = if (amPmOnOwnLine && parts.amPm != null) {
        measureClockText(
            textMeasurer = textMeasurer,
            text = parts.amPm,
            theme = theme,
            fontSize = amPmSize,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            classicFamily = ClockFonts.Dseg14,
        )
    } else {
        null
    }

    val ownSecondsSize = if (secondsOnOwnLine && parts.seconds != null) {
        measureClockText(
            textMeasurer = textMeasurer,
            text = parts.seconds,
            theme = theme,
            fontSize = secondsSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            classicFamily = ClockFonts.Dseg7,
        )
    } else {
        null
    }

    var digitsRowW = hmSize.width.toFloat()
    var digitsRowH = hmSize.height.toFloat()
    if (colonSecondsSize != null) {
        digitsRowW += colonSecondsSize.width
        digitsRowH = max(digitsRowH, colonSecondsSize.height.toFloat())
    }
    if (inlineAmPmSize != null) {
        digitsRowW += 10.dp.toPx() + inlineAmPmSize.width
        digitsRowH = max(digitsRowH, inlineAmPmSize.height.toFloat())
    }

    var timeW = digitsRowW
    var timeH = digitsRowH
    if (ownAmPmSize != null) {
        timeW = max(timeW, ownAmPmSize.width.toFloat())
        timeH += ownAmPmSize.height + 2.dp.toPx()
    }
    if (ownSecondsSize != null) {
        timeW = max(timeW, ownSecondsSize.width.toFloat())
        timeH += 2.dp.toPx() + ownSecondsSize.height
    }

    // Battery percent (+ optional bolt) and 10 blocks.
    val pctSize = measureClockText(
        textMeasurer = textMeasurer,
        text = percentLabel,
        theme = theme,
        fontSize = (36 * scale).sp,
        fontWeight = FontWeight.SemiBold,
        // Mixed digits + '%': classicAnnotated via null override.
        classicFamily = null,
    )
    val boltW = if (isCharging) (22 * scale).dp.toPx() + (6 * scale).dp.toPx() else 0f
    val boltH = if (isCharging) (22 * scale).dp.toPx() else 0f
    val pctRowW = boltW + pctSize.width
    val pctRowH = max(pctSize.height.toFloat(), boltH)
    val blocksW = (10 * (16 * scale) + 9 * (5 * scale)).dp.toPx()
    val blocksH = (28 * scale).dp.toPx()
    val batteryW = max(pctRowW, blocksW)
    val batteryH = pctRowH + (10 * scale).dp.toPx() + blocksH

    val contentW = maxOf(dateSize.width.toFloat(), timeW, batteryW)
    val contentH = dateSize.height + 8.dp.toPx() + timeH + 12.dp.toPx() + batteryH
    contentW to contentH
}

/** Mild shrink multipliers for packing 12h / landscape — mirrors [ClockTimeBlock]. */
private fun clockTimeScale(
    baseScale: Float,
    is12h: Boolean,
    showSeconds: Boolean,
    isLandscape: Boolean,
    isLargePlus: Boolean,
): Float = when {
    is12h && showSeconds && isLandscape -> baseScale * 0.82f
    is12h && showSeconds -> baseScale * 0.90f
    is12h && isLargePlus && isLandscape -> baseScale * 0.88f
    is12h && isLargePlus -> baseScale * 0.92f
    showSeconds && isLandscape && isLargePlus -> baseScale * 0.90f
    else -> baseScale
}

/**
 * Measure with the same font families [ThemedClockText] uses.
 * For CLASSIC_DIGITAL, [classicFamily] selects Dseg7 (digits/:) or Dseg14 (letters);
 * null uses [ClockFonts.classicAnnotated] for mixed strings (e.g. battery %).
 */
private fun measureClockText(
    textMeasurer: TextMeasurer,
    text: String,
    theme: ClockTheme,
    fontSize: TextUnit,
    fontWeight: FontWeight? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    classicFamily: FontFamily? = null,
): androidx.compose.ui.unit.IntSize {
    val base = TextStyle(
        fontSize = fontSize,
        letterSpacing = letterSpacing,
    )
    return when (theme) {
        ClockTheme.DEFAULT -> textMeasurer.measure(
            text = text,
            style = base.copy(fontWeight = fontWeight),
            softWrap = false,
            maxLines = 1,
        ).size
        ClockTheme.SEG14_DIGITAL -> textMeasurer.measure(
            text = text,
            style = base.copy(fontFamily = ClockFonts.Dseg14),
            softWrap = false,
            maxLines = 1,
        ).size
        ClockTheme.CLASSIC_DIGITAL -> {
            if (classicFamily != null) {
                textMeasurer.measure(
                    text = text,
                    style = base.copy(fontFamily = classicFamily),
                    softWrap = false,
                    maxLines = 1,
                ).size
            } else {
                textMeasurer.measure(
                    text = ClockFonts.classicAnnotated(text),
                    style = base,
                    softWrap = false,
                    maxLines = 1,
                ).size
            }
        }
    }
}

/**
 * Portrait Large/XL + seconds → seconds on their own line.
 * Portrait Large/XL + 12h, or Normal+ with 12h+seconds → AM/PM separated (own line).
 * Landscape stays compact (Row) with mild font shrink so digits do not wrap/overlap.
 *
 * [scale] is the already-fitted visual scale (user DisplayScale.factor × fit ≤ 1).
 * Line-break rules still key off [UserSettings.displayScale] enum, not the float.
 */
@Composable
private fun ClockTimeBlock(
    parts: TimeParts,
    settings: UserSettings,
    isLandscape: Boolean,
    textColor: Color,
    scale: Float,
) {
    val displayScale = settings.displayScale
    val baseScale = scale
    val isLargePlus = displayScale == DisplayScale.LARGE || displayScale == DisplayScale.XLARGE
    val isNormalPlus = displayScale != DisplayScale.SMALL
    val is12h = !settings.use24Hour
    val showSeconds = parts.seconds != null

    val secondsOnOwnLine = !isLandscape && showSeconds && isLargePlus
    val amPmOnOwnLine = is12h && !isLandscape && (
        isLargePlus || (isNormalPlus && showSeconds)
    )

    val timeScale = clockTimeScale(
        baseScale = baseScale,
        is12h = is12h,
        showSeconds = showSeconds,
        isLandscape = isLandscape,
        isLargePlus = isLargePlus,
    )

    val mainSize = (64 * timeScale).sp
    val secondsSize = if (secondsOnOwnLine) (40 * timeScale).sp else mainSize
    val amPmSize = (22 * timeScale).sp

    if (amPmOnOwnLine || secondsOnOwnLine) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (amPmOnOwnLine && parts.amPm != null) {
                ThemedClockText(
                    text = parts.amPm,
                    theme = settings.clockTheme,
                    color = textColor,
                    fontSize = amPmSize,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(2.dp))
            }
            TimeDigitsRow(
                hourMinute = parts.hourMinute,
                seconds = if (secondsOnOwnLine) null else parts.seconds,
                amPm = if (amPmOnOwnLine) null else parts.amPm,
                mainSize = mainSize,
                secondsSize = secondsSize,
                amPmSize = amPmSize,
                textColor = textColor,
                theme = settings.clockTheme,
            )
            if (secondsOnOwnLine && parts.seconds != null) {
                Spacer(Modifier.height(2.dp))
                ThemedClockText(
                    text = parts.seconds,
                    theme = settings.clockTheme,
                    color = textColor,
                    fontSize = secondsSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    letterSpacing = 1.sp,
                )
            }
        }
    } else {
        // Compact single row (small portrait, or landscape, or simple 24h).
        TimeDigitsRow(
            hourMinute = parts.hourMinute,
            seconds = parts.seconds,
            amPm = parts.amPm,
            mainSize = mainSize,
            secondsSize = mainSize,
            amPmSize = amPmSize,
            textColor = textColor,
            theme = settings.clockTheme,
        )
    }
}

@Composable
private fun TimeDigitsRow(
    hourMinute: String,
    seconds: String?,
    amPm: String?,
    mainSize: TextUnit,
    secondsSize: TextUnit,
    amPmSize: TextUnit,
    textColor: Color,
    theme: ClockTheme,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        ThemedClockText(
            text = hourMinute,
            theme = theme,
            color = textColor,
            fontSize = mainSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            letterSpacing = 1.sp,
        )
        if (seconds != null) {
            ThemedClockText(
                text = ":$seconds",
                theme = theme,
                color = textColor,
                fontSize = secondsSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                letterSpacing = 1.sp,
            )
        }
        if (amPm != null) {
            Spacer(Modifier.width(10.dp))
            ThemedClockText(
                text = amPm,
                theme = theme,
                color = textColor,
                fontSize = amPmSize,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                letterSpacing = 1.sp,
            )
        }
    }
}


/** floor(level/10) filled; 100% → 10 filled; 0–9% → 0 filled. */
internal fun batteryFilledBlocks(percent: Int): Int {
    val level = percent.coerceIn(0, 100)
    return if (level >= 100) 10 else level / 10
}

@Composable
private fun BatteryStatusBlock(
    percent: Int,
    isCharging: Boolean,
    textColor: Color,
    scale: Float,
    theme: ClockTheme,
    percentLabel: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (isCharging) {
                LightningBoltIcon(
                    color = textColor,
                    modifier = Modifier
                        .size((22 * scale).dp)
                        .semantics { contentDescription = "charging" },
                )
                Spacer(Modifier.width((6 * scale).dp))
            }
            ThemedClockText(
                text = percentLabel,
                theme = theme,
                color = textColor,
                fontSize = (36 * scale).sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
        }
        Spacer(Modifier.height((10 * scale).dp))
        BatteryBlocksRow(
            percent = percent,
            color = textColor,
            scale = scale,
        )
    }
}

@Composable
private fun LightningBoltIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val bolt = Path().apply {
            moveTo(w * 0.58f, 0f)
            lineTo(w * 0.18f, h * 0.55f)
            lineTo(w * 0.46f, h * 0.55f)
            lineTo(w * 0.38f, h)
            lineTo(w * 0.86f, h * 0.40f)
            lineTo(w * 0.52f, h * 0.40f)
            close()
        }
        drawPath(bolt, color)
    }
}

@Composable
private fun BatteryBlocksRow(
    percent: Int,
    color: Color,
    scale: Float,
) {
    val filled = batteryFilledBlocks(percent)
    val blockW: Dp = (16 * scale).dp
    val blockH: Dp = (28 * scale).dp
    val gap: Dp = (5 * scale).dp
    val stroke: Dp = (1.5f * scale).coerceAtLeast(1f).dp
    // Slight round (~2.5dp * scale ≈ 15% of block width) — vector Canvas, not glyphs.
    val corner: Dp = (2.5f * scale).dp
    Row(
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(10) { index ->
            val solid = index < filled
            Canvas(modifier = Modifier.size(width = blockW, height = blockH)) {
                val cornerPx = corner.toPx()
                val radii = CornerRadius(cornerPx, cornerPx)
                if (solid) {
                    drawRoundRect(
                        color = color,
                        cornerRadius = radii,
                    )
                } else {
                    val strokePx = stroke.toPx()
                    val inset = strokePx / 2f
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(inset, inset),
                        size = Size(this.size.width - strokePx, this.size.height - strokePx),
                        cornerRadius = radii,
                        style = Stroke(width = strokePx),
                    )
                }
            }
        }
    }
}
