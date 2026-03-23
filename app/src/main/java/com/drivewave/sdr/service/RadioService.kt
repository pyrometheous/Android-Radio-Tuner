package com.drivewave.sdr.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.drivewave.sdr.R
import com.drivewave.sdr.domain.model.SdrConnectionState
import com.drivewave.sdr.driver.SdrBackend
import com.drivewave.sdr.driver.SdrBackendSelector
import com.drivewave.sdr.scan.ScanEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that hosts SDR radio playback, scanning, and recording.
 *
 * Runs as a mediaPlayback + microphone foreground service to survive:
 * - Screen off
 * - App backgrounded
 * - USB attach/detach events (handled via event bus / ViewModel)
 *
 * The service maintains a [CoroutineScope] for all long-running radio work.
 * UI components observe StateFlow from the ViewModel (via the RadioEngine).
 */
@AndroidEntryPoint
class RadioService : Service() {

    @Inject lateinit var backendSelector: SdrBackendSelector
    @Inject lateinit var scanEngine: ScanEngine

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var activeBackend: SdrBackend? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID_RADIO, buildRadioNotification("DriveWave SDR", "Ready"))
        when (intent?.action) {
            ACTION_START_RADIO -> startRadio()
            ACTION_STOP_RADIO -> stopRadio()
            ACTION_MUTE -> handleMute()
            ACTION_SKIP_NEXT -> handleSkipNext()
            ACTION_SKIP_PREV -> handleSkipPrev()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startRadio() {
        serviceScope.launch {
            val backend = backendSelector.selectBackend()
            activeBackend = backend
            // TODO(native-backend): open backend, start FM demodulation loop
            // Emit updated state through RadioStateRepository / shared StateFlow
        }
    }

    private fun stopRadio() {
        serviceScope.launch {
            activeBackend?.close()
            activeBackend = null
        }
        stopSelf()
    }

    private fun handleMute() {
        // TODO: notify viewmodel/engine to toggle mute
    }

    private fun handleSkipNext() {
        // TODO: notify viewmodel/engine to seek next station
    }

    private fun handleSkipPrev() {
        // TODO: notify viewmodel/engine to seek previous station
    }

    private fun buildRadioNotification(title: String, subtitle: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val muteIntent = PendingIntent.getService(
            this, 1,
            Intent(this, RadioService::class.java).setAction(ACTION_MUTE),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val prevIntent = PendingIntent.getService(
            this, 2,
            Intent(this, RadioService::class.java).setAction(ACTION_SKIP_PREV),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val nextIntent = PendingIntent.getService(
            this, 3,
            Intent(this, RadioService::class.java).setAction(ACTION_SKIP_NEXT),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_RADIO)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Prev", prevIntent)
            .addAction(android.R.drawable.ic_media_pause, "Mute", muteIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RADIO, "Radio Playback", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Ongoing radio reception notification"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RECORDING, "Recording", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Active recording notification"
                setShowBadge(false)
            }
        )
    }

    companion object {
        const val ACTION_START_RADIO = "com.drivewave.sdr.START_RADIO"
        const val ACTION_STOP_RADIO = "com.drivewave.sdr.STOP_RADIO"
        const val ACTION_MUTE = "com.drivewave.sdr.MUTE"
        const val ACTION_SKIP_NEXT = "com.drivewave.sdr.SKIP_NEXT"
        const val ACTION_SKIP_PREV = "com.drivewave.sdr.SKIP_PREV"

        private const val NOTIFICATION_ID_RADIO = 1001
        private const val CHANNEL_RADIO = "drivewave_radio"
        private const val CHANNEL_RECORDING = "drivewave_recording"
    }
}
