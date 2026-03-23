package com.drivewave.sdr.ui.viewmodel

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.drivewave.sdr.domain.model.*
import com.drivewave.sdr.domain.repository.SettingsRepository
import com.drivewave.sdr.domain.repository.StationRepository
import com.drivewave.sdr.driver.*
import com.drivewave.sdr.metadata.FakeRdsProvider
import com.drivewave.sdr.recording.RecordingManager
import com.drivewave.sdr.scan.ScanEngine
import com.drivewave.sdr.scan.ScanEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.math.abs

/**
 * Central radio tuner ViewModel.
 * Owns connection lifecycle, tuning, scanning, metadata, and recording state.
 */
@HiltViewModel
class TunerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backendSelector: SdrBackendSelector,
    private val scanEngine: ScanEngine,
    private val stationRepository: StationRepository,
    private val settingsRepository: SettingsRepository,
    private val recordingManager: RecordingManager,
    private val fakeRdsProvider: FakeRdsProvider,
) : ViewModel() {

    private val _radioState = MutableStateFlow(RadioState())
    val radioState: StateFlow<RadioState> = _radioState.asStateFlow()

    private val _waveformAmplitudes = MutableStateFlow(FloatArray(32) { 0f })
    val waveformAmplitudes: StateFlow<FloatArray> = _waveformAmplitudes.asStateFlow()

    private val _uiMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    private var activeBackend: SdrBackend? = null
    private var waveformJob: Job? = null
    private var metadataRefreshJob: Job? = null

    val ppmOffset = settingsRepository.observePpmOffset().stateIn(
        viewModelScope, SharingStarted.Eagerly, 0
    )

    val bandConfig = settingsRepository.observeBandConfig().stateIn(
        viewModelScope, SharingStarted.Eagerly, BandConfig.FM_US
    )

    val allStations = stationRepository.observeAllStations().stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )

    val favorites = stationRepository.observeFavorites().stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )

    init {
        // Observe scan events
        viewModelScope.launch {
            scanEngine.scanEvents.filterNotNull().collect { event ->
                handleScanEvent(event)
            }
        }
    }

    // ── Connection ─────────────────────────────────────────────────────────────

    /** Called by MainActivity when a USB device is physically attached. */
    fun onUsbDeviceAttached() = connectDongle()

    /** Called by MainActivity when a USB device is physically detached. */
    fun onUsbDeviceDetached() = onDongleDisconnected()

    fun connectDongle() {
        viewModelScope.launch {
            _radioState.update { it.copy(connectionState = SdrConnectionState.CONNECTING, errorMessage = null) }
            val backend = backendSelector.selectBackend()
            activeBackend = backend
            when (val result = backend.open()) {
                is OpenResult.Success -> {
                    _radioState.update {
                        it.copy(connectionState = SdrConnectionState.READY)
                    }
                    // Tune to last or default frequency
                    tune(_radioState.value.currentFrequencyMhz)
                    startWaveformSimulation()
                }
                is OpenResult.PermissionDenied -> {
                    _radioState.update {
                        it.copy(
                            connectionState = SdrConnectionState.PERMISSION_NEEDED,
                            errorMessage = "USB permission denied. Please grant access."
                        )
                    }
                }
                is OpenResult.NoDevice -> {
                    _radioState.update {
                        it.copy(
                            connectionState = SdrConnectionState.NO_DONGLE,
                            errorMessage = null
                        )
                    }
                }
                is OpenResult.UnsupportedDevice -> {
                    _radioState.update {
                        it.copy(
                            connectionState = SdrConnectionState.ERROR,
                            errorMessage = "Unsupported hardware. Only RTL2832U / R820T2 dongles are supported."
                        )
                    }
                }
                is OpenResult.Error -> {
                    _radioState.update {
                        it.copy(
                            connectionState = SdrConnectionState.ERROR,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun onDongleDisconnected() {
        viewModelScope.launch {
            activeBackend?.close()
            activeBackend = null
            stopWaveform()
            _radioState.update {
                it.copy(
                    connectionState = SdrConnectionState.NO_DONGLE,
                    isPlaying = false,
                    errorMessage = "Dongle disconnected."
                )
            }
            _uiMessage.tryEmit("Dongle disconnected. Please reconnect the RTL-SDR.")
        }
    }

    // ── Tuning ────────────────────────────────────────────────────────────────

    fun tune(frequencyMhz: Float) {
        viewModelScope.launch {
            val backend = activeBackend ?: return@launch
            val ppm = ppmOffset.value
            backend.tune(frequencyMhz, ppm)
            // Find matching station from the list
            val matchingStation = allStations.value.firstOrNull {
                abs(it.frequencyMhz - frequencyMhz) < 0.051f && it.band == _radioState.value.currentBand
            }
            val rds = if (backend is FakeSdrBackend) {
                fakeRdsProvider.getMetadata(frequencyMhz)
            } else {
                matchingStation?.rdsMetadata ?: RdsMetadata()
            }
            _radioState.update {
                it.copy(
                    currentFrequencyMhz = frequencyMhz,
                    currentStation = matchingStation,
                    currentRdsMetadata = rds,
                    isPlaying = true,
                )
            }
            scheduleMetadataRefresh()
        }
    }

    fun seekNext() {
        val current = _radioState.value.currentFrequencyMhz
        val band = _radioState.value.currentBand
        val next = allStations.value
            .filter { it.band == band && it.frequencyMhz > current + 0.05f }
            .minByOrNull { it.frequencyMhz }
        if (next != null) tune(next.frequencyMhz)
        else tuneStep(+bandConfig.value.stepMhz)
    }

    fun seekPrev() {
        val current = _radioState.value.currentFrequencyMhz
        val band = _radioState.value.currentBand
        val prev = allStations.value
            .filter { it.band == band && it.frequencyMhz < current - 0.05f }
            .maxByOrNull { it.frequencyMhz }
        if (prev != null) tune(prev.frequencyMhz)
        else tuneStep(-bandConfig.value.stepMhz)
    }

    fun tuneStep(stepMhz: Float) {
        val config = bandConfig.value
        val next = (_radioState.value.currentFrequencyMhz + stepMhz)
            .coerceIn(config.startMhz, config.endMhz)
        tune(next)
    }

    fun tuneToStation(station: Station) = tune(station.frequencyMhz)

    // ── Play / Mute ───────────────────────────────────────────────────────────

    fun togglePlayMute() {
        _radioState.update { it.copy(isMuted = !it.isMuted) }
        // TODO(native-backend): apply mute to audio output chain
    }

    // ── Favorites ─────────────────────────────────────────────────────────────

    fun toggleFavorite() {
        val station = _radioState.value.currentStation ?: return
        viewModelScope.launch {
            stationRepository.setFavorite(station.id, !station.isFavorite)
        }
    }

    fun setFavorite(stationId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            stationRepository.setFavorite(stationId, isFavorite)
        }
    }

    fun renameStation(stationId: String, label: String?) {
        viewModelScope.launch {
            stationRepository.setUserLabel(stationId, label)
        }
    }

    // ── Scanning ──────────────────────────────────────────────────────────────

    fun startScan() {
        val backend = activeBackend ?: run {
            _uiMessage.tryEmit("Connect a dongle to start scanning.")
            return
        }
        _radioState.update { it.copy(connectionState = SdrConnectionState.SCANNING) }
        scanEngine.startScan(backend, bandConfig.value, ppmOffset.value, viewModelScope)
    }

    fun stopScan() {
        scanEngine.cancelScan()
    }

    private suspend fun handleScanEvent(event: ScanEvent) {
        when (event) {
            is ScanEvent.Completed -> {
                stationRepository.replaceScannedStations(event.stations, event.sessionId)
                _radioState.update { it.copy(connectionState = SdrConnectionState.READY) }
                _uiMessage.tryEmit("Scan complete. Found ${event.stations.size} station(s).")
            }
            is ScanEvent.Cancelled -> {
                _radioState.update { it.copy(connectionState = SdrConnectionState.READY) }
                _uiMessage.tryEmit("Scan cancelled. Found ${event.stationsSoFar.size} station(s) so far.")
            }
            is ScanEvent.Error -> {
                _radioState.update {
                    it.copy(connectionState = SdrConnectionState.ERROR, errorMessage = event.message)
                }
            }
            is ScanEvent.StationFound -> { /* individual station rows update live via DB flow */ }
            is ScanEvent.Progress -> {
                _radioState.update { it.copy(currentFrequencyMhz = event.currentFrequencyMhz) }
            }
        }
    }

    // ── Recording ─────────────────────────────────────────────────────────────

    fun startRecording() {
        viewModelScope.launch {
            val state = _radioState.value
            val stationName = state.currentStation?.effectiveName() ?: "%.1f FM".format(state.currentFrequencyMhz)
            val result = recordingManager.startRecording(
                stationName = stationName,
                frequencyMhz = state.currentFrequencyMhz,
                band = state.currentBand,
                rdsMetadata = state.currentRdsMetadata,
            )
            result.fold(
                onSuccess = {
                    _radioState.update { it.copy(isRecording = true, connectionState = SdrConnectionState.RECORDING) }
                },
                onFailure = { e ->
                    _uiMessage.tryEmit("Recording failed: ${e.message}")
                }
            )
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            recordingManager.stopRecording()
            _radioState.update {
                it.copy(isRecording = false, connectionState = SdrConnectionState.READY)
            }
            _uiMessage.tryEmit("Recording saved.")
        }
    }

    // ── Waveform simulation ────────────────────────────────────────────────────

    private fun startWaveformSimulation() {
        stopWaveform()
        waveformJob = viewModelScope.launch {
            var t = 0f
            while (isActive) {
                val quality = _radioState.value.signalQuality.audioQuieting
                val amplitudes = FloatArray(32) { i ->
                    val baseWave = kotlin.math.sin(t * 3f + i * 0.4f) * 0.4f +
                            kotlin.math.sin(t * 7f + i * 0.7f) * 0.3f +
                            kotlin.math.sin(t * 13f + i * 0.2f) * 0.3f
                    ((baseWave + 1f) / 2f * quality + (Math.random() * 0.05f).toFloat()).coerceIn(0f, 1f)
                }
                _waveformAmplitudes.value = amplitudes
                t += 0.12f
                delay(50) // 20 fps is fine for waveform
            }
        }
    }

    private fun stopWaveform() {
        waveformJob?.cancel()
        waveformJob = null
        _waveformAmplitudes.value = FloatArray(32) { 0f }
    }

    // ── Metadata refresh ──────────────────────────────────────────────────────

    private fun scheduleMetadataRefresh() {
        metadataRefreshJob?.cancel()
        metadataRefreshJob = viewModelScope.launch {
            delay(3000) // let RDS settle
            val backend = activeBackend ?: return@launch
            val quality = backend.measureSignalQuality()
            _radioState.update {
                it.copy(signalQuality = quality)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            activeBackend?.close()
        }
    }
}

/** Extension to call effectiveName on potentially null Station. */
private fun Station?.effectiveName(): String = this?.let {
    userLabel?.takeIf { l -> l.isNotBlank() }
        ?: rdsMetadata.programService?.takeIf { ps -> ps.isNotBlank() }
        ?: displayName.takeIf { n -> n.isNotBlank() }
        ?: "Unnamed Station"
} ?: "Unknown"
