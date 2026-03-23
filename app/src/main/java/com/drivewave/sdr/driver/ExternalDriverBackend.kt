package com.drivewave.sdr.driver

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.drivewave.sdr.domain.model.RadioBand
import com.drivewave.sdr.domain.model.SignalQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject

/**
 * External driver app backend.
 *
 * Routes SDR control to a compatible third-party driver app installed separately
 * (e.g., the official RTL-SDR Android driver by Henrik Rydberg / steve-m).
 *
 * This is the license-safe alternative to bundling librtlsdr directly.
 * The external driver app holds the GPL code; this app communicates via Android IPC.
 *
 * Common driver apps:
 *  - "RTL-SDR USB driver" (package: marto.rtl_tcp_andro) — exposes rtl_tcp server on localhost
 *  - Others with similar intent-based protocols
 *
 * TODO(external-driver): Implement actual IPC communication:
 *   1. Launch driver app via intent with USB device extra
 *   2. Receive TCP port back via activity result or broadcast
 *   3. Connect to localhost:<port> rtl_tcp protocol
 *   4. Send rtl_tcp commands for tune/gain/sample-rate
 *   5. Read IQ data stream from the TCP socket
 */
class ExternalDriverBackend @Inject constructor(
    @ApplicationContext private val context: Context,
) : SdrBackend {

    override val name: String = "External Driver App (rtl_tcp)"
    override val supportedBands: List<RadioBand> = listOf(RadioBand.FM)
    override val isAvailable: Boolean get() = isDriverAppInstalled()

    private var _isOpen = false
    override val isOpen: Boolean get() = _isOpen
    override val deviceInfo: DeviceInfo? = null

    override suspend fun open(): OpenResult {
        if (!isDriverAppInstalled()) return OpenResult.Error("External driver app not installed. Install 'RTL-SDR USB driver' from the Play Store or sideload a compatible driver.")
        // TODO(external-driver): send intent to launch driver app and bind service
        // TODO(external-driver): wait for service connection and TCP port assignment
        // TODO(external-driver): open TCP socket to 127.0.0.1:<assigned_port>
        _isOpen = true // optimistic; real open will return after TCP handshake
        return OpenResult.Success
    }

    override suspend fun close() {
        // TODO(external-driver): close TCP socket connection
        // TODO(external-driver): send stop intent to driver app
        _isOpen = false
    }

    override suspend fun tune(frequencyMhz: Float, ppmOffset: Int) {
        val frequencyHz = (frequencyMhz * 1_000_000).toLong()
        // TODO(external-driver): send rtl_tcp command 0x01 (set frequency)
        // TODO(external-driver): send rtl_tcp command 0x05 (set frequency correction)
    }

    override suspend fun setGain(gainDb: Float?) {
        if (gainDb == null) {
            // TODO(external-driver): send rtl_tcp command 0x03, value=0 (AGC on)
        } else {
            // TODO(external-driver): send rtl_tcp command 0x03, value=1 (manual gain)
            // TODO(external-driver): send rtl_tcp command 0x04 (set gain in tenths dB)
        }
    }

    override suspend fun setSampleRate(sampleRateHz: Int) {
        // TODO(external-driver): send rtl_tcp command 0x02 (set sample rate)
    }

    override fun iqSampleStream(): Flow<IqSample> {
        // TODO(external-driver): read rtl_tcp IQ data stream from TCP socket
        // After the initial 12-byte magic header, data is 8-bit unsigned IQ pairs
        return emptyFlow()
    }

    override suspend fun measureSignalQuality(): SignalQuality {
        // TODO(external-driver): derive signal quality from streaming IQ data
        return SignalQuality()
    }

    private fun isDriverAppInstalled(): Boolean {
        return DRIVER_PACKAGES.any { pkg ->
            try {
                context.packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    companion object {
        private val DRIVER_PACKAGES = listOf(
            "marto.rtl_tcp_andro",
            "marto.androsdr2",
        )

        private val LAUNCH_INTENT = Intent("marto.rtl_tcp_andro.RtlTcpExternalClient")
    }
}
