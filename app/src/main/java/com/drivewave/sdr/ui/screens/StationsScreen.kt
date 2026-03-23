package com.drivewave.sdr.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivewave.sdr.domain.model.Station
import com.drivewave.sdr.domain.model.effectiveName
import com.drivewave.sdr.domain.model.formattedFrequency
import com.drivewave.sdr.ui.components.SignalMeter
import com.drivewave.sdr.ui.components.StationRow
import com.drivewave.sdr.ui.theme.TextMuted
import com.drivewave.sdr.ui.theme.TextSecondary
import com.drivewave.sdr.ui.viewmodel.TunerViewModel

enum class StationSortMode { Frequency, SignalStrength, Name, FavoritesFirst }

/**
 * Master station list — shows results from the most recent scan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationsScreen(
    viewModel: TunerViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stations by viewModel.allStations.collectAsState()
    val currentState by viewModel.radioState.collectAsState()
    var sortMode by remember { mutableStateOf(StationSortMode.Frequency) }
    var showSortMenu by remember { mutableStateOf(false) }

    val sortedStations = remember(stations, sortMode) {
        when (sortMode) {
            StationSortMode.Frequency -> stations.sortedBy { it.frequencyMhz }
            StationSortMode.SignalStrength -> stations.sortedByDescending { it.signalConfidence }
            StationSortMode.Name -> stations.sortedBy { it.effectiveName() }
            StationSortMode.FavoritesFirst -> stations.sortedWith(
                compareByDescending<Station> { it.isFavorite }.thenBy { it.frequencyMhz }
            )
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("All Stations") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::startScan) {
                        Icon(Icons.Filled.Refresh, "Scan")
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Filled.Sort, "Sort")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            StationSortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.name) },
                                    onClick = { sortMode = mode; showSortMenu = false },
                                    leadingIcon = {
                                        if (sortMode == mode) Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        if (sortedStations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Radio, null, modifier = Modifier.size(48.dp), tint = TextMuted)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No stations found.\nTap Scan to discover stations.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = viewModel::startScan) {
                        Icon(Icons.Filled.Search, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scan Now")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                item {
                    Text(
                        "${sortedStations.size} station(s) found",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(sortedStations, key = { it.id }) { station ->
                    StationRow(
                        station = station,
                        isCurrentlyPlaying = station.frequencyMhz == currentState.currentFrequencyMhz,
                        onTune = { viewModel.tuneToStation(station) },
                        onFavoriteToggle = { viewModel.setFavorite(station.id, !station.isFavorite) },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * Favorites screen — preset-style quick access.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: TunerViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val favorites by viewModel.favorites.collectAsState()
    val currentState by viewModel.radioState.collectAsState()
    var showRenameDialog by remember { mutableStateOf<Station?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Favorites") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.FavoriteBorder, null, modifier = Modifier.size(48.dp), tint = TextMuted)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No favorites yet.\nTune to a station and tap ♥ to add it.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(favorites, key = { it.id }) { station ->
                    StationRow(
                        station = station,
                        isCurrentlyPlaying = kotlin.math.abs(station.frequencyMhz - currentState.currentFrequencyMhz) < 0.05f,
                        onTune = { viewModel.tuneToStation(station) },
                        onFavoriteToggle = { viewModel.setFavorite(station.id, false) },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }

    // Rename dialog
    showRenameDialog?.let { station ->
        var label by remember { mutableStateOf(station.effectiveName()) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename Station") },
            text = {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameStation(station.id, label.trim().takeIf { it.isNotBlank() })
                    showRenameDialog = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) { Text("Cancel") }
            },
        )
    }
}
