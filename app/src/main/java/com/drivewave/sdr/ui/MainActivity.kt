package com.drivewave.sdr.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.drivewave.sdr.domain.model.SdrConnectionState
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
import com.drivewave.sdr.ui.viewmodel.TunerViewModel.Companion.USB_PERMISSION_ACTION

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val tunerViewModel: TunerViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val recordingsViewModel: RecordingsViewModel by viewModels()

    // Receives ACTION_USB_DEVICE_DETACHED when the dongle is physically removed.
    // RECEIVER_EXPORTED is required for system-protected broadcasts on Android 12+.
    private val usbDetachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                tunerViewModel.onUsbDeviceDetached()
            }
        }
    }

    // Receives USB_PERMISSION_ACTION after the user responds to the system permission dialog.
    // NOT_EXPORTED — this intent is only sent by us via PendingIntent.
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == USB_PERMISSION_ACTION) {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (granted) {
                    tunerViewModel.onUsbDeviceAttached()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Register detach receiver. RECEIVER_EXPORTED required for system USB broadcasts.
        registerReceiver(
            usbDetachReceiver,
            IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED),
            RECEIVER_EXPORTED,
        )

        // Register USB permission receiver. NOT_EXPORTED — PendingIntent is app-internal only.
        registerReceiver(
            usbPermissionReceiver,
            IntentFilter(USB_PERMISSION_ACTION),
            RECEIVER_NOT_EXPORTED,
        )

        // Handle USB ATTACHED intent from manifest filter (cold start or permission just granted).
        handleUsbIntent(intent)

        setContent {
            val accentIndex by settingsViewModel.accentTheme.collectAsState()
            val customAccentHex by settingsViewModel.customAccentColor.collectAsState()
            val developerMode by settingsViewModel.developerMode.collectAsState()
            val windowSizeClass = calculateWindowSizeClass(this)

            DriveWaveTheme(accentIndex = accentIndex, customAccentHex = customAccentHex) {
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
        unregisterReceiver(usbDetachReceiver)
        unregisterReceiver(usbPermissionReceiver)
    }

    // Called when singleTask activity is re-used (e.g. USB attach while already running).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUsbIntent(intent)
    }

    private fun handleUsbIntent(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            tunerViewModel.onUsbDeviceAttached()
        }
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
            val backendName by tunerViewModel.activeBackendName.collectAsState()
            val connectionState by tunerViewModel.radioState.collectAsState()
            SettingsScreen(
                viewModel = settingsViewModel,
                backendName = backendName,
                connectionState = connectionState.connectionState,
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
