package com.drivewave.sdr.driver

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.drivewave.sdr.domain.model.RadioBand
import com.drivewave.sdr.domain.model.SignalQuality
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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

    override fun iqSampleStream(): Flow<IqSample> {
        // RTL2832U chip initialisation (baseband setup, I²C tuner config, sample-rate
        // divider) has not been implemented yet. Without it the device either returns
        // no data or DVB-T garbage that the FM demodulator hears as loud noise.
        // Use ExternalDriverBackend (rtl_tcp) for real-hardware reception today.
        // TODO(native-backend): implement initBaseband() + r82xx_init() then remove this guard.
        return emptyFlow()
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
