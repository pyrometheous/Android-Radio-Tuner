package com.drivewave.sdr.dsp

import kotlin.math.atan2

/**
 * FM demodulator with two-stage CIC decimation and 75 µs de-emphasis.
 *
 * Pipeline:
 *   Input  → 8-bit signed IQ bytes at [INPUT_RATE] Hz
 *   Stage1 → Decimate by [DEC1] (box/CIC filter) → [INTER_RATE] Hz
 *   FM     → Polar discriminator → instantaneous frequency deviation
 *   Deemph → 75 µs RC IIR low-pass (US/European broadcast standard)
 *   Stage2 → Decimate by [DEC2] (box filter) → [OUTPUT_RATE] Hz
 *   Output → 16-bit signed PCM mono at [OUTPUT_RATE] Hz
 *
 * Math: 960,000 ÷ 4 ÷ 5 = 48,000 Hz
 *
 * Input bytes should be signed (-128..127) interleaved I,Q as per IqSample.data.
 */
class FmDemodulator {

    // Stage-1 CIC decimation state
    private val iAcc  = FloatArray(DEC1)
    private val qAcc  = FloatArray(DEC1)
    private var acc1  = 0

    // FM polar discriminator state
    private var prevI = 0f
    private var prevQ = 0f

    // 75 µs de-emphasis IIR state
    private var deemphY = 0f

    // Stage-2 CIC decimation state
    private val audioAcc = FloatArray(DEC2)
    private var acc2     = 0

    // Scratch output list — reused each call
    private val outBuf = ArrayList<Short>(4096)

    /**
     * Process one block of interleaved signed IQ bytes.
     * Returns a ShortArray of 16-bit PCM mono samples at [OUTPUT_RATE] Hz.
     * Safe to call from any thread; not thread-safe across concurrent calls.
     */
    fun process(data: ByteArray): ShortArray {
        outBuf.clear()
        var i = 0
        val end = data.size and 1.inv()   // ensure we consume whole IQ pairs

        while (i < end) {
            val iSamp = data[i++].toFloat() * INV128
            val qSamp = data[i++].toFloat() * INV128

            // Stage 1: accumulate DEC1 samples and average
            iAcc[acc1] = iSamp
            qAcc[acc1] = qSamp
            if (++acc1 == DEC1) {
                acc1 = 0
                var iSum = 0f; var qSum = 0f
                for (k in 0 until DEC1) { iSum += iAcc[k]; qSum += qAcc[k] }
                val iD = iSum * INV_DEC1
                val qD = qSum * INV_DEC1

                // Polar FM discriminator:
                //   demod = atan2(I[n-1]·Q[n] − Q[n-1]·I[n],
                //                 I[n]·I[n-1] + Q[n]·Q[n-1])
                // Output proportional to instantaneous frequency deviation.
                val dot   = iD * prevI + qD * prevQ
                val cross = prevI * qD - prevQ * iD
                val demod = if (dot == 0f && cross == 0f) 0f
                            else atan2(cross, dot) * INV_PI
                prevI = iD
                prevQ = qD

                // 75 µs de-emphasis: 1st-order IIR low-pass at INTER_RATE
                deemphY += DEEMPH_ALPHA * (demod - deemphY)

                // Stage 2: accumulate DEC2 samples and average
                audioAcc[acc2] = deemphY
                if (++acc2 == DEC2) {
                    acc2 = 0
                    var aSum = 0f
                    for (k in 0 until DEC2) aSum += audioAcc[k]
                    val pcm = (aSum * INV_DEC2 * AUDIO_GAIN)
                        .toInt()
                        .coerceIn(-32768, 32767)
                    outBuf.add(pcm.toShort())
                }
            }
        }

        return ShortArray(outBuf.size) { outBuf[it] }
    }

    /** Reset all internal state — call when tuning to a new frequency. */
    fun reset() {
        iAcc.fill(0f); qAcc.fill(0f); acc1 = 0
        prevI = 0f; prevQ = 0f; deemphY = 0f
        audioAcc.fill(0f); acc2 = 0
        outBuf.clear()
    }

    companion object {
        /** IQ input sample rate — must match [SdrBackend.setSampleRate] and IqSample.sampleRateHz. */
        const val INPUT_RATE  = 960_000
        /** PCM audio output sample rate. */
        const val OUTPUT_RATE =  48_000

        private const val DEC1 = 4      // 960 kHz → 240 kHz
        private const val DEC2 = 5      // 240 kHz →  48 kHz
        private const val INTER_RATE = INPUT_RATE / DEC1   // 240,000 Hz

        /** 75 µs de-emphasis time constant at the intermediate sample rate. */
        private val DEEMPH_ALPHA: Float = run {
            val dt = 1.0 / INTER_RATE
            (dt / (75e-6 + dt)).toFloat()
        }

        /**
         * Gain applied after demodulation to bring FM deviation into 16-bit PCM range.
         * FM max deviation = ±75 kHz; at 240 kHz intermediate rate, full-scale atan2 ≈ 0.625.
         * 0.625 × AUDIO_GAIN ≈ 18,750 — slightly below Short.MAX_VALUE for headroom.
         */
        private const val AUDIO_GAIN  = 30_000f

        private const val INV128   = 1f / 128f
        private const val INV_PI   = (1.0 / Math.PI).toFloat()
        private const val INV_DEC1 = 1f / DEC1
        private const val INV_DEC2 = 1f / DEC2
    }
}
