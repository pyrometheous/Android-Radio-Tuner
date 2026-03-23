package com.drivewave.sdr.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drivewave.sdr.domain.model.Station
import com.drivewave.sdr.domain.model.effectiveName
import com.drivewave.sdr.domain.model.formattedFrequency
import com.drivewave.sdr.ui.theme.*

/**
 * A single row in the master station list or favorites list.
 */
@Composable
fun StationRow(
    station: Station,
    isCurrentlyPlaying: Boolean,
    onTune: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onTune),
        color = if (isCurrentlyPlaying) MaterialTheme.radio.accentDim.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Signal confidence bar
            Column(
                modifier = Modifier.width(4.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                SignalMeter(
                    confidence = station.signalConfidence,
                    modifier = Modifier.size(width = 20.dp, height = 30.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            // Station info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.effectiveName(),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrentlyPlaying) MaterialTheme.radio.accent else TextPrimary,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${station.formattedFrequency()} ${station.band.name}",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    )
                    station.rdsMetadata.radioText?.takeIf { it.isNotBlank() }?.let { rt ->
                        Text(
                            text = "• $rt",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Favorite button
            IconButton(
                onClick = onFavoriteToggle,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = if (station.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (station.isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (station.isFavorite) MaterialTheme.radio.accent else TextMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
