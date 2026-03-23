package com.drivewave.sdr.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivewave.sdr.domain.model.AudioConfig
import com.drivewave.sdr.domain.model.RegionPreset
import com.drivewave.sdr.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val regionPreset = settingsRepository.observeRegionPreset().stateIn(
        viewModelScope, SharingStarted.Eagerly, RegionPreset.USA
    )
    val ppmOffset = settingsRepository.observePpmOffset().stateIn(
        viewModelScope, SharingStarted.Eagerly, 0
    )
    val audioConfig = settingsRepository.observeAudioConfig().stateIn(
        viewModelScope, SharingStarted.Eagerly, AudioConfig()
    )
    val developerMode = settingsRepository.observeDeveloperModeEnabled().stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )
    val accentTheme = settingsRepository.observeAccentTheme().stateIn(
        viewModelScope, SharingStarted.Eagerly, 4
    )
    val customAccentColor = settingsRepository.observeCustomAccentColor().stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )
    val calibrationProfile = settingsRepository.observeCalibrationProfile().stateIn(
        viewModelScope, SharingStarted.Eagerly, null
    )

    fun setRegion(preset: RegionPreset) = viewModelScope.launch { settingsRepository.setRegionPreset(preset) }
    fun setPpm(ppm: Int) = viewModelScope.launch { settingsRepository.setPpmOffset(ppm) }
    fun setAudio(config: AudioConfig) = viewModelScope.launch { settingsRepository.setAudioConfig(config) }
    fun setDeveloperMode(enabled: Boolean) = viewModelScope.launch { settingsRepository.setDeveloperModeEnabled(enabled) }
    fun setAccentTheme(index: Int) = viewModelScope.launch { settingsRepository.setAccentTheme(index) }
    fun setCustomAccentColor(hex: String) = viewModelScope.launch { settingsRepository.setCustomAccentColor(hex) }
}
