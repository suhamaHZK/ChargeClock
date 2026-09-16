package com.kmmm_engineering.chargeclock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kmmm_engineering.chargeclock.R
import kotlin.math.roundToInt

@Composable
fun UnlockSlider(
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackHeight = 44.dp
    val thumbWidth = 56.dp
    val thumbHeight = 36.dp
    val shape = RoundedCornerShape(22.dp)
    var offsetX by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF3A3A3A), Color(0xFF2A2A2A))
                )
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        val maxOffsetPx = with(density) { (maxWidth - thumbWidth - 8.dp).toPx() }.coerceAtLeast(1f)
        val completeThreshold = maxOffsetPx * 0.85f

        Text(
            text = stringResource(R.string.slide_to_settings),
            color = Color(0xFFBBBBBB),
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.Center),
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .size(width = thumbWidth, height = thumbHeight)
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .pointerInput(maxOffsetPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX >= completeThreshold) {
                                onUnlocked()
                            }
                            offsetX = 0f
                        },
                        onDragCancel = { offsetX = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount).coerceIn(0f, maxOffsetPx)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF111111),
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
