package com.drivewave.sdr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.drivewave.sdr.domain.model.*
import com.drivewave.sdr.ui.components.*
import com.drivewave.sdr.ui.viewmodel.TunerViewModel
import kotlinx.coroutines.launch

/**
 * Main tuner screen — single pane for phone layout.
 * Used when WindowSizeClass width is Compact.
 */
@Composable
fun TunerScreen(
    viewModel: TunerViewModel,
    onNavigateToStations: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.radioState.collectAsState()
    val amplitudes by viewModel.waveformAmplitudes.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showDirectTuneDialog by remember { mutableStateOf(false) }

    // Show a brief pop-up each time the scan engine finds a new station
    val stationFound by viewModel.stationFoundEvent.collectAsState(initial = null)
    LaunchedEffect(stationFound) {
        stationFound?.let { station ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Found: ${station.displayName}  •  ${"%.1f".format(station.frequencyMhz)} MHz",
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }

    // Connect on first composition if not already connected
    LaunchedEffect(Unit) {
        if (state.connectionState == SdrConnectionState.NO_DONGLE) {
            viewModel.connectDongle()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        // ── Top bar ───────────────────────────────────────────────────────────
        TunerTopBar(
            connectionState = state.connectionState,
            bandLabel = state.currentBand.name,
            onFavoritesClick = onNavigateToFavorites,
            onSettingsClick = onNavigateToSettings,
        )

        Spacer(Modifier.weight(0.5f))

        // True when no hardware is connected — greys out controls and shows placeholder text
        val noHardware = state.connectionState == SdrConnectionState.NO_DONGLE

        // ── Frequency hero ────────────────────────────────────────────────────
        FrequencyDisplay(
            frequencyText = if (noHardware) "Error" else "%.1f".format(state.currentFrequencyMhz),
            bandLabel = state.currentBand.name,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(8.dp))

        // ── Signal / status badges ────────────────────────────────────────────
        Row(
            modifier = Modifier.padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        ) {
            SignalMeter(
                confidence = state.signalQuality.confidence,
                modifier = Modifier.height(32.dp),
            )
            StatusBadges(
                isStereo = state.signalQuality.stereoPilotLocked,
                hasRds = state.currentRdsMetadata.programService != null,
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Waveform ──────────────────────────────────────────────────────────
        AudioWaveform(
            amplitudes = amplitudes,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 24.dp),
        )

        Spacer(Modifier.height(12.dp))

        // ── Metadata ──────────────────────────────────────────────────────────
        StationMetadataPanel(
            stationName = when {
                noHardware -> "No tuner detected"
                state.connectionState == SdrConnectionState.SCANNING -> "Scanning…"
                else -> state.currentStation?.effectiveName() ?: "Unnamed Station"
            },
            radioText = if (noHardware) null else state.currentRdsMetadata.radioText,
            programType = if (noHardware) null else state.currentRdsMetadata.programTypeLabel,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.weight(1f))

        // ── Main control row ──────────────────────────────────────────────────
        MainControlRow(
            state = state,
            onPrev = viewModel::seekPrevPreset,      // SkipPrev  = jump to saved preset
            onSeekDown = { viewModel.seekToSignal(-1) }, // FastRewind = seek until signal found
            onPlayMute = viewModel::togglePlayMute,
            onSeekUp = { viewModel.seekToSignal(+1) },   // FastForward = seek until signal found
            onNext = viewModel::seekNextPreset,      // SkipNext  = jump to saved preset
            onFavorite = viewModel::toggleFavorite,
        )

        Spacer(Modifier.height(16.dp))

        // ── Secondary action row ──────────────────────────────────────────────
        SecondaryActionRow(
            enabled = !noHardware,
            isScanning = state.connectionState == SdrConnectionState.SCANNING,
            isRecording = state.isRecording,
            onScan = {
                if (state.connectionState == SdrConnectionState.SCANNING) viewModel.stopScan()
                else viewModel.startScan()
            },
            onDirectTune = { showDirectTuneDialog = true },
            onRecordToggle = {
                if (state.isRecording) viewModel.stopRecording()
                else viewModel.startRecording()
            },
            onStations = onNavigateToStations,
            onFavorites = onNavigateToFavorites,
        )

        Spacer(Modifier.height(8.dp))

        // ── Error banner ──────────────────────────────────────────────────────
        state.errorMessage?.let { error ->
            ErrorBanner(message = error)
        }
        }   // end Column
    }       // end Scaffold

    // ── Direct Tune dialog ────────────────────────────────────────────────────
    if (showDirectTuneDialog) {
        DirectTuneDialog(
            allStations = viewModel.allStations.collectAsState().value,
            onTuneFrequency = { freq ->
                viewModel.tune(freq)
                showDirectTuneDialog = false
            },
            onTuneStation = { station ->
                viewModel.tuneToStation(station)
                showDirectTuneDialog = false
            },
            onDismiss = { showDirectTuneDialog = false },
        )
    }
}

// ── Sub-composables ────────────────────────────────────────────────────────────

@Composable
private fun MainControlRow(
    state: RadioState,
    onPrev: () -> Unit,
    onSeekDown: () -> Unit,
    onPlayMute: () -> Unit,
    onSeekUp: () -> Unit,
    onNext: () -> Unit,
    onFavorite: () -> Unit,
) {
    val enabled = state.connectionState != SdrConnectionState.NO_DONGLE &&
            state.connectionState != SdrConnectionState.CONNECTING &&
            state.connectionState != SdrConnectionState.SCANNING

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TunerActionButton(
            icon = Icons.Filled.SkipPrevious,
            contentDescription = "Previous preset station",
            onClick = onPrev,
            enabled = enabled,
            size = 56.dp,
        )
        TunerActionButton(
            icon = Icons.Filled.FastRewind,
            contentDescription = "Seek down — scan for next signal",
            onClick = onSeekDown,
            enabled = enabled,
            size = 56.dp,
        )
        PlayButton(
            isPlaying = state.isPlaying,
            isMuted = state.isMuted,
            onClick = onPlayMute,
            enabled = enabled,
        )
        TunerActionButton(
            icon = Icons.Filled.FastForward,
            contentDescription = "Seek up — scan for next signal",
            onClick = onSeekUp,
            enabled = enabled,
            size = 56.dp,
        )
        TunerActionButton(
            icon = Icons.Filled.SkipNext,
            contentDescription = "Next preset station",
            onClick = onNext,
            enabled = enabled,
            size = 56.dp,
        )
    }

    Spacer(Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        FavoriteButton(
            isFavorite = state.currentStation?.isFavorite == true,
            onClick = onFavorite,
            enabled = enabled,
        )
    }
}

@Composable
private fun FavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    com.drivewave.sdr.ui.components.FavoriteButton(
        isFavorite = isFavorite,
        onClick = onClick,
        modifier = modifier,
        size = 52.dp,
    )
}

