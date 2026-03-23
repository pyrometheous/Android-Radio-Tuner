package com.drivewave.sdr.driver

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Selects the best available [SdrBackend] at runtime.
 *
 * Priority order:
 *   1. [RtlSdrNativeBackend] — embedded native driver (preferred, requires native lib integration)
 *   2. [ExternalDriverBackend] — route through an installed driver app
 *   3. [FakeSdrBackend] — always available, used for UI development / no hardware
 */
@Singleton
class SdrBackendSelector @Inject constructor(
    private val nativeBackend: RtlSdrNativeBackend,
    private val externalBackend: ExternalDriverBackend,
    private val fakeBackend: FakeSdrBackend,
) {
    /** Returns the best available backend right now. */
    fun selectBackend(): SdrBackend = when {
        nativeBackend.isAvailable -> nativeBackend
        externalBackend.isAvailable -> externalBackend
        else -> fakeBackend
    }

    /** All backends in priority order, for settings display. */
    fun allBackends(): List<SdrBackend> = listOf(nativeBackend, externalBackend, fakeBackend)
}
