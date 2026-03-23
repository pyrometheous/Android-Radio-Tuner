package com.drivewave.sdr.driver

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Selects the best available [SdrBackend] at runtime.
 *
 * Priority order:
 *   1. [ExternalDriverBackend] — rtl_tcp server from the RTL-SDR driver app.
 *      This is the recommended real-hardware path: the driver app handles USB
 *      permissions and RTL2832U chip initialisation; we just read the IQ stream.
 *   2. [RtlSdrNativeBackend] — direct USB backend (chip init is TODO; selected
 *      as last resort when no driver app is present but a dongle is detected).
 *   3. [FakeSdrBackend] — always available; FM-modulated test tone for UI testing.
 */
@Singleton
class SdrBackendSelector @Inject constructor(
    private val nativeBackend: RtlSdrNativeBackend,
    private val externalBackend: ExternalDriverBackend,
    private val fakeBackend: FakeSdrBackend,
) {
    /** Returns the best available backend right now. */
    fun selectBackend(): SdrBackend = when {
        externalBackend.isAvailable -> externalBackend
        nativeBackend.isAvailable   -> nativeBackend
        else                        -> fakeBackend
    }

    /** All backends in priority order, for settings display. */
    fun allBackends(): List<SdrBackend> = listOf(externalBackend, nativeBackend, fakeBackend)
}
