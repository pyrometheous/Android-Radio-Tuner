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
import androidx.compose.ui.unit.dp
import com.drivewave.sdr.domain.model.Recording
import com.drivewave.sdr.ui.theme.TextMuted
import com.drivewave.sdr.ui.viewmodel.RecordingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    viewModel: RecordingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recordings by viewModel.recordings.collectAsState()
    var deleteTarget by remember { mutableStateOf<Recording?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Recordings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (recordings.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Mic, null, modifier = Modifier.size(48.dp), tint = TextMuted)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No recordings yet.\nTap Record while tuned to a station.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(recordings, key = { it.id }) { recording ->
                    RecordingRow(
                        recording = recording,
                        onDelete = { deleteTarget = recording },
                    )
                }
            }
        }
    }

    deleteTarget?.let { recording ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Recording") },
            text = { Text("Delete \"${recording.fileName}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecording(recording)
                    deleteTarget = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun RecordingRow(
    recording: Recording,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateStr = remember(recording.startedAtEpochMs) {
        SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US).format(Date(recording.startedAtEpochMs))
    }
    val durationStr = remember(recording.durationMs) {
        val mins = TimeUnit.MILLISECONDS.toMinutes(recording.durationMs)
        val secs = TimeUnit.MILLISECONDS.toSeconds(recording.durationMs) % 60
        "%d:%02d".format(mins, secs)
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recording.stationName,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "${recording.fileName}",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                    maxLines = 1,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted),
                    )
                    Text(
                        text = durationStr,
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted),
                    )
                    if (recording.durationMs == 0L) {
                        Text(
                            text = "—",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted),
                        )
                    }
                }
                recording.radioText?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
