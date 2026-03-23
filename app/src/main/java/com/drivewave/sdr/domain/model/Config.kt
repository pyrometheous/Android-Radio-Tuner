package com.drivewave.sdr.domain.model

/** FM band configuration — region-aware. */
data class BandConfig(
    val band: RadioBand,
    val startMhz: Float,
    val endMhz: Float,
    /** Channel raster / step size in MHz. */
    val stepMhz: Float,
    /** De-emphasis time constant in microseconds. */
    val deEmphasisUs: Int,
) {
    companion object {
        val FM_WORLD = BandConfig(
            band = RadioBand.FM,
            startMhz = 87.5f,
            endMhz = 108.0f,
            stepMhz = 0.1f,
            deEmphasisUs = 50,
        )
        val FM_US = BandConfig(
            band = RadioBand.FM,
            startMhz = 87.9f,
            endMhz = 107.9f,
            stepMhz = 0.2f,
            deEmphasisUs = 75,
        )
        val FM_JAPAN = BandConfig(
            band = RadioBand.FM,
            startMhz = 76.0f,
            endMhz = 95.0f,
            stepMhz = 0.1f,
            deEmphasisUs = 50,
        )
        /** Experimental AM MW band. Only used if hardware supports it. */
        val AM_MW_EXPERIMENTAL = BandConfig(
            band = RadioBand.AM_EXPERIMENTAL,
            startMhz = 0.530f,
            endMhz = 1.710f,
            stepMhz = 0.010f,
            deEmphasisUs = 0,
        )

        val ALL_FM = listOf(FM_WORLD, FM_US, FM_JAPAN)
    }
}

enum class RegionPreset(val displayName: String, val bandConfig: BandConfig) {
    USA("USA", BandConfig.FM_US),
    WORLDWIDE("Worldwide / EU", BandConfig.FM_WORLD),
    JAPAN("Japan", BandConfig.FM_JAPAN),
}

/** DSP and audio cleanup configuration. */
data class AudioConfig(
    val monoMode: Boolean = false,      // false = auto stereo when pilot locked
    val softMute: Boolean = true,
    val squelchThreshold: Float = 0.1f, // signal confidence below which to mute
    val highCutEnabled: Boolean = false, // treble reduction for hiss
    val noiseReduction: Float = 0.3f,   // 0.0–1.0
    val blendToMono: Boolean = true,    // blend to mono on weak stereo signal
    val volume: Float = 1.0f,           // 0.0–1.0
)

/** Per-device SDR calibration profile. */
data class CalibrationProfile(
    val deviceSerial: String,
    val ppmOffset: Int = 0,
    val autoCalibrationConfidence: Float = 0f,
    val lastCalibrationEpochMs: Long = 0L,
    val isManualOverride: Boolean = false,
)
