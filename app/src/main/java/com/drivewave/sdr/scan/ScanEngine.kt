package com.drivewave.sdr.scan

import com.drivewave.sdr.domain.model.BandConfig
import com.drivewave.sdr.domain.model.RadioBand
import com.drivewave.sdr.domain.model.RdsMetadata
import com.drivewave.sdr.domain.model.SignalQuality
import com.drivewave.sdr.domain.model.Station
import com.drivewave.sdr.domain.model.stationIdFrom
import com.drivewave.sdr.driver.SdrBackend
import com.drivewave.sdr.metadata.RdsParser
import com.drivewave.sdr.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Scan progress event. */
sealed class ScanEvent {
    data class Progress(val currentFrequencyMhz: Float, val percentComplete: Float) : ScanEvent()
    data class StationFound(val station: Station) : ScanEvent()
    data class Completed(val stations: List<Station>, val sessionId: String) : ScanEvent()
    data class Cancelled(val stationsSoFar: List<Station>) : ScanEvent()
    data class Error(val message: String) : ScanEvent()
}

/**
 * Station scan engine.
 *
 * Implements a multi-pass scoring model to find valid stations:
 *   Stage 1 — Quick sweep: step across the band looking for power peaks
 *   Stage 2 — Candidate hold: dwell on each candidate for [DWELL_MS] ms and
 *             collect multiple signal quality samples
 *   Stage 3 — Scoring: combine all metrics into a confidence score
 *   Stage 4 — Metadata collection: attempt RDS lock for a short window
 *
 * TODO(calibration): Run auto-calibration heuristics during scan (see AutoCalibrator)
 */
