package com.drivewave.sdr.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drivewave.sdr.domain.model.AudioConfig
import com.drivewave.sdr.domain.model.RegionPreset
import com.drivewave.sdr.ui.theme.TextMuted
import com.drivewave.sdr.ui.theme.radio
import com.drivewave.sdr.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val region by viewModel.regionPreset.collectAsState()
    val ppm by viewModel.ppmOffset.collectAsState()
    val audio by viewModel.audioConfig.collectAsState()
    val devMode by viewModel.developerMode.collectAsState()
    val accent by viewModel.accentTheme.collectAsState()
    var devTapCount by remember { mutableStateOf(0) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── SDR Backend ──────────────────────────────────────────────────
            item { SettingsSectionHeader("SDR Hardware") }
            item {
                ElevatedCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Backend: UI Preview (No Hardware)", style = MaterialTheme.typography.bodyMedium)
                        Text("Connect an RTL-SDR R820T2 / RTL2832U dongle via USB-C OTG to enable real reception.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            // ── Region ───────────────────────────────────────────────────────
            item { SettingsSectionHeader("Region") }
            item {
                ElevatedCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("FM Region Preset", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        RegionPreset.entries.forEach { preset ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = region == preset,
                                    onClick = { viewModel.setRegion(preset) },
                                )
                                Text(preset.displayName, modifier = Modifier.clickable { viewModel.setRegion(preset) })
                            }
                        }
                    }
                }
            }

            // ── Calibration ──────────────────────────────────────────────────
            item { SettingsSectionHeader("Frequency Calibration") }
            item {
                ElevatedCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("PPM Offset: $ppm", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.weight(1f))
                        }
                        Slider(
                            value = ppm.toFloat(),
                            onValueChange = { viewModel.setPpm(it.toInt()) },
                            valueRange = -100f..100f,
                            steps = 199,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.setPpm(0) }, modifier = Modifier.weight(1f)) {
                                Text("Reset", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text(
                            "PPM offset corrects frequency drift in SDR hardware. Typical range: –50 to +50.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                        )
                    }
                }
            }

            // ── Audio ────────────────────────────────────────────────────────
            item { SettingsSectionHeader("Audio & DSP") }
            item {
                ElevatedCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SwitchSettingRow("Mono Mode", audio.monoMode) { viewModel.setAudio(audio.copy(monoMode = it)) }
                        SwitchSettingRow("Soft Mute", audio.softMute) { viewModel.setAudio(audio.copy(softMute = it)) }
                        SwitchSettingRow("Blend to Mono on Weak Signal", audio.blendToMono) { viewModel.setAudio(audio.copy(blendToMono = it)) }
                        SwitchSettingRow("High-Cut (Treble Reduction)", audio.highCutEnabled) { viewModel.setAudio(audio.copy(highCutEnabled = it)) }
                        Column {
                            Text("Noise Reduction: ${"%.0f".format(audio.noiseReduction * 100)}%",
                                style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = audio.noiseReduction,
                                onValueChange = { viewModel.setAudio(audio.copy(noiseReduction = it)) },
                                valueRange = 0f..1f,
                            )
                        }
                        Column {
                            Text("Squelch Threshold: ${"%.0f".format(audio.squelchThreshold * 100)}%",
                                style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = audio.squelchThreshold,
                                onValueChange = { viewModel.setAudio(audio.copy(squelchThreshold = it)) },
                                valueRange = 0f..1f,
                            )
                        }
                    }
                }
            }

            // ── Appearance ───────────────────────────────────────────────────
            item { SettingsSectionHeader("Appearance") }
            item {
                ElevatedCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Accent Color Theme", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                            listOf("Amber", "Cyan", "Red", "Green").forEachIndexed { i, name ->
                                FilterChip(
                                    selected = accent == i,
                                    onClick = { viewModel.setAccentTheme(i) },
                                    label = { Text(name) },
                                )
                            }
                        }
                    }
                }
            }

            // ── Developer ────────────────────────────────────────────────────
            item { SettingsSectionHeader("Developer") }
            item {
                ElevatedCard {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .clickable {
                                if (!devMode) {
                                    devTapCount++
                                    if (devTapCount >= 7) {
                                        viewModel.setDeveloperMode(true)
                                        devTapCount = 0
                                    }
                                }
                            },
                    ) {
                        if (devMode) {
                            SwitchSettingRow("Developer Diagnostics", true) {
                                viewModel.setDeveloperMode(it)
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = onNavigateToDiagnostics) {
                                Text("Open Diagnostics")
                            }
                        } else {
                            Text(
                                "Developer mode is off. Tap here 7 times to enable.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.radio.accent),
        modifier = modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
fun SwitchSettingRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
