package com.kmmm_engineering.chargeclock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kmmm_engineering.chargeclock.R
import com.kmmm_engineering.chargeclock.data.UserSettings
import com.kmmm_engineering.chargeclock.util.Formatters
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
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showSlider by remember { mutableStateOf(false) }
    var shiftX by remember { mutableIntStateOf(0) }
    var shiftY by remember { mutableIntStateOf(0) }

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
        if (showSlider) {
            delay(8_000L)
            showSlider = false
        }
    }

    val cal = remember(now) {
        Calendar.getInstance().apply { timeInMillis = now }
    }
    val dateText = Formatters.formatDate(cal, settings)
    val timeText = Formatters.formatTime(cal, settings)
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
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = timeText,
                color = textColor,
                fontSize = (64 * scale).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.battery_percent, batteryPercent),
                color = textColor,
                fontSize = (36 * scale).sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
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
                    .fillMaxWidth(),
            )
        }
    }
}
