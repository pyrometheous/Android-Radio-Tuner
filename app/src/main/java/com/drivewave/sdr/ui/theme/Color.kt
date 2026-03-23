package com.drivewave.sdr.ui.theme

import androidx.compose.ui.graphics.Color

// ── Core car radio dark palette ──────────────────────────────────────────────

/** Almost black background — like a car stereo bezel */
val RadioBlack = Color(0xFF0A0A0C)
/** Dark panel surface */
val RadioSurface = Color(0xFF12141A)
/** Slightly elevated panel */
val RadioElevated = Color(0xFF1C1F28)
/** Card / tile background */
val RadioCard = Color(0xFF222636)

// ── Accent colors  (0=Amber, 1=Cyan, 2=Red, 3=Green, 4=Purple [default], 5=Custom) ──

// Amber / warm
val AmberPrimary = Color(0xFFFFB300)
val AmberDim = Color(0xFF7A5500)
val AmberGlow = Color(0xFFFFCC40)

// Cyan / cool
val CyanPrimary = Color(0xFF00E5FF)
val CyanDim = Color(0xFF006070)
val CyanGlow = Color(0xFF80F0FF)

// Red / sporty
val RedPrimary = Color(0xFFFF3D3D)
val RedDim = Color(0xFF6A0000)
val RedGlow = Color(0xFFFF8080)

// Green / retro
val GreenPrimary = Color(0xFF39FF14)
val GreenDim = Color(0xFF1A5200)
val GreenGlow = Color(0xFFAAFFAA)

// Purple (default theme)
val PurplePrimary = Color(0xFFBB86FC)
val PurpleDim = Color(0xFF4A1A8A)
val PurpleGlow = Color(0xFFE0C0FF)

// ── Signal indicator colors ───────────────────────────────────────────────────

val SignalStrong = Color(0xFF39FF14)   // bright green — full bars
val SignalMedium = Color(0xFFFFB300)   // amber — moderate signal
val SignalWeak = Color(0xFFFF6B00)     // orange — low signal
val SignalNone = Color(0xFF444444)     // gray — no signal

// ── Text colors ───────────────────────────────────────────────────────────────

val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB0B4C0)
val TextMuted = Color(0xFF666A80)
val TextDisabled = Color(0xFF3A3D50)

// ── Waveform colors ───────────────────────────────────────────────────────────

val WaveformActive = AmberPrimary
val WaveformInactive = AmberDim

/** Pick the primary accent for a given theme index (0=Amber, 1=Cyan, 2=Red, 3=Green, 4=Purple, 5=Custom). */
fun accentColor(index: Int) = when (index) {
    0 -> AmberPrimary
    1 -> CyanPrimary
    2 -> RedPrimary
    3 -> GreenPrimary
    else -> PurplePrimary // 4=Purple (default), 5=Custom (handled in Theme.kt)
}

fun accentDimColor(index: Int) = when (index) {
    0 -> AmberDim
    1 -> CyanDim
    2 -> RedDim
    3 -> GreenDim
    else -> PurpleDim
}

fun accentGlowColor(index: Int) = when (index) {
    0 -> AmberGlow
    1 -> CyanGlow
    2 -> RedGlow
    3 -> GreenGlow
    else -> PurpleGlow
}

/** Parse a hex color string (e.g. "#BB86FC" or "BB86FC") into a Compose Color. Returns null on failure. */
fun parseHexColor(hex: String): Color? = try {
    val normalized = if (hex.startsWith("#")) hex else "#$hex"
    Color(android.graphics.Color.parseColor(normalized))
} catch (_: Exception) { null }
