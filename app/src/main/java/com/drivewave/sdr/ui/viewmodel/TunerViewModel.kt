package com.drivewave.sdr.ui.viewmodel

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import com.drivewave.sdr.service.RadioService
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

    private val _activeBackendName = MutableStateFlow("UI Preview (No Hardware)")
    val activeBackendName: StateFlow<String> = _activeBackendName.asStateFlow()

    /** Emits a station each time the scan engine confirms a new find — drives pop-up toasts. */
    private val _stationFoundEvent = MutableSharedFlow<Station>(extraBufferCapacity = 8)
    val stationFoundEvent: SharedFlow<Station> = _stationFoundEvent.asSharedFlow()

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
                    _activeBackendName.value = backend.name
                    // Launch foreground service for background audio output
                    context.startService(
                        Intent(context, RadioService::class.java)
                            .setAction(RadioService.ACTION_START_RADIO)
                    )
                    _radioState.update {
                        it.copy(connectionState = SdrConnectionState.READY)
                    }
                    // Tune to last or default frequency
                    tune(_radioState.value.currentFrequencyMhz)
                    startWaveformSimulation()
                }
                is OpenResult.PermissionDenied -> {
                    // Request USB permission; the system dialog will trigger
                    // ACTION_USB_DEVICE_ATTACHED in MainActivity which calls onUsbDeviceAttached()
                    // again when the user grants access.
                    requestUsbPermission()
                    _radioState.update {
                        it.copy(
                            connectionState = SdrConnectionState.PERMISSION_NEEDED,
                            errorMessage = "USB permission needed — tap Allow in the system dialog."
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
            _activeBackendName.value = "UI Preview (No Hardware)"
            context.startService(
                Intent(context, RadioService::class.java)
                    .setAction(RadioService.ACTION_STOP_RADIO)
            )
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

    // ── Station navigation ────────────────────────────────────

    /**
     * Auto-seek: step the tuner in [direction] (+1 = up, -1 = down) by [bandConfig.stepMhz]
     * until a signal above [SEEK_THRESHOLD] is found or the full band is swept.
     * Wraps around at band edges.
     */
    fun seekToSignal(direction: Int) {
        val backend = activeBackend ?: return
        viewModelScope.launch {
            val config = bandConfig.value
            val step = config.stepMhz * direction
            var freq = _radioState.value.currentFrequencyMhz
            val maxSteps = ((config.endMhz - config.startMhz) / config.stepMhz).toInt() + 2
            repeat(maxSteps) {
                freq += step
                // Wrap at band edges
                if (freq > config.endMhz)   freq = config.startMhz
                if (freq < config.startMhz) freq = config.endMhz
                backend.tune(freq, ppmOffset.value)
                delay(SEEK_SETTLE_MS)
                val quality = backend.measureSignalQuality()
                if (quality.confidence >= SEEK_THRESHOLD) {
                    tune(freq)
                    return@launch
                }
            }
            // No signal found — stay at last position
            tune(freq)
        }
    }

    /** Jump to the next scanned/saved station above current frequency (wraps). */
    fun seekNextPreset() {
        val current = _radioState.value.currentFrequencyMhz
        val band    = _radioState.value.currentBand
        val stations = allStations.value.filter { it.band == band }
        val next = stations
            .filter { it.frequencyMhz > current + 0.05f }
            .minByOrNull { it.frequencyMhz }
            ?: stations.minByOrNull { it.frequencyMhz }  // wrap around
        if (next != null) tune(next.frequencyMhz)
        else tuneStep(+bandConfig.value.stepMhz)
    }

    /** Jump to the previous scanned/saved station below current frequency (wraps). */
    fun seekPrevPreset() {
        val current  = _radioState.value.currentFrequencyMhz
        val band     = _radioState.value.currentBand
        val stations = allStations.value.filter { it.band == band }
        val prev = stations
            .filter { it.frequencyMhz < current - 0.05f }
            .maxByOrNull { it.frequencyMhz }
            ?: stations.maxByOrNull { it.frequencyMhz }  // wrap around
        if (prev != null) tune(prev.frequencyMhz)
        else tuneStep(-bandConfig.value.stepMhz)
    }

    /** @deprecated Use seekNextPreset / seekPrevPreset for preset navigation. */
    fun seekNext() = seekNextPreset()
    fun seekPrev() = seekPrevPreset()

    // ── USB permission ────────────────────────────────────────────────────────

    private fun requestUsbPermission() {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val device = usbManager.deviceList.values.firstOrNull { d ->
            RTL_SDR_IDS.any { (v, p) -> d.vendorId == v && d.productId == p }
        } ?: return
        val pi = PendingIntent.getBroadcast(
            context, 0,
            Intent(USB_PERMISSION_ACTION),
            PendingIntent.FLAG_IMMUTABLE
        )
        usbManager.requestPermission(device, pi)
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
            is ScanEvent.StationFound -> {
                // Save immediately so the Stations list updates in real-time
                stationRepository.upsertStation(event.station)
                // Emit event so TunerScreen can show a brief pop-up
                _stationFoundEvent.tryEmit(event.station)
            }
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

    companion object {
        private const val SEEK_THRESHOLD = 0.35f   // matches ScanEngine.CONFIDENCE_THRESHOLD
        private const val SEEK_SETTLE_MS = 80L     // ms to wait after tune before quality check
        const val USB_PERMISSION_ACTION = "com.drivewave.sdr.USB_PERMISSION"
        private val RTL_SDR_IDS = listOf(
            0x0BDA to 0x2832, 0x0BDA to 0x2838, 0x0BDA to 0x2831,
            0x0BDA to 0x2840, 0x0BDA to 0x2836
        )
    }
}

/** Extension to call effectiveName on potentially null Station. */
private fun Station?.effectiveName(): String = this?.let {
    userLabel?.takeIf { l -> l.isNotBlank() }
        ?: rdsMetadata.programService?.takeIf { ps -> ps.isNotBlank() }
        ?: displayName.takeIf { n -> n.isNotBlank() }
        ?: "Unnamed Station"
} ?: "Unknown"
