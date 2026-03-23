package com.drivewave.sdr.driver

import com.drivewave.sdr.domain.model.RadioBand
import com.drivewave.sdr.domain.model.SignalQuality
import com.drivewave.sdr.dsp.FmDemodulator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fake SDR backend for UI development and on-device preview.
 *
 * Simulates realistic-ish signal behavior:
 *  - Generates fake IQ sample blocks
 *  - Simulates signal quality that changes slightly over time
 *  - Supports FM only
 *
 * This backend is always available and is used when no real hardware is connected.
 */
class FakeSdrBackend @Inject constructor() : SdrBackend {

    override val name: String = "UI Preview (No Hardware)"
    override val isAvailable: Boolean = true
    override val supportedBands: List<RadioBand> = listOf(RadioBand.FM)

    private var _isOpen = false
    override val isOpen: Boolean get() = _isOpen

    private var currentFrequency = 101.1f
    private var ppm = 0
    private var gain: Float? = null
    // Sample rate must match FmDemodulator.INPUT_RATE for the audio pipeline
    private var sampleRate = FmDemodulator.INPUT_RATE

    override val deviceInfo: DeviceInfo? = DeviceInfo(
        vendorId = 0x0000,
        productId = 0x0000,
        serialNumber = "FAKE-000001",
        manufacturerName = "DriveWave Preview",
        productName = "Simulated RTL-SDR",
    )

    override suspend fun open(): OpenResult {
        delay(600) // simulate connection delay
        _isOpen = true
        return OpenResult.Success
    }

    override suspend fun close() {
        _isOpen = false
    }

    override suspend fun tune(frequencyMhz: Float, ppmOffset: Int) {
        currentFrequency = frequencyMhz
        ppm = ppmOffset
        delay(50) // small settle time
    }

    override suspend fun setGain(gainDb: Float?) {
        gain = gainDb
    }

    override suspend fun setSampleRate(sampleRateHz: Int) {
        sampleRate = sampleRateHz
    }

    override fun iqSampleStream(): Flow<IqSample> = flow {
        // Generate FM-modulated 1 kHz test tone so demodulation produces audible output.
        // IQ bytes are signed (-128..127) matching IqSample.data convention.
        var carrierPhase = 0.0
        var audioPhase   = 0.0
        val audioFreqHz  = 1_000.0   // 1 kHz tone, clearly audible after demod
        val maxDeviation = 50_000.0  // ±50 kHz FM deviation (inside ±75 kHz standard)
        val bytesPerBlock = 8_192    // 4096 IQ pairs

        while (true) {
            val samplePeriod = 1.0 / sampleRate
            val data = ByteArray(bytesPerBlock)
            for (n in 0 until bytesPerBlock / 2) {
                val audio = sin(audioPhase)
                audioPhase += 2.0 * Math.PI * audioFreqHz * samplePeriod

                // FM: integrate audio to get phase deviation
                carrierPhase += 2.0 * Math.PI * maxDeviation * audio * samplePeriod

                // RTL-SDR unsigned format: 0-255, centre at 127.
                // Stored as signed Java bytes (128 → -128, 255 → -1, etc.).
                data[n * 2]     = ((cos(carrierPhase) * 100.0 + 127.0).toInt().coerceIn(0, 255)).toByte()
                data[n * 2 + 1] = ((sin(carrierPhase) * 100.0 + 127.0).toInt().coerceIn(0, 255)).toByte()
            }
            emit(IqSample(data, sampleRate, currentFrequency, System.nanoTime()))
            // Pace to ~real-time: bytesPerBlock/2 IQ pairs at sampleRate Hz
            delay(((bytesPerBlock / 2) * 1000L / sampleRate).coerceAtLeast(1))
        }
    }

    override suspend fun measureSignalQuality(): SignalQuality {
        if (!_isOpen) return SignalQuality()
        // Simulate varying signal that's better on known "fake" FM stations
        val fakeStrong = listOf(87.9f, 91.1f, 95.5f, 99.1f, 101.1f, 103.7f, 107.9f)
        val isOnStation = fakeStrong.any { kotlin.math.abs(it - currentFrequency) < 0.05f }
        val base = if (isOnStation) 0.75f else 0.05f
        val noise = Random.nextFloat() * 0.1f
        val power = if (isOnStation) -55f + Random.nextFloat() * 5f else -85f + Random.nextFloat() * 10f
        return SignalQuality(
            powerDbm = power,
            snrDb = if (isOnStation) 25f + Random.nextFloat() * 10f else 5f,
            stereoPilotLocked = isOnStation && currentFrequency > 90f,
            rdsBlockErrorRate = if (isOnStation) 0.05f + Random.nextFloat() * 0.1f else 0.9f,
            audioQuieting = (base + noise).coerceIn(0f, 1f),
            confidence = (base + noise).coerceIn(0f, 1f),
        )
    }
}