@Singleton
class ScanEngine @Inject constructor(
    private val rdsParser: RdsParser,
    private val logger: AppLogger,
) {
    private val _scanEvents = MutableStateFlow<ScanEvent?>(null)
    val scanEvents: StateFlow<ScanEvent?> = _scanEvents

    private var scanJob: Job? = null
    private val foundStations = mutableListOf<Station>()

    val isScanning: Boolean get() = scanJob?.isActive == true

    /**
     * Start a scan of [bandConfig] using [backend].
     * Emits [ScanEvent]s via [scanEvents].
     * @param ppmOffset calibration offset to use during scan
     */
    fun startScan(
        backend: SdrBackend,
        bandConfig: BandConfig,
        ppmOffset: Int,
        scope: CoroutineScope,
    ) {
        if (isScanning) return
        foundStations.clear()
        val sessionId = UUID.randomUUID().toString()

        scanJob = scope.launch {
            try {
                logger.d(TAG, "startScan: backend=${backend.name} band=${bandConfig.band} ppm=$ppmOffset")
                runScan(backend, bandConfig, ppmOffset, sessionId)
            } catch (e: CancellationException) {
                logger.d(TAG, "scan cancelled — ${foundStations.size} stations so far")
                _scanEvents.value = ScanEvent.Cancelled(foundStations.toList())
            } catch (e: Exception) {
                val msg = "${e.javaClass.simpleName}: ${e.message ?: "(no message)"}"
                logger.e(TAG, "scan failed", e)
                _scanEvents.value = ScanEvent.Error(msg)
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
    }

    private suspend fun runScan(
        backend: SdrBackend,
        bandConfig: BandConfig,
        ppmOffset: Int,
        sessionId: String,
    ) {
        _scanEvents.value = null // reset
        val frequencies = generateSequence(bandConfig.startMhz) {
            it + bandConfig.stepMhz
        }.takeWhile { it <= bandConfig.endMhz + 0.001f }.toList()

        val total = frequencies.size

        // Stage 1 + 2 + 3: sweep with dwell scoring
        frequencies.forEachIndexed { index, freq ->
            if (!currentCoroutineContext().isActive) return

            _scanEvents.value = ScanEvent.Progress(
                currentFrequencyMhz = freq,
                percentComplete = (index.toFloat() / total) * 0.8f, // 80% for sweep
            )

            backend.tune(freq, ppmOffset)
            delay(TUNE_SETTLE_MS)

            // Collect multiple quality samples during dwell window
            val samples = (1..QUALITY_SAMPLES).map {
                delay(SAMPLE_INTERVAL_MS)
                backend.measureSignalQuality()
            }

            val avgScore = scoreCandidate(samples, freq, bandConfig)
            if (avgScore >= CONFIDENCE_THRESHOLD) {
                // Stage 4: RDS metadata collection
                val rds = collectRdsTimed(backend, freq, ppmOffset)
                val station = buildStation(freq, bandConfig.band, avgScore, rds, sessionId)
                foundStations.add(station)
                _scanEvents.value = ScanEvent.StationFound(station)
            }
        }

        _scanEvents.value = ScanEvent.Progress(107.9f, 1f)
        _scanEvents.value = ScanEvent.Completed(foundStations.toList(), sessionId)
    }

    /**
     * Station scoring model.
     * Combines RF power, SNR, stereo pilot, RDS quality, audio quieting.
     * Returns 0.0–1.0 confidence score.
     */
    private fun scoreCandidate(samples: List<SignalQuality>, freq: Float, bandConfig: BandConfig): Float {
        if (samples.isEmpty()) return 0f

        val avgPower = samples.map { it.powerDbm }.average().toFloat()
        val avgSnr = samples.map { it.snrDb }.average().toFloat()
        val avgQuieting = samples.map { it.audioQuieting }.average().toFloat()
        val avgRdsErr = samples.map { it.rdsBlockErrorRate }.average().toFloat()
        val stereoPct = samples.count { it.stereoPilotLocked }.toFloat() / samples.size

        // Normalize power: -90 dBm = 0, -40 dBm = 1
        val powerScore = ((avgPower + 90f) / 50f).coerceIn(0f, 1f)
        // SNR: 3 dB = 0, 30 dB = 1
        val snrScore = ((avgSnr - 3f) / 27f).coerceIn(0f, 1f)
        // RDS quality (inverted — low BLER = good)
        val rdsScore = (1f - avgRdsErr).coerceIn(0f, 1f)
        // Channel raster alignment penalty — penalize off-raster frequencies
        val rasterScore = if (isOnRaster(freq, bandConfig.stepMhz)) 1f else 0.6f

        return (powerScore * 0.25f +
                snrScore * 0.25f +
                avgQuieting * 0.20f +
                stereoPct * 0.10f +
                rdsScore * 0.10f +
                rasterScore * 0.10f).coerceIn(0f, 1f)
    }

    private fun isOnRaster(freq: Float, step: Float): Boolean {
        val scaled = (freq / step).toLong()
        val remainder = kotlin.math.abs(freq - scaled * step)
        return remainder < step * 0.1f
    }

    /**
     * Dwells on a frequency for [RDS_COLLECT_MS] and collects RDS metadata.
     */
    private suspend fun collectRdsTimed(backend: SdrBackend, freq: Float, ppmOffset: Int): RdsMetadata {
        rdsParser.reset()
        backend.tune(freq, ppmOffset)
        delay(RDS_COLLECT_MS)
        // TODO(rds-parser): Collect IQ samples from backend, demodulate FM+RDS,
        // call rdsParser.parseRawGroup() for each decoded group.
        return rdsParser.buildMetadata()
    }

    private fun buildStation(
        freq: Float,
        band: RadioBand,
        confidence: Float,
        rds: RdsMetadata,
        sessionId: String,
    ): Station {
        val id = stationIdFrom(freq, band)
        val name = rds.programService?.trim()?.takeIf { it.isNotBlank() } ?: "%.1f %s".format(freq, band.name)
        return Station(
            id = id,
            frequencyMhz = freq,
            band = band,
            displayName = name,
            rdsMetadata = rds,
            signalConfidence = confidence,
            discoveredAtEpochMs = System.currentTimeMillis(),
            scanSessionId = sessionId,
        )
    }

    companion object {
        private const val TAG = "ScanEngine"
        private const val TUNE_SETTLE_MS = 80L
        private const val QUALITY_SAMPLES = 5
        private const val SAMPLE_INTERVAL_MS = 40L
        private const val CONFIDENCE_THRESHOLD = 0.35f
        private const val RDS_COLLECT_MS = 500L
    }
}
