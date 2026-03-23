package com.drivewave.sdr.domain.repository

import com.drivewave.sdr.domain.model.AudioConfig
import com.drivewave.sdr.domain.model.BandConfig
import com.drivewave.sdr.domain.model.CalibrationProfile
import com.drivewave.sdr.domain.model.RegionPreset
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeRegionPreset(): Flow<RegionPreset>
    fun observeBandConfig(): Flow<BandConfig>
    fun observePpmOffset(): Flow<Int>
    fun observeAudioConfig(): Flow<AudioConfig>
    fun observeCalibrationProfile(): Flow<CalibrationProfile?>
    fun observeDeveloperModeEnabled(): Flow<Boolean>
    fun observeAccentTheme(): Flow<Int>
    fun observeCustomAccentColor(): Flow<String?>

    suspend fun setRegionPreset(preset: RegionPreset)
    suspend fun setPpmOffset(ppm: Int)
    suspend fun setAudioConfig(config: AudioConfig)
    suspend fun saveCalibrationProfile(profile: CalibrationProfile)
    suspend fun setDeveloperModeEnabled(enabled: Boolean)
    suspend fun setAccentTheme(index: Int)
    suspend fun setCustomAccentColor(hex: String)
}
