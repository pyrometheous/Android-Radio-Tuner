package com.drivewave.sdr.domain.model

import kotlinx.serialization.Serializable

/** Supported radio bands. AM is capability-gated and marked experimental. */
enum class RadioBand {
    FM,
    AM_EXPERIMENTAL // Only shown when hardware explicitly reports AM coverage
}

/** Raw signal quality snapshot from the SDR engine. */
data class SignalQuality(
    /** RSSI-like power estimate in dBm (negative, closer to 0 = stronger). */
    val powerDbm: Float = -99f,
    /** Estimated SNR in dB. */
    val snrDb: Float = 0f,
    /** True when FM stereo pilot (19 kHz) is locked. */
    val stereoPilotLocked: Boolean = false,
    /** RDS block error rate 0.0–1.0 (0 = perfect, 1 = total failure). */
    val rdsBlockErrorRate: Float = 1f,
    /** Audio quieting estimate 0.0–1.0 (1 = fully quieted / clean reception). */
    val audioQuieting: Float = 0f,
    /** Confidence score 0.0–1.0 computed during scanning. */
    val confidence: Float = 0f,
)

/** Parsed RDS / RBDS metadata from the broadcast signal. Never from the internet. */
@Serializable
data class RdsMetadata(
    /** Program Service name — the 8-char station name field. */
    val programService: String? = null,
    /** RadioText — up to 64 chars of now-playing / show info. */
    val radioText: String? = null,
    /** Program Type code 0–31. */
    val programType: Int? = null,
    /** Human-readable program type string, e.g. "Rock", "Pop", "News". */
    val programTypeLabel: String? = null,
    /** Programme Identification code (hex). */
    val piCode: String? = null,
    /** Alternative Frequencies list in MHz. */
    val alternativeFrequencies: List<Float> = emptyList(),
    /** Timestamp when this metadata was last refreshed from the signal. */
    val lastRefreshedEpochMs: Long = 0L,
)

/** A tunable station discovered during a scan. */
@Serializable
data class Station(
    /** Unique ID — stable across app sessions, derived from frequency + band. */
    val id: String,
    /** Frequency in MHz (FM) or kHz (AM). */
    val frequencyMhz: Float,
    val band: RadioBand,
    /** Name derived from RDS PS, or fallback. */
    val displayName: String,
    /** Optional user-provided rename that overrides displayName. */
    val userLabel: String? = null,
    val rdsMetadata: RdsMetadata = RdsMetadata(),
    val signalConfidence: Float = 0f,  // 0.0–1.0 from scan scoring
    val isFavorite: Boolean = false,
    val favoriteOrderIndex: Int = -1,  // -1 = not in favorites order list
    val discoveredAtEpochMs: Long = 0L,
    val scanSessionId: String = "",
)

/** Effective display name: user label takes priority, then RDS PS, then fallback. */
fun Station.effectiveName(): String =
    userLabel?.takeIf { it.isNotBlank() }
        ?: rdsMetadata.programService?.takeIf { it.isNotBlank() }
        ?: displayName.takeIf { it.isNotBlank() }
        ?: "Unnamed Station"

/** Formatted frequency string for display. */
fun Station.formattedFrequency(): String = when (band) {
    RadioBand.FM -> "%.1f".format(frequencyMhz)
    RadioBand.AM_EXPERIMENTAL -> "%.0f".format(frequencyMhz * 1000)
}

/** Unique stable ID from frequency + band. */
fun stationIdFrom(frequencyMhz: Float, band: RadioBand): String =
    "${band.name}_${"%.2f".format(frequencyMhz)}"
