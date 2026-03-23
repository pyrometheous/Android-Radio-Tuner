package com.drivewave.sdr.driver

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.drivewave.sdr.domain.model.RadioBand
import com.drivewave.sdr.domain.model.SignalQuality
import com.drivewave.sdr.dsp.FmDemodulator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import javax.inject.Inject

/**
 * Native RTL-SDR backend using JNI bridging to librtlsdr.
 *
 * This backend handles real RTL2832U / R820T2 hardware over USB OTG / USB-C host.
 *
 * Integration status: STUB — all hardware calls are marked TODO.
 * To complete native integration:
 *   1. Add the librtlsdr prebuilt .so files for arm64-v8a / x86_64 to app/src/main/jniLibs/
 *   2. Implement native/jni/SdrBridge.cpp calling the librtlsdr C API
 *   3. Generate JNI bindings or use JNA
 *   4. Replace every TODO block below with actual JNI calls
 *
 * Licensing note: librtlsdr is GPLv2. If you bundle it, the app must comply with
 * GPLv2 terms (source availability). Consult your legal team. As a license-safe
 * alternative, route through the official RTL-SDR Android driver app using
 * [ExternalDriverBackend] and the documented intent-based IPC protocol.
 */
class RtlSdrNativeBackend @Inject constructor(
    @ApplicationContext private val context: Context,
) : SdrBackend {

    override val name: String = "RTL-SDR Native (RTL2832U / R820T2)"
    override val supportedBands: List<RadioBand> = listOf(RadioBand.FM)
    // Available when a matching USB device is physically present — permission is handled in open().
    override val isAvailable: Boolean get() = findRtlSdrDevice() != null

    private var _isOpen = false
    override val isOpen: Boolean get() = _isOpen
    override var deviceInfo: DeviceInfo? = null
        private set

    // Active USB connection managed across open()/iqSampleStream()/close()
    @Volatile private var _usbConnection: UsbDeviceConnection? = null
    @Volatile private var _usbInterface: UsbInterface? = null
    @Volatile private var _currentFrequencyMhz = 0f

    private val usbManager: UsbManager
        get() = context.getSystemService(Context.USB_SERVICE) as UsbManager

    override suspend fun open(): OpenResult {
        val device = findRtlSdrDevice() ?: return OpenResult.NoDevice

        if (!usbManager.hasPermission(device)) {
            return OpenResult.PermissionDenied
        }

        // TODO(native-backend): open USB device via UsbManager.openDevice()
        // TODO(native-backend): claim USB interface (interface 0)
        // TODO(native-backend): call rtlsdr_open() or equivalent via JNI
        // TODO(native-backend): call rtlsdr_set_tuner_gain_mode(0) for AGC
        // TODO(native-backend): call rtlsdr_reset_buffer()
        // TODO(native-backend): populate deviceInfo from USB descriptor + rtlsdr_get_device_name()

        deviceInfo = DeviceInfo(
            vendorId = device.vendorId,
            productId = device.productId,
            serialNumber = device.serialNumber ?: "UNKNOWN",
            manufacturerName = device.manufacturerName,
            productName = device.productName,
        )

        _isOpen = true
        return OpenResult.Success
    }

    override suspend fun close() {
        // TODO(native-backend): call rtlsdr_close() via JNI
        // TODO(native-backend): release USB interface and close UsbDeviceConnection
        _usbInterface?.let { _usbConnection?.releaseInterface(it) }
        _usbConnection?.close()
        _usbConnection = null
        _usbInterface = null
        _isOpen = false
        deviceInfo = null
    }

    override suspend fun tune(frequencyMhz: Float, ppmOffset: Int) {
        require(_isOpen) { "Backend not open" }
        _currentFrequencyMhz = frequencyMhz
        // TODO(native-backend): call rtlsdr_set_center_freq(dev, frequencyHz)
        // TODO(native-backend): call rtlsdr_set_freq_correction(dev, ppmOffset)
    }

    override suspend fun setGain(gainDb: Float?) {
        if (gainDb == null) {
            // TODO(native-backend): call rtlsdr_set_tuner_gain_mode(dev, 0) for AGC
        } else {
            // TODO(native-backend): call rtlsdr_set_tuner_gain_mode(dev, 1) for manual
            // TODO(native-backend): call rtlsdr_set_tuner_gain(dev, gainDb * 10) — gain is in tenths of dB
        }
    }

    override suspend fun setSampleRate(sampleRateHz: Int) {
        // TODO(native-backend): call rtlsdr_set_sample_rate(dev, sampleRateHz)
    }

    override fun iqSampleStream(): Flow<IqSample> = flow {
        // Attempt to stream raw IQ bytes from the RTL2832U's bulk-in endpoint.
        //
        // NOTE: For audio to work the USB device must first be initialised with the
        // RTL2832U + R820T2 register sequences (see librtlsdr rtlsdr_init_baseband /
        // r82xx_init).  The open() stub above marks those TODO.  Until that
        // initialization is implemented, bulkTransfer() will return 0 bytes because
        // the endpoint is not yet enabled by the chip.
        //
        // To use real hardware RIGHT NOW without native library:
        //   1. Install "RTL-SDR USB Driver" (marto.rtl_tcp_andro) from the Play Store.
        //   2. The backend selection will then use ExternalDriverBackend via rtl_tcp.

        val device = findRtlSdrDevice() ?: return@flow
        val conn = usbManager.openDevice(device) ?: return@flow

        val intf = (0 until device.interfaceCount).map { device.getInterface(it) }.firstOrNull()
            ?: run { conn.close(); return@flow }

        val endpoint = (0 until intf.endpointCount)
            .map { intf.getEndpoint(it) }
            .firstOrNull {
                it.direction == UsbConstants.USB_DIR_IN &&
                it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
            }
            ?: run { conn.close(); return@flow }

        if (!conn.claimInterface(intf, true)) {
            conn.close()
            return@flow
        }
        _usbConnection = conn
        _usbInterface  = intf

        val buf = ByteArray(65_536) // 64 KB ≈ 68 ms of IQ at 960 kHz
        try {
            while (currentCoroutineContext().isActive) {
                val n = conn.bulkTransfer(endpoint, buf, buf.size, 5_000)
                if (n > 0) {
                    emit(
                        IqSample(
                            data                = buf.copyOf(n),
                            sampleRateHz        = FmDemodulator.INPUT_RATE,
                            centerFrequencyMhz  = _currentFrequencyMhz,
                            timestampNs         = System.nanoTime(),
                        )
                    )
                } else if (n < 0) {
                    break // USB error or device disconnected
                }
            }
        } finally {
            conn.releaseInterface(intf)
            conn.close()
            _usbConnection = null
            _usbInterface  = null
        }
    }

    override suspend fun measureSignalQuality(): SignalQuality {
        // TODO(native-backend): implement signal measurement from IQ samples
        // Approach: collect a short burst of IQ data, compute:
        //   - power level from RMS of IQ magnitudes
        //   - rough SNR via spectral analysis
        //   - pilot tone detection at 19 kHz offset
        return SignalQuality()
    }

    // --- USB helpers ---

    private fun findRtlSdrDevice(): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull { device ->
            RTL_SDR_VID_PIDS.any { (vid, pid) -> device.vendorId == vid && device.productId == pid }
        }
    }

    companion object {
        private val RTL_SDR_VID_PIDS = listOf(
            0x0BDA to 0x2832,
            0x0BDA to 0x2838,
            0x0BDA to 0x2831,
            0x0BDA to 0x2840,
            0x0BDA to 0x2836,
        )
    }
}
