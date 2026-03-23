package com.drivewave.sdr.ui.adaptive

import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Settings
import androidx.navigation.NavHostController
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
 *  - Compact width (phones): bottom bar
 *  - Medium/Expanded (tablets, landscape): navigation rail or drawer
 */
@Composable
fun AdaptiveTunerLayout(
    widthSizeClass: WindowWidthSizeClass,
    currentDest: Dest,
    onDestSelected: (Dest) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val suiteType = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> NavigationSuiteType.NavigationBar
        WindowWidthSizeClass.Medium -> NavigationSuiteType.NavigationRail
        else -> NavigationSuiteType.NavigationDrawer
    }

    NavigationSuiteScaffold(
        modifier = modifier,
        layoutType = suiteType,
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContainerColor = MaterialTheme.colorScheme.surface,
            navigationRailContainerColor = MaterialTheme.colorScheme.surface,
            navigationDrawerContainerColor = MaterialTheme.colorScheme.surface,
        ),
        navigationSuiteItems = {
            navItems.forEach { item ->
                item(
                    selected = currentDest == item.dest,
                    onClick = { onDestSelected(item.dest) },
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = { Text(item.label) },
                )
            }
        },
    ) {
        content()
    }
}
