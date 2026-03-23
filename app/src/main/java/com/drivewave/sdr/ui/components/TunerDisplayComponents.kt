package com.drivewave.sdr.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivewave.sdr.ui.theme.AmberPrimary
import com.drivewave.sdr.ui.theme.RadioBlack
import com.drivewave.sdr.ui.theme.TextMuted
import com.drivewave.sdr.ui.theme.radio

/**
 * Large digital segmented-style frequency display.
 * The hero element of the main screen — looks like a car stereo display.
 */
@Composable
fun FrequencyDisplay(
    frequencyText: String,
    bandLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.semantics { contentDescription = "Current frequency: $frequencyText" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Frequency number — 7-segment LED-style digits
        SevenSegmentDisplay(
            text = frequencyText,
            digitHeight = 80.dp,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        // Band label (FM / AM)
        Text(
            text = bandLabel,
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = TextMuted,
                letterSpacing = 8.sp,
            ),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * Signal quality indicator bar — styled like car stereo reception bars.
 */
@Composable
fun SignalMeter(
    confidence: Float, // 0.0–1.0
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.radio.accent
    val barCount = 5
    val filledBars = (confidence * barCount).toInt().coerceIn(0, barCount)

    Row(
        modifier = modifier.semantics { contentDescription = "Signal strength: $filledBars out of $barCount bars" },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        (1..barCount).forEach { bar ->
            val barHeight = (8 + bar * 6).dp
            val filled = bar <= filledBars
            val color = when {
                !filled -> MaterialTheme.radio.signalNone
                bar <= 2 -> MaterialTheme.radio.signalWeak
                bar <= 4 -> MaterialTheme.radio.signalMedium
                else -> MaterialTheme.radio.signalStrong
            }
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(barHeight)
                    .drawBehind {
                        drawRect(color)
                    }
            )
        }
    }
}

/**
 * Compact status badges — STEREO / MONO / RDS
 */
@Composable
fun StatusBadges(
    isStereo: Boolean,
    hasRds: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusBadge(
            label = if (isStereo) "STEREO" else "MONO",
            active = true,
            color = if (isStereo) MaterialTheme.radio.accentGlow else TextMuted,
        )
        if (hasRds) {
            StatusBadge(label = "RDS", active = true, color = MaterialTheme.radio.accent)
        }
    }
}

@Composable
private fun StatusBadge(
    label: String,
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = if (active) color else TextMuted,
            letterSpacing = 2.sp,
        ),
        modifier = modifier,
    )
}
