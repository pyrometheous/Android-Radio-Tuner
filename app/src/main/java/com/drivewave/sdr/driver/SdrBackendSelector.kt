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
 *   2. [RtlSdrNativeBackend] — direct USB backend (chip init is TODO).
 *
 * Returns **null** when no real hardware backend is available.
 * The app must show a "No tuner detected" state and not play audio in that case.
 * [FakeSdrBackend] is intentionally NOT a fallback — this app requires hardware.
 */
@Singleton
class SdrBackendSelector @Inject constructor(
    private val nativeBackend: RtlSdrNativeBackend,
    private val externalBackend: ExternalDriverBackend,
    private val fakeBackend: FakeSdrBackend, // kept for DI; not returned by selectBackend()
) {
    /** Returns the best available real-hardware backend, or null if none is present. */
    fun selectBackend(): SdrBackend? = when {
        externalBackend.isAvailable -> externalBackend
        nativeBackend.isAvailable   -> nativeBackend
        else                        -> null
    }

    /** All backends in priority order, for settings display. */
    fun allBackends(): List<SdrBackend> = listOf(externalBackend, nativeBackend, fakeBackend)
}
