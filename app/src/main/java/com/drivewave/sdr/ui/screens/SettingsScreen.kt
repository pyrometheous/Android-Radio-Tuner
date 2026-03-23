package com.drivewave.sdr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drivewave.sdr.domain.model.AudioConfig
import com.drivewave.sdr.domain.model.RegionPreset
import com.drivewave.sdr.domain.model.SdrConnectionState
import com.drivewave.sdr.ui.theme.PurplePrimary
import com.drivewave.sdr.ui.theme.SignalStrong
import com.drivewave.sdr.ui.theme.TextMuted
import com.drivewave.sdr.ui.theme.parseHexColor
import com.drivewave.sdr.ui.theme.radio
import com.drivewave.sdr.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    backendName: String,
    connectionState: SdrConnectionState,
    onNavigateBack: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val region by viewModel.regionPreset.collectAsState()
    val ppm by viewModel.ppmOffset.collectAsState()
    val audio by viewModel.audioConfig.collectAsState()
    val devMode by viewModel.developerMode.collectAsState()
    val accent by viewModel.accentTheme.collectAsState()
    val customAccentColor by viewModel.customAccentColor.collectAsState()
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
                        Text("Backend: $backendName", style = MaterialTheme.typography.bodyMedium)
                        val (statusText, statusColor) = when (connectionState) {
                            SdrConnectionState.READY, SdrConnectionState.SCANNING,
                            SdrConnectionState.RECORDING ->
                                "Hardware connected and ready." to SignalStrong
                            SdrConnectionState.CONNECTING ->
                                "Connecting…" to MaterialTheme.colorScheme.primary
                            SdrConnectionState.PERMISSION_NEEDED ->
                                "USB permission required. Replug the dongle and tap Allow." to MaterialTheme.colorScheme.error
                            SdrConnectionState.ERROR ->
                                "Connection error. Replug the dongle and try again." to MaterialTheme.colorScheme.error
                            else ->
                                "Connect an RTL-SDR R820T2 / RTL2832U dongle via USB-C OTG to enable real reception." to TextMuted
                        }
                        Text(
                            statusText,
                            style = MaterialTheme.typography.bodySmall.copy(color = statusColor),
                            modifier = Modifier.padding(top = 4.dp),
                        )
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
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf("Amber", "Cyan", "Red", "Green", "Purple", "Custom")
                                .forEachIndexed { i, name ->
                                    FilterChip(
                                        selected = accent == i,
                                        onClick = { viewModel.setAccentTheme(i) },
                                        label = { Text(name) },
                                    )
                                }
                        }

                        // Custom hex color input — shown when Custom (index 5) is selected
                        if (accent == 5) {
                            Spacer(Modifier.height(12.dp))
                            var hexInput by remember(customAccentColor) {
                                mutableStateOf(
                                    (customAccentColor ?: "#BB86FC").trimStart('#').uppercase()
                                )
                            }
                            val previewColor = parseHexColor(hexInput) ?: PurplePrimary
                            val isValidHex = parseHexColor(hexInput) != null

                            Text(
                                "Custom Color (hex)",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // Live color preview swatch
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(previewColor, RoundedCornerShape(8.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                                )
                                OutlinedTextField(
                                    value = hexInput,
                                    onValueChange = { v ->
                                        hexInput = v.uppercase()
                                            .filter { it.isLetterOrDigit() }
                                            .take(6)
                                    },
                                    label = { Text("RRGGBB") },
                                    prefix = { Text("#") },
                                    singleLine = true,
                                    isError = !isValidHex && hexInput.isNotEmpty(),
                                    modifier = Modifier.weight(1f),
                                )
                                Button(
                                    onClick = { viewModel.setCustomAccentColor("#$hexInput") },
                                    enabled = isValidHex,
                                ) {
                                    Text("Apply")
                                }
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
