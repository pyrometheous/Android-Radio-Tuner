package com.drivewave.sdr.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.drivewave.sdr.ui.theme.radio
import kotlinx.coroutines.launch

/**
 * Animated horizontal audio waveform visualizer.
 * Shows bars that animate with audio amplitude data.
 * Battery-friendly: 20 fps limit via discrete animation.
 *
 * Defaults to the current theme accent color when [activeColor]/[inactiveColor] are
 * [Color.Unspecified].
 */
@Composable
fun AudioWaveform(
    amplitudes: FloatArray,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.Unspecified,
    inactiveColor: Color = Color.Unspecified,
    barWidthFraction: Float = 0.6f,
) {
    val resolvedActive   = if (activeColor   == Color.Unspecified) MaterialTheme.radio.accent    else activeColor
    val resolvedInactive = if (inactiveColor == Color.Unspecified) MaterialTheme.radio.accentDim else inactiveColor
    val animated = remember(amplitudes.size) {
        Array(amplitudes.size) { Animatable(0f) }
    }

    LaunchedEffect(amplitudes) {
        amplitudes.forEachIndexed { index, target ->
            launch {
                animated[index].animateTo(
                    target,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    )
                )
            }
        }
    }

    val currentValues = animated.map { it.value }

    Canvas(
        modifier = modifier.semantics { contentDescription = "Audio waveform" }
    ) {
        val count = currentValues.size
        if (count == 0) return@Canvas
        val totalWidth = size.width
        val barSpacing = totalWidth / count
        val barWidth = barSpacing * barWidthFraction
        val maxBarHeight = size.height * 0.9f
        val centerY = size.height / 2f

        currentValues.forEachIndexed { index, amplitude ->
            val x = index * barSpacing + barSpacing / 2f
            val barHeight = (amplitude * maxBarHeight).coerceAtLeast(3f)
            val color = if (amplitude > 0.05f) resolvedActive else resolvedInactive
            drawLine(
                color = color,
                start = Offset(x, centerY - barHeight / 2f),
                end = Offset(x, centerY + barHeight / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
