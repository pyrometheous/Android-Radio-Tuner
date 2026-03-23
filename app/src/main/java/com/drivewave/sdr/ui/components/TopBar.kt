package com.drivewave.sdr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivewave.sdr.domain.model.SdrConnectionState
import com.drivewave.sdr.ui.theme.*

/**
 * Top bar showing device connection state and navigation shortcuts.
 */
@Composable
fun TunerTopBar(
    connectionState: SdrConnectionState,
    bandLabel: String,
    onFavoritesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Device status indicator
        ConnectionIndicator(state = connectionState)

        // Band label
        Text(
            text = bandLabel,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                color = MaterialTheme.radio.accent,
            ),
        )

        // Action icons
        Row {
            IconButton(onClick = onFavoritesClick) {
                Icon(Icons.Filled.Favorite, "Favorites", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Filled.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun ConnectionIndicator(
    state: SdrConnectionState,
    modifier: Modifier = Modifier,
) {
    val (label, color) = when (state) {
        SdrConnectionState.NO_DONGLE -> "No Dongle" to TextMuted
        SdrConnectionState.PERMISSION_NEEDED -> "Permission" to SignalWeak
        SdrConnectionState.CONNECTING -> "Connecting…" to SignalMedium
        SdrConnectionState.READY -> "Ready" to SignalStrong
        SdrConnectionState.SCANNING -> "Scanning" to AmberPrimary
        SdrConnectionState.RECORDING -> "Recording" to RedPrimary
        SdrConnectionState.ERROR -> "Error" to RedPrimary
    }
    Row(
        modifier = modifier.semantics { contentDescription = "Device status: $label" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(50))
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = color,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
            ),
        )
    }
}

/**
 * Metadata area below waveform — shows station name, RT text, artist/song.
 */
@Composable
fun StationMetadataPanel(
    stationName: String,
    radioText: String?,
    programType: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stationName,
            style = MaterialTheme.typography.titleMedium.copy(
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!radioText.isNullOrBlank()) {
            Text(
                text = radioText,
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!programType.isNullOrBlank()) {
            Text(
                text = programType,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                ),
            )
        }
    }
}
