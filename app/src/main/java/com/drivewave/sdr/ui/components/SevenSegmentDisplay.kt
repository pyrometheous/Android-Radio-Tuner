package com.drivewave.sdr.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drivewave.sdr.ui.theme.radio

/**
 * Segment index encoding for each character.
 *
 * Indices   layout:
 * ```
 *   0000
 *  5    1
 *  5    1
 *   6666
 *  4    2
 *  4    2
 *   3333
 * ```
 * [0]=top  [1]=top-right  [2]=bottom-right  [3]=bottom
 * [4]=bottom-left  [5]=top-left  [6]=middle
 */
private val DIGIT_SEGMENTS: Map<Char, BooleanArray> = mapOf(
    '0' to booleanArrayOf(true,  true,  true,  true,  true,  true,  false),
    '1' to booleanArrayOf(false, true,  true,  false, false, false, false),
    '2' to booleanArrayOf(true,  true,  false, true,  true,  false, true),
    '3' to booleanArrayOf(true,  true,  true,  true,  false, false, true),
    '4' to booleanArrayOf(false, true,  true,  false, false, true,  true),
    '5' to booleanArrayOf(true,  false, true,  true,  false, true,  true),
    '6' to booleanArrayOf(true,  false, true,  true,  true,  true,  true),
    '7' to booleanArrayOf(true,  true,  true,  false, false, false, false),
    '8' to booleanArrayOf(true,  true,  true,  true,  true,  true,  true),
    '9' to booleanArrayOf(true,  true,  true,  true,  false, true,  true),
    '-' to booleanArrayOf(false, false, false, false, false, false, true),
    'E' to booleanArrayOf(true,  false, false, true,  true,  true,  true),
    'e' to booleanArrayOf(true,  true,  false, true,  true,  true,  true),
    'R' to booleanArrayOf(true,  true,  false, false, true,  true,  true),
    'r' to booleanArrayOf(false, false, false, false, true,  false, true),
    'O' to booleanArrayOf(true,  true,  true,  true,  true,  true,  false),
    'o' to booleanArrayOf(false, false, true,  true,  true,  false, true),
    'H' to booleanArrayOf(false, true,  true,  false, true,  true,  true),
    'h' to booleanArrayOf(false, false, true,  false, true,  true,  true),
    'L' to booleanArrayOf(false, false, false, true,  true,  true,  false),
    'P' to booleanArrayOf(true,  true,  false, false, true,  true,  true),
    'S' to booleanArrayOf(true,  false, true,  true,  false, true,  true),
    ' ' to booleanArrayOf(false, false, false, false, false, false, false),
)

/**
 * A canvas-based 7-segment LED display.
 *
 * Renders each character in [text] as a physical LED segment digit.  Characters
 * not in the mapping are rendered as blanks.  The decimal point '.' is rendered
 * as a small dot at the bottom-right corner of the preceding digit slot.
 *
 * @param text          String to render (digits, '.', '-', and supported letters).
 * @param modifier      Modifier applied to the containing [Row].
 * @param digitHeight   Height of each digit cell.
 * @param activeColor   Color of lit segments; defaults to [MaterialTheme.radio.accent].
 */
@Composable
fun SevenSegmentDisplay(
    text: String,
    modifier: Modifier = Modifier,
    digitHeight: Dp = 80.dp,
    activeColor: Color = Color.Unspecified,
) {
    val resolved    = if (activeColor == Color.Unspecified) MaterialTheme.radio.accent else activeColor
    val dimColor    = resolved.copy(alpha = 0.10f)

    // Proportions tuned to look like a standard LED display
    val digitWidth  = digitHeight * 0.55f
    val dotWidth    = digitHeight * 0.20f
    val spacing     = digitHeight * 0.05f

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
    ) {
        text.forEach { char ->
            if (char == '.') {
                // Decimal point — small dot at bottom of a narrow slot
                Canvas(
                    modifier = Modifier
                        .width(dotWidth)
                        .height(digitHeight),
                ) {
                    val r = size.width * 0.38f
                    drawCircle(
                        color = resolved,
                        radius = r,
                        center = Offset(size.width / 2f, size.height - r),
                    )
                }
            } else {
                val segs = DIGIT_SEGMENTS[char]
                    ?: DIGIT_SEGMENTS[char.uppercaseChar()]
                    ?: DIGIT_SEGMENTS[' ']!!

                Canvas(
                    modifier = Modifier
                        .width(digitWidth)
                        .height(digitHeight),
                ) {
                    val t = size.width * 0.13f          // segment thickness (≈13% of width)
                    val g = size.width * 0.04f          // gap between segments
                    drawSevenSegment(segs, resolved, dimColor, t, g)
                }
                Spacer(Modifier.width(spacing))
            }
        }
    }
}

/** Draw all seven segments of one digit inside the current [DrawScope]. */
private fun DrawScope.drawSevenSegment(
    segs: BooleanArray,   // indices 0–6 as per header doc
    on: Color,
    off: Color,
    t: Float,             // segment thickness in px
    g: Float,             // gap in px
) {
    val w = size.width
    val h = size.height
    val r = CornerRadius(t * 0.4f)
    val mid = h / 2f

    // ── Horizontal bars ──────────────────────────────────────────────────────
    // [0] top
    drawRoundRect(
        color = if (segs[0]) on else off,
        topLeft = Offset(t + g, 0f),
        size = Size(w - 2f * (t + g), t),
        cornerRadius = r,
    )
    // [6] middle
    drawRoundRect(
        color = if (segs[6]) on else off,
        topLeft = Offset(t + g, mid - t / 2f),
        size = Size(w - 2f * (t + g), t),
        cornerRadius = r,
    )
    // [3] bottom
    drawRoundRect(
        color = if (segs[3]) on else off,
        topLeft = Offset(t + g, h - t),
        size = Size(w - 2f * (t + g), t),
        cornerRadius = r,
    )

    // ── Vertical bars (upper half) ───────────────────────────────────────────
    val vTopStart = t + g
    val vTopEnd   = mid - g
    val vTopH     = vTopEnd - vTopStart
    // [5] top-left
    drawRoundRect(
        color = if (segs[5]) on else off,
        topLeft = Offset(0f, vTopStart),
        size = Size(t, vTopH),
        cornerRadius = r,
    )
    // [1] top-right
    drawRoundRect(
        color = if (segs[1]) on else off,
        topLeft = Offset(w - t, vTopStart),
        size = Size(t, vTopH),
        cornerRadius = r,
    )

    // ── Vertical bars (lower half) ───────────────────────────────────────────
    val vBotStart = mid + g
    val vBotEnd   = h - t - g
    val vBotH     = vBotEnd - vBotStart
    // [4] bottom-left
    drawRoundRect(
        color = if (segs[4]) on else off,
        topLeft = Offset(0f, vBotStart),
        size = Size(t, vBotH),
        cornerRadius = r,
    )
    // [2] bottom-right
    drawRoundRect(
        color = if (segs[2]) on else off,
        topLeft = Offset(w - t, vBotStart),
        size = Size(t, vBotH),
        cornerRadius = r,
    )
}
