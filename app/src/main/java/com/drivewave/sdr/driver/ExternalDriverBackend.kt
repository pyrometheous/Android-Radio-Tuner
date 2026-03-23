package com.drivewave.sdr.driver

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.drivewave.sdr.domain.model.RadioBand
import com.drivewave.sdr.domain.model.SignalQuality
import com.drivewave.sdr.dsp.FmDemodulator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import javax.inject.Inject
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * External driver app backend using the rtl\_tcp protocol.
 *
 * Connects to an [rtl\_tcp](https://github.com/merbanan/rtl-sdr) server running on localhost:1234.
 * The server is provided by the free "RTL-SDR Driver" app (package marto.rtl_tcp_andro)
 * available on the Play Store or via sideload.
 *
 * Protocol overview:
 *   Server → client: 12-byte header ("RTL0" magic + tuner type + gain count), then
 *                     a continuous stream of unsigned IQ byte pairs (I,Q, 0-255, centre 127).
 *   Client → server: 5-byte commands (1 byte type + 4 bytes big-endian param) for
 *                     frequency (0x01), samplerate (0x02), gain mode (0x03), gain (0x04),
 *                     PPM correction (0x05), AGC (0x08).
 *
 * Usage:
 *   1. Install the RTL-SDR driver app from the Play Store.
 *   2. Plug in your RTL-SDR dongle via USB OTG.
 *   3. Open the driver app, grant USB permission when prompted, tap “Start server”.
 *   4. Launch this app — it will connect automatically.
 */
class ExternalDriverBackend @Inject constructor(
    @ApplicationContext private val context: Context,
) : SdrBackend {

    override val name: String = "External Driver App (rtl_tcp)"
    override val supportedBands: List<RadioBand> = listOf(RadioBand.FM)
    override val isAvailable: Boolean get() = isDriverAppInstalled()

    private var _isOpen = false
    override val isOpen: Boolean get() = _isOpen
    override var deviceInfo: DeviceInfo? = null
        private set

    @Volatile private var _socket: Socket? = null
    @Volatile private var _out: OutputStream? = null
    @Volatile private var _currentFreqMhz = 100.0f
    /** Latest IQ block captured by iqSampleStream — used by measureSignalQuality. */
    @Volatile private var _lastIqSnapshot: ByteArray? = null

    override suspend fun open(): OpenResult = withContext(Dispatchers.IO) {
        if (!isDriverAppInstalled()) {
            return@withContext OpenResult.Error(
                "RTL-SDR driver app not installed. " +
                "Search the Play Store for ‘RTL-SDR Driver’ (marto.rtl_tcp_andro), " +
                "install it, plug in your dongle, open the app, and tap ‘Start server’."
            )
        }

        // Attempt connection; on first refusal try to bring the driver app to the foreground.
        for (attempt in 0..2) {
            try {
                val s = Socket()
                s.connect(InetSocketAddress("127.0.0.1", 1234), 2_000)
                s.soTimeout = 5_000

                // Read the 12-byte rtl_tcp header.
                val header = ByteArray(12)
                var got = 0
                while (got < 12) {
                    val n = s.inputStream.read(header, got, 12 - got)
                    if (n < 0) break
                    got += n
                }

                // Verify "RTL0" magic (0x52 0x54 0x4C 0x30).
                if (got >= 4 &&
                    header[0] == 0x52.toByte() &&
                    header[1] == 0x54.toByte() &&
                    header[2] == 0x4C.toByte()) {

                    _socket = s
                    _out    = s.outputStream

                    // Configure sample rate to match FM demodulator input.
                    sendCmd(0x02, FmDemodulator.INPUT_RATE.toLong())
                    // Enable hardware AGC (safest default for broadcast FM).
                    sendCmd(0x03, 0L)   // gain mode = auto
                    sendCmd(0x08, 1L)   // RTL AGC on

                    deviceInfo = DeviceInfo(
                        vendorId         = 0x0BDA,
                        productId        = 0x2838,
                        serialNumber     = "EXT-TCP-001",
                        manufacturerName = "rtl_tcp server",
                        productName      = "External RTL-SDR",
                    )
                    _isOpen = true
                    return@withContext OpenResult.Success
                }
                s.close()

            } catch (_: ConnectException) {
                if (attempt == 0) tryBringDriverToForeground()
                delay(1_500)
            } catch (_: Exception) {
                delay(500)
            }
        }

        OpenResult.Error(
            "Could not connect to rtl_tcp on 127.0.0.1:1234. " +
            "Open the RTL-SDR driver app, confirm USB permission, and tap ‘Start server’."
        )
    }

    override suspend fun close() {
        _isOpen = false
        withContext(Dispatchers.IO) {
            try { _socket?.close() } catch (_: Exception) {}
        }
        _socket = null
        _out    = null
        deviceInfo = null
    }

    override suspend fun tune(frequencyMhz: Float, ppmOffset: Int) {
        _currentFreqMhz = frequencyMhz
        withContext(Dispatchers.IO) {
            sendCmd(0x01, (frequencyMhz * 1_000_000L).toLong())
            if (ppmOffset != 0) sendCmd(0x05, ppmOffset.toLong())
        }
    }

    override suspend fun setGain(gainDb: Float?) {
        withContext(Dispatchers.IO) {
            if (gainDb == null) {
                sendCmd(0x03, 0L); sendCmd(0x08, 1L)
            } else {
                sendCmd(0x03, 1L)
                sendCmd(0x04, (gainDb * 10).toLong().coerceIn(0, 500))
            }
        }
    }

    override suspend fun setSampleRate(sampleRateHz: Int) {
        withContext(Dispatchers.IO) { sendCmd(0x02, sampleRateHz.toLong()) }
    }

    /**
     * Streams raw IQ blocks from the rtl_tcp server.
     * Each block is 65,536 bytes (32,768 IQ pairs) at [FmDemodulator.INPUT_RATE] Hz ≈ 68 ms.
     * Bytes are unsigned 0–255 stored as signed Java bytes (RTL-SDR standard format).
     */
    override fun iqSampleStream(): Flow<IqSample> = flow {
        val s = _socket ?: return@flow
        val blockBytes = 65_536
        val buf = ByteArray(blockBytes)

        try {
            while (currentCoroutineContext().isActive) {
                val n = withContext(Dispatchers.IO) {
                    try {
                        var read = 0
                        while (read < blockBytes) {
                            val r = s.inputStream.read(buf, read, blockBytes - read)
                            if (r < 0) return@withContext -1
                            read += r
                        }
                        read
                    } catch (_: SocketTimeoutException) { 0 }
                      catch (_: Exception) { -1 }
                }
                if (n < 0) break
                if (n > 0) {
                    // Keep a snapshot for measureSignalQuality (first 8 KB is enough).
                    _lastIqSnapshot = buf.copyOfRange(0, minOf(8_192, n))
                    emit(IqSample(
                        data               = buf.copyOf(n),
                        sampleRateHz       = FmDemodulator.INPUT_RATE,
                        centerFrequencyMhz = _currentFreqMhz,
                        timestampNs        = System.nanoTime(),
                    ))
                }
            }
        } catch (_: Exception) { /* socket closed */ }
    }

    /**
     * Estimates signal quality from the most recent IQ snapshot.
     * Returns default (weak signal) if no data has been collected yet.
     */
    override suspend fun measureSignalQuality(): SignalQuality {
        return _lastIqSnapshot?.let { computeSignalQuality(it) } ?: SignalQuality()
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Computes signal quality metrics from a block of unsigned RTL-SDR IQ bytes.
     * Power is estimated from the RMS magnitude; confidence is mapped from
     * power with a simple linear model calibrated for broadcast FM.
     */
    private fun computeSignalQuality(iq: ByteArray): SignalQuality {
        var sumSq = 0.0
        val pairs = iq.size / 2
        for (i in 0 until pairs * 2 step 2) {
            val iv = ((iq[i].toInt()     and 0xFF) - 127) / 128.0
            val qv = ((iq[i + 1].toInt() and 0xFF) - 127) / 128.0
            sumSq += iv * iv + qv * qv
        }
        val rms       = sqrt(sumSq / pairs.coerceAtLeast(1).toDouble())
        // Calibration: 0 dBFS RMS ≈ -20 dBm for a typical RTL-SDR with AGC.
        val powerDbm  = if (rms > 1e-9) (20.0 * log10(rms) - 20.0).toFloat() else -100f
        val snrDb     = (powerDbm + 80f).coerceIn(0f, 40f)
        // A broadcast FM station normally appears above -65 dBm at the antenna.
        // Map -70 dBm → 0.0 and -35 dBm → 1.0 for confidence.
        val confidence = ((powerDbm + 70f) / 35f).coerceIn(0f, 1f)
        return SignalQuality(
            powerDbm          = powerDbm,
            snrDb             = snrDb,
            audioQuieting     = confidence,
            confidence        = confidence,
            rdsBlockErrorRate = (1f - confidence).coerceIn(0f, 1f),
        )
    }

    private fun isDriverAppInstalled(): Boolean =
        DRIVER_PACKAGES.any { pkg ->
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) { false }
        }

    /** Bring the driver app’s main activity to the foreground so the user can start the server. */
    private fun tryBringDriverToForeground() {
        for (pkg in DRIVER_PACKAGES) {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: continue
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(intent); return } catch (_: Exception) {}
        }
    }

    /** Send a 5-byte rtl_tcp command: 1 byte type + 4 bytes big-endian uint32 parameter. */
    private fun sendCmd(command: Int, value: Long) {
        val out = _out ?: return
        val buf = ByteArray(5)
        buf[0] = command.toByte()
        buf[1] = ((value ushr 24) and 0xFF).toByte()
        buf[2] = ((value ushr 16) and 0xFF).toByte()
        buf[3] = ((value ushr 8)  and 0xFF).toByte()
        buf[4] = ( value          and 0xFF).toByte()
        try { out.write(buf); out.flush() } catch (_: Exception) {}
    }

    companion object {
        /** Known package names for RTL-SDR driver apps that expose an rtl_tcp server. */
        private val DRIVER_PACKAGES = listOf(
            "marto.rtl_tcp_andro",   // most common, free
            "marto.androsdr2",        // AndroSDR variant
        )
    }
}
