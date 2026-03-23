package com.drivewave.sdr.metadata

import com.drivewave.sdr.domain.model.RdsMetadata
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides fake RDS metadata for the [FakeSdrBackend].
 * Used during UI development and preview mode to simulate station data.
 */
@Singleton
class FakeRdsProvider @Inject constructor() {

    private val fakeStations = mapOf(
        87.9f to RdsMetadata(
            programService = "WDRV 88",
            radioText = "The Drive - Chicago's Classic Rock",
            programType = 6,
            programTypeLabel = "Classic Rock",
            piCode = "4BDA",
            alternativeFrequencies = listOf(88.3f),
            lastRefreshedEpochMs = System.currentTimeMillis(),
        ),
        91.1f to RdsMetadata(
            programService = "WBEZ 91",
            radioText = "All Things Considered",
            programType = 2,
            programTypeLabel = "Information",
            piCode = "7B01",
            lastRefreshedEpochMs = System.currentTimeMillis(),
        ),
        95.5f to RdsMetadata(
            programService = "WNUA",
            radioText = "Smooth Jazz 95.5",
            programType = 14,
            programTypeLabel = "Jazz",
            piCode = "2CC3",
            lastRefreshedEpochMs = System.currentTimeMillis(),
        ),
        99.1f to RdsMetadata(
            programService = "WBBM 99",
            radioText = "WBBM Newsradio 780/105.9",
            programType = 1,
            programTypeLabel = "News",
            piCode = "3F11",
            lastRefreshedEpochMs = System.currentTimeMillis(),
        ),
        101.1f to RdsMetadata(
            programService = "WKSC 103",
            radioText = "Kiss FM Chicago - Hip Hop & R&B",
            programType = 26,
            programTypeLabel = "Hip Hop",
            piCode = "5012",
            lastRefreshedEpochMs = System.currentTimeMillis(),
        ),
        103.7f to RdsMetadata(
            programService = "WLS 103",
            radioText = "Katy Perry - Firework",
            programType = 9,
            programTypeLabel = "Top 40",
            piCode = "614A",
            alternativeFrequencies = listOf(890.0f),
            lastRefreshedEpochMs = System.currentTimeMillis(),
        ),
        107.9f to RdsMetadata(
            programService = "WLUP 107",
            radioText = "The Loop - Rock 107.9",
            programType = 5,
            programTypeLabel = "Rock",
            piCode = "7F2B",
            lastRefreshedEpochMs = System.currentTimeMillis(),
        ),
    )

    fun getMetadata(frequencyMhz: Float): RdsMetadata {
        return fakeStations.entries
            .firstOrNull { (freq, _) -> kotlin.math.abs(freq - frequencyMhz) < 0.05f }
            ?.value
            ?.copy(lastRefreshedEpochMs = System.currentTimeMillis())
            ?: RdsMetadata(lastRefreshedEpochMs = System.currentTimeMillis())
    }

    fun getAllFakeStationFrequencies(): List<Float> = fakeStations.keys.sorted()
}
