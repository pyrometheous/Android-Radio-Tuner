package com.drivewave.sdr.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.drivewave.sdr.domain.model.AudioConfig
import com.drivewave.sdr.domain.model.BandConfig
import com.drivewave.sdr.domain.model.CalibrationProfile
import com.drivewave.sdr.domain.model.RegionPreset
import com.drivewave.sdr.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "drivewave_settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    private object Keys {
        val REGION = stringPreferencesKey("region")
        val PPM_OFFSET = intPreferencesKey("ppm_offset")
        val AUDIO_MONO = booleanPreferencesKey("audio_mono")
        val AUDIO_SOFT_MUTE = booleanPreferencesKey("audio_soft_mute")
        val AUDIO_SQUELCH = floatPreferencesKey("audio_squelch")
        val AUDIO_HIGH_CUT = booleanPreferencesKey("audio_high_cut")
        val AUDIO_NOISE_REDUCTION = floatPreferencesKey("audio_noise_reduction")
        val AUDIO_BLEND_TO_MONO = booleanPreferencesKey("audio_blend_mono")
        val AUDIO_VOLUME = floatPreferencesKey("audio_volume")
        val CAL_DEVICE = stringPreferencesKey("cal_device")
        val CAL_PPM = intPreferencesKey("cal_ppm")
        val CAL_CONFIDENCE = floatPreferencesKey("cal_confidence")
        val CAL_TS = longPreferencesKey("cal_ts")
        val CAL_MANUAL = booleanPreferencesKey("cal_manual")
        val DEV_MODE = booleanPreferencesKey("dev_mode")
        val ACCENT_THEME = intPreferencesKey("accent_theme")
        val CUSTOM_ACCENT_COLOR = stringPreferencesKey("custom_accent_color")
    }

    override fun observeRegionPreset(): Flow<RegionPreset> = context.dataStore.data.map { prefs ->
        try { RegionPreset.valueOf(prefs[Keys.REGION] ?: RegionPreset.USA.name) }
        catch (_: Exception) { RegionPreset.USA }
    }

    override fun observeBandConfig(): Flow<BandConfig> = observeRegionPreset().map { it.bandConfig }

    override fun observePpmOffset(): Flow<Int> = context.dataStore.data.map { it[Keys.PPM_OFFSET] ?: 0 }

    override fun observeAudioConfig(): Flow<AudioConfig> = context.dataStore.data.map { prefs ->
        AudioConfig(
            monoMode = prefs[Keys.AUDIO_MONO] ?: false,
            softMute = prefs[Keys.AUDIO_SOFT_MUTE] ?: true,
            squelchThreshold = prefs[Keys.AUDIO_SQUELCH] ?: 0.1f,
            highCutEnabled = prefs[Keys.AUDIO_HIGH_CUT] ?: false,
            noiseReduction = prefs[Keys.AUDIO_NOISE_REDUCTION] ?: 0.3f,
            blendToMono = prefs[Keys.AUDIO_BLEND_TO_MONO] ?: true,
            volume = prefs[Keys.AUDIO_VOLUME] ?: 1.0f,
        )
    }

    override fun observeCalibrationProfile(): Flow<CalibrationProfile?> = context.dataStore.data.map { prefs ->
        val device = prefs[Keys.CAL_DEVICE] ?: return@map null
        CalibrationProfile(
            deviceSerial = device,
            ppmOffset = prefs[Keys.CAL_PPM] ?: 0,
            autoCalibrationConfidence = prefs[Keys.CAL_CONFIDENCE] ?: 0f,
            lastCalibrationEpochMs = prefs[Keys.CAL_TS] ?: 0L,
            isManualOverride = prefs[Keys.CAL_MANUAL] ?: false,
        )
    }

    override fun observeDeveloperModeEnabled(): Flow<Boolean> =
        context.dataStore.data.map { it[Keys.DEV_MODE] ?: false }

    override fun observeAccentTheme(): Flow<Int> =
        context.dataStore.data.map { it[Keys.ACCENT_THEME] ?: 4 }

    override fun observeCustomAccentColor(): Flow<String?> =
        context.dataStore.data.map { it[Keys.CUSTOM_ACCENT_COLOR] }

    override suspend fun setRegionPreset(preset: RegionPreset) {
        context.dataStore.edit { it[Keys.REGION] = preset.name }
    }

    override suspend fun setPpmOffset(ppm: Int) {
        context.dataStore.edit { it[Keys.PPM_OFFSET] = ppm }
    }

    override suspend fun setAudioConfig(config: AudioConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUDIO_MONO] = config.monoMode
            prefs[Keys.AUDIO_SOFT_MUTE] = config.softMute
            prefs[Keys.AUDIO_SQUELCH] = config.squelchThreshold
            prefs[Keys.AUDIO_HIGH_CUT] = config.highCutEnabled
            prefs[Keys.AUDIO_NOISE_REDUCTION] = config.noiseReduction
            prefs[Keys.AUDIO_BLEND_TO_MONO] = config.blendToMono
            prefs[Keys.AUDIO_VOLUME] = config.volume
        }
    }

    override suspend fun saveCalibrationProfile(profile: CalibrationProfile) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CAL_DEVICE] = profile.deviceSerial
            prefs[Keys.CAL_PPM] = profile.ppmOffset
            prefs[Keys.CAL_CONFIDENCE] = profile.autoCalibrationConfidence
            prefs[Keys.CAL_TS] = profile.lastCalibrationEpochMs
            prefs[Keys.CAL_MANUAL] = profile.isManualOverride
        }
    }

    override suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DEV_MODE] = enabled }
    }

    override suspend fun setAccentTheme(index: Int) {
        context.dataStore.edit { it[Keys.ACCENT_THEME] = index }
    }

    override suspend fun setCustomAccentColor(hex: String) {
        context.dataStore.edit { it[Keys.CUSTOM_ACCENT_COLOR] = hex }
    }
}
