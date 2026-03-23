package com.drivewave.sdr.ui.adaptive

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.drivewave.sdr.ui.navigation.Dest

data class NavItem(
    val dest: Dest,
    val label: String,
    val icon: ImageVector,
)

private val navItems = listOf(
    NavItem(Dest.Tuner, "Tuner", Icons.Filled.Radio),
    NavItem(Dest.Stations, "Stations", Icons.Filled.List),
    NavItem(Dest.Favorites, "Favorites", Icons.Filled.Favorite),
    NavItem(Dest.Recordings, "Recordings", Icons.Filled.FiberManualRecord),
    NavItem(Dest.Settings, "Settings", Icons.Filled.Settings),
)

/**
 * Wraps the app content with adaptive navigation:
 *  - Compact width (phones): NavigationBar at the bottom
 *  - Medium/Expanded (tablets, landscape): NavigationRail on the left
 */
@Composable
fun AdaptiveTunerLayout(
    widthSizeClass: WindowWidthSizeClass,
    currentDest: Dest,
    onDestSelected: (Dest) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (widthSizeClass == WindowWidthSizeClass.Compact) {
        // Phone: bottom navigation bar
        Column(modifier = modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
            NavigationBar {
                navItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentDest == item.dest,
                        onClick = { onDestSelected(item.dest) },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        }
    } else {
        // Tablet/landscape: navigation rail on the left
        Row(modifier = modifier.fillMaxSize()) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                Spacer(Modifier.height(8.dp))
                navItems.forEach { item ->
                    NavigationRailItem(
                        selected = currentDest == item.dest,
                        onClick = { onDestSelected(item.dest) },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                content()
            }
        }
    }
}

