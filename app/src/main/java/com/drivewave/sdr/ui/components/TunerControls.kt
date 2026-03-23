package com.drivewave.sdr.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivewave.sdr.ui.theme.RadioCard
import com.drivewave.sdr.ui.theme.RadioElevated
import com.drivewave.sdr.ui.theme.TextPrimary
import com.drivewave.sdr.ui.theme.radio

/**
 * Large round tactile button for the main tuner control row.
 * Big touch target for in-car / large thumb use.
 */
@Composable
fun TunerActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    enabled: Boolean = true,
    isActive: Boolean = false,
    tint: Color? = null,
) {
    val accentColor = MaterialTheme.radio.accent
    val resolvedColor = tint ?: if (isActive) accentColor else TextPrimary

    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (isActive) accentColor.copy(alpha = 0.2f) else RadioElevated,
            contentColor = resolvedColor,
            disabledContainerColor = RadioCard.copy(alpha = 0.5f),
            disabledContentColor = resolvedColor.copy(alpha = 0.3f),
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(size * 0.44f),
        )
    }
}

/**
 * Primary large play button — slightly larger and accented.
 */
@Composable
fun PlayButton(
    isPlaying: Boolean,
    isMuted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val accent = MaterialTheme.radio.accent
    val icon = if (isMuted) Icons.Filled.VolumeOff else if (isPlaying) Icons.Filled.VolumeUp else Icons.Filled.PlayArrow
    val desc = if (isMuted) "Unmute" else if (isPlaying) "Mute" else "Play"

    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(80.dp).semantics { contentDescription = desc },
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = accent,
            contentColor = MaterialTheme.colorScheme.background,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
    }
}

/**
 * Secondary flat pill button used in the bottom action row.
 */
@Composable
fun SecondaryActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isActive: Boolean = false,
) {
    val accent = MaterialTheme.radio.accent
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = label },
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (isActive) accent else MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(
            1.dp,
            if (isActive) accent else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 12.sp)
    }
}

/**
 * Favorite toggle button — heart icon that fills when active.
 */
@Composable
fun FavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder
    val desc = if (isFavorite) "Remove from favorites" else "Add to favorites"
    val tint = if (isFavorite) MaterialTheme.radio.accent else TextPrimary

    TunerActionButton(
        icon = icon,
        contentDescription = desc,
        onClick = onClick,
        size = size,
        modifier = modifier,
        isActive = isFavorite,
        tint = tint,
    )
}
