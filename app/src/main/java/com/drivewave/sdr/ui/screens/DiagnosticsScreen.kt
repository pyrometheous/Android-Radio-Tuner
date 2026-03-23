package com.drivewave.sdr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drivewave.sdr.ui.viewmodel.TunerViewModel
import com.drivewave.sdr.ui.viewmodel.SettingsViewModel

/**
 * Developer diagnostics screen — hidden from normal users.
 * Shows raw signal data, lock status, RDS stats, and tuner state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    tunerViewModel: TunerViewModel,
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by tunerViewModel.radioState.collectAsState()
    val ppm by settingsViewModel.ppmOffset.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Developer Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { DiagRow("Connection State", state.connectionState.name) }
            item { DiagRow("Frequency", "${"%.4f".format(state.currentFrequencyMhz)} MHz") }
            item { DiagRow("Band", state.currentBand.name) }
            item { DiagRow("Signal Power", "${"%.1f".format(state.signalQuality.powerDbm)} dBm") }
            item { DiagRow("SNR", "${"%.1f".format(state.signalQuality.snrDb)} dB") }
            item { DiagRow("Confidence", "${"%.3f".format(state.signalQuality.confidence)}") }
            item { DiagRow("Audio Quieting", "${"%.3f".format(state.signalQuality.audioQuieting)}") }
            item { DiagRow("Stereo Pilot Locked", state.signalQuality.stereoPilotLocked.toString()) }
            item { DiagRow("RDS BLER", "${"%.3f".format(state.signalQuality.rdsBlockErrorRate)}") }
            item { DiagRow("PPM Offset", "$ppm ppm calibration") }
            item { DiagRow("RDS PS", state.currentRdsMetadata.programService ?: "–") }
            item { DiagRow("RDS RT", state.currentRdsMetadata.radioText ?: "–") }
            item { DiagRow("RDS PTY", "${state.currentRdsMetadata.programType} (${state.currentRdsMetadata.programTypeLabel ?: "–"})") }
            item { DiagRow("RDS PI", state.currentRdsMetadata.piCode ?: "–") }
            item { DiagRow("RDS AFs", state.currentRdsMetadata.alternativeFrequencies.joinToString(", ").ifEmpty { "–" }) }
            item { DiagRow("Is Playing", state.isPlaying.toString()) }
            item { DiagRow("Is Muted", state.isMuted.toString()) }
            item { DiagRow("Is Recording", state.isRecording.toString()) }
            item { DiagRow("Error", state.errorMessage ?: "–") }
            item { Spacer(Modifier.height(16.dp)) }
            item {
                Button(
                    onClick = { tunerViewModel.exportDebugLog() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Export Debug Log to Downloads")
                }
            }
            item {
                Text(
                    "Log is saved to Downloads/DriveWave-debug.log. " +
                    "Pull it via USB or open it in a file manager to share.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
        Text(value, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface))
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}