@Composable
private fun SecondaryActionRow(
    enabled: Boolean,
    isScanning: Boolean,
    isRecording: Boolean,
    onScan: () -> Unit,
    onDirectTune: () -> Unit,
    onRecordToggle: () -> Unit,
    onStations: () -> Unit,
    onFavorites: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        SecondaryActionButton(
            label = if (isScanning) "Stop" else "Scan",
            icon = if (isScanning) Icons.Filled.Stop else Icons.Filled.Search,
            onClick = onScan,
            enabled = enabled,
            isActive = isScanning,
        )
        SecondaryActionButton(
            label = "Tune",
            icon = Icons.Filled.Dialpad,
            onClick = onDirectTune,
            enabled = enabled,
        )
        SecondaryActionButton(
            label = if (isRecording) "Stop Rec" else "Record",
            icon = if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
            onClick = onRecordToggle,
            enabled = enabled,
            isActive = isRecording,
        )
        SecondaryActionButton(
            label = "Stations",
            icon = Icons.Filled.List,
            onClick = onStations,
            enabled = enabled,
        )
        SecondaryActionButton(
            label = "Presets",
            icon = Icons.Filled.Star,
            onClick = onFavorites,
            enabled = enabled,
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onErrorContainer
                ),
            )
        }
    }
}

@Composable
private fun DirectTuneDialog(
    allStations: List<Station>,
    onTuneFrequency: (Float) -> Unit,
    onTuneStation: (Station) -> Unit,
    onDismiss: () -> Unit,
) {
    var textInput by remember { mutableStateOf(TextFieldValue("")) }
    val query = textInput.text.trim()

    val matchingStations = remember(query, allStations) {
        if (query.isBlank()) emptyList()
        else allStations.filter { station ->
            station.effectiveName().contains(query, ignoreCase = true) ||
                    station.rdsMetadata.radioText?.contains(query, ignoreCase = true) == true ||
                    "%.1f".format(station.frequencyMhz).startsWith(query)
        }.take(8)
    }

    val directFreq = query.toFloatOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Direct Tune") },
        text = {
            Column {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Frequency (e.g. 101.5) or station name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (matchingStations.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Stations:", style = MaterialTheme.typography.labelSmall)
                    matchingStations.forEach { station ->
                        TextButton(onClick = { onTuneStation(station) }) {
                            Text("${station.effectiveName()} – ${"%.1f".format(station.frequencyMhz)} FM")
                        }
                    }
                } else if (query.isNotBlank() && directFreq == null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "No stations found. Run a scan first.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.error
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { directFreq?.let(onTuneFrequency) ?: onDismiss() },
                enabled = directFreq != null,
            ) { Text("Tune") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
