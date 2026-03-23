package com.drivewave.sdr.driver

import com.drivewave.sdr.domain.model.RadioBand
import com.drivewave.sdr.domain.model.SignalQuality
import kotlinx.coroutines.flow.Flow

/**
 * SDR hardware backend abstraction.
 *
 * All SDR hardware interaction goes through this interface.
 * Swap implementations to use:
 *   - [FakeSdrBackend]   — fake/mock backend for UI development and testing
 *   - [RtlSdrNativeBackend] — real RTL-SDR via JNI (requires native integration, see TODO)
 *   - [ExternalDriverBackend] — routes through an installed driver app via IPC (fallback)
 *
 * Thread safety: all suspend functions run on whatever dispatcher the caller provides.
 * The [signalStream] Flow emits on a background thread; collectors must handle dispatching.
 */
interface SdrBackend {
    /** Human-readable name for this backend, shown in settings. */
    val name: String

    /** True if this backend is available on the current device/configuration. */
    val isAvailable: Boolean

    /**
     * Attempt to open and initialize the hardware.
     * Returns [OpenResult.Success] if the device is ready, or a specific failure.
     */
    suspend fun open(): OpenResult

    /** Close and release the hardware / driver connection. */
    suspend fun close()

    /** True if the backend is currently open and ready. */
    val isOpen: Boolean

    /**
     * Tune to the given frequency.
     * @param frequencyMhz tuning frequency in MHz
     * @param ppmOffset frequency correction offset in parts-per-million
     */
    suspend fun tune(frequencyMhz: Float, ppmOffset: Int = 0)

    /**
     * Set the RF gain. null = auto gain.
     * @param gainDb gain in dB, or null for AGC
     */
    suspend fun setGain(gainDb: Float?)

    /**
     * Set the sample rate (Hz). Typically 2_400_000 or 3_200_000 for RTL-SDR.
     * TODO(native-backend): map to rtlsdr_set_sample_rate()
     */
    suspend fun setSampleRate(sampleRateHz: Int)

    /**
     * Returns a cold Flow that emits [IqSample] blocks continuously while
     * the backend is open. Cancel the collector to stop streaming.
     *
     * TODO(native-backend): bridge this to the native ring buffer callback.
     */
    fun iqSampleStream(): Flow<IqSample>

    /**
     * Snapshot the current signal quality at the currently tuned frequency.
     * For the fake backend this returns simulated values.
     */
    suspend fun measureSignalQuality(): SignalQuality

    /**
     * Returns reported bands this hardware is capable of receiving.
     * R820T2 supports up to ~1.7 GHz RF; AM is technically out of its native
     * tuning range and is only surfaced here if the driver explicitly confirms it.
     */
    val supportedBands: List<RadioBand>

    /** Device information — serial number, USB descriptor, firmware version. */
    val deviceInfo: DeviceInfo?
}

/** Result of [SdrBackend.open]. */
sealed class OpenResult {
    data object Success : OpenResult()
    data object PermissionDenied : OpenResult()
    data object NoDevice : OpenResult()
    data object UnsupportedDevice : OpenResult()
    data class Error(val message: String, val cause: Throwable? = null) : OpenResult()
}

/** A block of raw IQ samples from the SDR. */
data class IqSample(
    /** Interleaved signed bytes: [I0, Q0, I1, Q1, ...]. Range -128..127. */
    val data: ByteArray,
    val sampleRateHz: Int,
    val centerFrequencyMhz: Float,
    val timestampNs: Long,
) {
    // ByteArray requires manual equals/hashCode for data class correctness
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IqSample) return false
        return data.contentEquals(other.data) &&
            sampleRateHz == other.sampleRateHz &&
            centerFrequencyMhz == other.centerFrequencyMhz &&
            timestampNs == other.timestampNs
    }
    override fun hashCode(): Int = data.contentHashCode()
}

/** Static information about the connected SDR device. */
data class DeviceInfo(
    val vendorId: Int,
    val productId: Int,
    val serialNumber: String,
    val manufacturerName: String?,
    val productName: String?,
    val firmwareVersion: String? = null,
)
