package com.drivewave.sdr.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.drivewave.sdr.ui.adaptive.AdaptiveTunerLayout
import com.drivewave.sdr.ui.navigation.Dest
import com.drivewave.sdr.ui.screens.DiagnosticsScreen
import com.drivewave.sdr.ui.screens.FavoritesScreen
import com.drivewave.sdr.ui.screens.RecordingsScreen
import com.drivewave.sdr.ui.screens.SettingsScreen
import com.drivewave.sdr.ui.screens.StationsScreen
import com.drivewave.sdr.ui.screens.TunerScreen
import com.drivewave.sdr.ui.theme.DriveWaveTheme
import com.drivewave.sdr.ui.viewmodel.RecordingsViewModel
import com.drivewave.sdr.ui.viewmodel.SettingsViewModel
import com.drivewave.sdr.ui.viewmodel.TunerViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val tunerViewModel: TunerViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val recordingsViewModel: RecordingsViewModel by viewModels()

    // Receives ACTION_USB_DEVICE_ATTACHED when the OS grants permission after the dialog.
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                tunerViewModel.onUsbDeviceAttached()
            } else if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                tunerViewModel.onUsbDeviceDetached()
            }
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Register USB hot-plug receiver.
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        registerReceiver(usbPermissionReceiver, filter, RECEIVER_NOT_EXPORTED)

        // If launched by a USB_DEVICE_ATTACHED intent (from usb_device_filter.xml), notify ViewModel.
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            if (device != null) tunerViewModel.onUsbDeviceAttached()
        }

        setContent {
            val accentIndex by settingsViewModel.accentTheme.collectAsState()
            val developerMode by settingsViewModel.developerMode.collectAsState()
            val windowSizeClass = calculateWindowSizeClass(this)

            DriveWaveTheme(accentIndex = accentIndex) {
                DriveWaveNavGraph(
                    tunerViewModel = tunerViewModel,
                    settingsViewModel = settingsViewModel,
                    recordingsViewModel = recordingsViewModel,
                    widthSizeClass = windowSizeClass.widthSizeClass,
                    developerMode = developerMode,
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbPermissionReceiver)
    }
}

@Composable
private fun DriveWaveNavGraph(
    tunerViewModel: TunerViewModel,
    settingsViewModel: SettingsViewModel,
    recordingsViewModel: RecordingsViewModel,
    widthSizeClass: androidx.compose.material3.windowsizeclass.WindowWidthSizeClass,
    developerMode: Boolean,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Show snackbar on one-shot messages from TunerViewModel.
    val uiMessage by tunerViewModel.uiMessage.collectAsState(initial = null)
    LaunchedEffect(uiMessage) {
        uiMessage?.let { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
    }

    // Resolve current Dest for navigation highlight.
    val currentRoute = backStackEntry?.destination?.route
    val currentDest: Dest = when {
        currentRoute?.contains("Tuner") == true -> Dest.Tuner
        currentRoute?.contains("Favorites") == true -> Dest.Favorites
        currentRoute?.contains("Stations") == true -> Dest.Stations
        currentRoute?.contains("Recordings") == true -> Dest.Recordings
        currentRoute?.contains("Settings") == true -> Dest.Settings
        currentRoute?.contains("Diagnostics") == true -> Dest.Diagnostics
        else -> Dest.Tuner
    }

    // Top-level destinations shown in the navigation bar/rail (Diagnostics is hidden).
    val topLevelDests = setOf(Dest.Tuner, Dest.Stations, Dest.Favorites, Dest.Recordings, Dest.Settings)
    val showNavigation = currentDest in topLevelDests

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (showNavigation) {
            AdaptiveTunerLayout(
                widthSizeClass = widthSizeClass,
                currentDest = currentDest,
                onDestSelected = { dest ->
                    navController.navigate(dest) {
                        popUpTo(Dest.Tuner) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.padding(innerPadding),
            ) {
                AppNavHost(
                    navController = navController,
                    tunerViewModel = tunerViewModel,
                    settingsViewModel = settingsViewModel,
                    recordingsViewModel = recordingsViewModel,
                    developerMode = developerMode,
                )
            }
        } else {
            AppNavHost(
                navController = navController,
                tunerViewModel = tunerViewModel,
                settingsViewModel = settingsViewModel,
                recordingsViewModel = recordingsViewModel,
                developerMode = developerMode,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun AppNavHost(
    navController: androidx.navigation.NavHostController,
    tunerViewModel: TunerViewModel,
    settingsViewModel: SettingsViewModel,
    recordingsViewModel: RecordingsViewModel,
    developerMode: Boolean,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Dest.Tuner,
        modifier = modifier.fillMaxSize(),
    ) {
        composable<Dest.Tuner> {
            TunerScreen(
                viewModel = tunerViewModel,
                onNavigateToStations = { navController.navigate(Dest.Stations) },
                onNavigateToFavorites = { navController.navigate(Dest.Favorites) },
                onNavigateToSettings = { navController.navigate(Dest.Settings) },
            )
        }
        composable<Dest.Stations> {
            StationsScreen(
                viewModel = tunerViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable<Dest.Favorites> {
            FavoritesScreen(
                viewModel = tunerViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable<Dest.Recordings> {
            RecordingsScreen(
                viewModel = recordingsViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable<Dest.Settings> {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDiagnostics = {
                    if (developerMode) navController.navigate(Dest.Diagnostics)
                },
            )
        }
        composable<Dest.Diagnostics> {
            DiagnosticsScreen(
                tunerViewModel = tunerViewModel,
                settingsViewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
