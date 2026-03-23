package com.drivewave.sdr.scan

import com.drivewave.sdr.domain.model.CalibrationProfile
import com.drivewave.sdr.driver.SdrBackend
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Automatic PPM frequency offset calibration.
 *
 * RTL-SDR dongles have a crystal oscillator that drifts from nominal, causing
 * all received frequencies to be offset by a small amount (typically ±50 PPM).
 * This class heuristically estimates the correct offset during a station scan.
 *
 * Algorithm:
 *   For each strong candidate found during scanning, evaluate PPM offsets
 *   in the range [currentOffset - 30 .. currentOffset + 30] in steps of 2 PPM.
 *   For each candidate offset, re-measure:
 *     - signal strength delta (should not change much)
 *     - RDS block error rate (improves with correct offset)
 *     - stereo pilot stability
 *     - alignment with nearest expected FM raster frequency
 *   Score each PPM candidate and collect votes weighted by candidate confidence.
 *   The offset with the most weighted votes is the calibration result.
 *
 * TODO(calibration): Implement this properly once native/IQ stream is available.
 * Currently returns the last known offset without modification.
 */
@Singleton
class AutoCalibrator @Inject constructor() {

    /**
     * Run auto-calibration given a set of found stations.
     * @param backend SDR backend (may be used to re-tune with candidate offsets)
     * @param currentPpm the last known PPM offset
     * @param deviceSerial USB serial number for profile storage
     * @return updated [CalibrationProfile] with estimated PPM and confidence
     */
    suspend fun calibrate(
        backend: SdrBackend,
        currentPpm: Int,
        deviceSerial: String,
    ): CalibrationProfile {
        // TODO(calibration): Implement PPM estimation algorithm.
        // Step 1: For each strong station found in scan, loop over candidate PPMs
        // Step 2: Tune with candidate PPM, measure RDS BLER + pilot stability
        // Step 3: Score each candidate and accumulate weighted votes
        // Step 4: Pick best-voted offset; if confidence < 0.5, set needsReview = true

        // For now: return current offset with low confidence, triggering UI review prompt
        return CalibrationProfile(
            deviceSerial = deviceSerial,
            ppmOffset = currentPpm,
            autoCalibrationConfidence = 0f, // TODO(calibration): set from algorithm
            lastCalibrationEpochMs = System.currentTimeMillis(),
            isManualOverride = false,
        )
    }
}
