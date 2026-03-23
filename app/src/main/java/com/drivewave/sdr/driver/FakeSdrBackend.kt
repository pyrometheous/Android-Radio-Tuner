package com.drivewave.sdr.driver

import com.drivewave.sdr.domain.model.RadioBand
import com.drivewave.sdr.domain.model.SignalQuality
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
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
    private var sampleRate = 2_400_000

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
        var t = 0L
        while (true) {
            val blockSize = 16384
            val data = ByteArray(blockSize) { i ->
                val phase = (2.0 * Math.PI * 200_000.0 * t / sampleRate).mod(2.0 * Math.PI)
                val signal = if (i % 2 == 0) {
                    (sin(phase + (i / 2).toDouble() * 0.001) * 100 + Random.nextDouble(-10.0, 10.0)).toInt().coerceIn(-128, 127).toByte()
                } else {
                    (sin(phase + Math.PI / 2 + (i / 2).toDouble() * 0.001) * 100 + Random.nextDouble(-10.0, 10.0)).toInt().coerceIn(-128, 127).toByte()
                }
                t++
                signal
            }
            emit(IqSample(data, sampleRate, currentFrequency, System.nanoTime()))
            delay(33) // ~30 blocks/sec
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
