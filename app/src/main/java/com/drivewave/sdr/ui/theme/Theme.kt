package com.drivewave.sdr.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Extended theme tokens for car-radio-specific colors that M3 doesn't have. */
data class RadioThemeExtras(
    val accent: Color,
    val accentDim: Color,
    val accentGlow: Color,
    val signalStrong: Color = SignalStrong,
    val signalMedium: Color = SignalMedium,
    val signalWeak: Color = SignalWeak,
    val signalNone: Color = SignalNone,
    val textMuted: Color = TextMuted,
    val radioSurface: Color = RadioSurface,
    val radioElevated: Color = RadioElevated,
    val radioCard: Color = RadioCard,
)

val LocalRadioTheme = staticCompositionLocalOf {
    RadioThemeExtras(
        accent = AmberPrimary,
        accentDim = AmberDim,
        accentGlow = AmberGlow,
    )
}

@Composable
fun DriveWaveTheme(
    accentIndex: Int = 4,
    customAccentHex: String? = null,
    content: @Composable () -> Unit,
) {
    // For index 5 (Custom), derive accent from stored hex; fallback to Purple.
    val accent = if (accentIndex == 5) parseHexColor(customAccentHex ?: "") ?: PurplePrimary
                 else accentColor(accentIndex)
    val accentDim = if (accentIndex == 5) Color(
        red = accent.red * 0.35f, green = accent.green * 0.35f, blue = accent.blue * 0.35f, alpha = 1f
    ) else accentDimColor(accentIndex)
    val accentGlow = if (accentIndex == 5) Color(
        red = (accent.red + 0.28f).coerceAtMost(1f),
        green = (accent.green + 0.28f).coerceAtMost(1f),
        blue = (accent.blue + 0.28f).coerceAtMost(1f),
        alpha = 1f,
    ) else accentGlowColor(accentIndex)

    val colorScheme = darkColorScheme(
        primary = accent,
        onPrimary = RadioBlack,
        primaryContainer = accentDim,
        onPrimaryContainer = accentGlow,
        secondary = accentGlow,
        onSecondary = RadioBlack,
        secondaryContainer = RadioCard,
        onSecondaryContainer = TextSecondary,
        tertiary = SignalStrong,
        background = RadioBlack,
        onBackground = TextPrimary,
        surface = RadioSurface,
        onSurface = TextPrimary,
        surfaceVariant = RadioElevated,
        onSurfaceVariant = TextSecondary,
        surfaceContainer = RadioCard,
        surfaceContainerHigh = RadioElevated,
        outline = accentDim,
        outlineVariant = TextDisabled,
        error = RedPrimary,
        onError = RadioBlack,
    )

    CompositionLocalProvider(
        LocalRadioTheme provides RadioThemeExtras(
            accent = accent,
            accentDim = accentDim,
            accentGlow = accentGlow,
        )
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

/** Shorthand to access extended radio theme colors. */
val MaterialTheme.radio: RadioThemeExtras
    @Composable get() = LocalRadioTheme.current
