package com.drivewave.sdr.domain.model

/** State of the connected SDR hardware. */
enum class SdrConnectionState {
    NO_DONGLE,
    PERMISSION_NEEDED,
    CONNECTING,
    READY,
    SCANNING,
    RECORDING,
    ERROR,
}

/** Current radio engine / playback state. */
data class RadioState(
    val connectionState: SdrConnectionState = SdrConnectionState.NO_DONGLE,
    val currentStation: Station? = null,
    val currentFrequencyMhz: Float = 87.9f,
    val currentBand: RadioBand = RadioBand.FM,
    val signalQuality: SignalQuality = SignalQuality(),
    val isPlaying: Boolean = false,
    val isMuted: Boolean = false,
    val isRecording: Boolean = false,
    val currentRdsMetadata: RdsMetadata = RdsMetadata(),
    val errorMessage: String? = null,
    /** True when auto-calibration confidence is too low to apply silently. */
    val needsCalibrationReview: Boolean = false,
)

/** A single scan session result. */
data class ScanSession(
    val id: String,
    val band: RadioBand,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long? = null,
    val stationsFound: Int = 0,
    val ppmOffsetUsed: Int = 0,
)

/** A saved audio recording. */
data class Recording(
    val id: String,
    val filePath: String,
    val fileName: String,
    val stationName: String,
    val frequencyMhz: Float,
    val band: RadioBand,
    val startedAtEpochMs: Long,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val radioText: String? = null,
)
