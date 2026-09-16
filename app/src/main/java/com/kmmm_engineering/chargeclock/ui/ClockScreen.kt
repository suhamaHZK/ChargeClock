package com.kmmm_engineering.chargeclock.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kmmm_engineering.chargeclock.R
import com.kmmm_engineering.chargeclock.data.DisplayScale
import com.kmmm_engineering.chargeclock.data.UserSettings
import com.kmmm_engineering.chargeclock.util.Formatters
import com.kmmm_engineering.chargeclock.util.TimeParts
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.random.Random

@Composable
fun ClockScreen(
    settings: UserSettings,
    batteryPercent: Int,
    onSingleTap: () -> Unit,
    onOpenSettings: () -> Unit,
    onHintDismissed: () -> Unit,
    onUnlockSliderVisibilityChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showSlider by remember { mutableStateOf(false) }
    var shiftX by remember { mutableIntStateOf(0) }
    var shiftY by remember { mutableIntStateOf(0) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(settings.showSeconds) {
        while (true) {
            now = System.currentTimeMillis()
            delay(if (settings.showSeconds) 200L else 1000L)
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

    val cal = remember(now) {
        Calendar.getInstance().apply { timeInMillis = now }
    }
    val dateText = Formatters.formatDate(cal, settings)
    val timeParts = Formatters.formatTimeParts(cal, settings)
    val textColor = Color(settings.textColor.argb)
    val scale = settings.displayScale.factor

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
                        }
                    },
                    onDoubleTap = {
                        showSlider = true
                        if (!settings.firstRunHintSeen) {
                            onHintDismissed()
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .offset { IntOffset(shiftX, shiftY) }
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = dateText,
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
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.battery_percent, batteryPercent),
                color = textColor,
                fontSize = (36 * scale).sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
            )
        }

        if (!settings.firstRunHintSeen && !showSlider) {
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
 * Portrait Large/XL + seconds → seconds on their own line.
 * Portrait Large/XL + 12h, or Normal+ with 12h+seconds → AM/PM separated (own line).
 * Landscape stays compact (Row) with mild font shrink so digits do not wrap/overlap.
 */
@Composable
private fun ClockTimeBlock(
    parts: TimeParts,
    settings: UserSettings,
    isLandscape: Boolean,
    textColor: Color,
) {
    val displayScale = settings.displayScale
    val baseScale = displayScale.factor
    val isLargePlus = displayScale == DisplayScale.LARGE || displayScale == DisplayScale.XLARGE
    val isNormalPlus = displayScale != DisplayScale.SMALL
    val is12h = !settings.use24Hour
    val showSeconds = parts.seconds != null

    val secondsOnOwnLine = !isLandscape && showSeconds && isLargePlus
    val amPmOnOwnLine = is12h && !isLandscape && (
        isLargePlus || (isNormalPlus && showSeconds)
    )

    // Mild shrink when packing 12h (+seconds) so landscape/compact rows stay readable.
    val timeScale = when {
        is12h && showSeconds && isLandscape -> baseScale * 0.82f
        is12h && showSeconds -> baseScale * 0.90f
        is12h && isLargePlus && isLandscape -> baseScale * 0.88f
        is12h && isLargePlus -> baseScale * 0.92f
        showSeconds && isLandscape && isLargePlus -> baseScale * 0.90f
        else -> baseScale
    }

    val mainSize = (64 * timeScale).sp
    val secondsSize = if (secondsOnOwnLine) (40 * timeScale).sp else mainSize
    val amPmSize = (22 * timeScale).sp

    if (amPmOnOwnLine || secondsOnOwnLine) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (amPmOnOwnLine && parts.amPm != null) {
                Text(
                    text = parts.amPm,
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
            )
            if (secondsOnOwnLine && parts.seconds != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = parts.seconds,
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
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = hourMinute,
            color = textColor,
            fontSize = mainSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            letterSpacing = 1.sp,
        )
        if (seconds != null) {
            Text(
                text = ":$seconds",
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
            Text(
                text = amPm,
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
