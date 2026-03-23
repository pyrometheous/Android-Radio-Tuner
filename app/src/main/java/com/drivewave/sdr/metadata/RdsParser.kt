package com.drivewave.sdr.metadata

import com.drivewave.sdr.domain.model.RdsMetadata
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses RDS (Radio Data System) / RBDS data from FM multiplex.
 *
 * RDS data structure:
 *   - Each RDS group = 4 x 16-bit blocks (A, B, C, D) + 10-bit offset words
 *   - Block A: PI code (16-bit programme identification)
 *   - Block B: group type (4 bits), version (1 bit), TP, PTY, flags
 *   - Blocks C/D: payload varies by group type
 *
 * Group types used here:
 *   Group 0A/0B: Program Service name (PS) and basic tuning info
 *   Group 2A/2B: RadioText (RT)
 *   Group 10A:   Programme Type Name (PTYN) — optional
 *   Group 14A/14B: Enhanced Other Networks (EON) — Alternative Frequencies
 *
 * TODO(rds-parser): Replace [parseRawGroup] stub with proper IQ → MPX → RDS demodulation chain:
 *   1. FM demodulation from IQ samples → composite MPX audio
 *   2. 57 kHz subcarrier extraction → biphase mark coding → RDS clock recovery
 *   3. Group assembly with error correction (10-bit offset word checking)
 *   4. Group dispatch to handlers below
 *
 * References:
 *   - IEC 62106 (RDS standard)
 *   - NRSC-4-B (RBDS US standard)
 */
@Singleton
class RdsParser @Inject constructor() {

    private val psBuffer = CharArray(8) { ' ' }
    private val rtBuffer = CharArray(64) { ' ' }
    private var psSegmentsReceived = 0
    private var rtSegmentsReceived = 0
    private var piCode: Int = 0
    private var pty: Int = 0
    private var hasRtA: Boolean? = null // null=unknown, true=64-char, false=32-char
    private val alternativeFrequencies = mutableSetOf<Float>()

    fun reset() {
        psBuffer.fill(' ')
        rtBuffer.fill(' ')
        psSegmentsReceived = 0
        rtSegmentsReceived = 0
        piCode = 0
        pty = 0
        hasRtA = null
        alternativeFrequencies.clear()
    }

    /**
     * Feed a single RDS group (4 blocks of 16 bits each + BLER flags).
     *
     * TODO(rds-parser): Call this from the FM demodulator once RDS sync is achieved.
     * @param blockA PI code
     * @param blockB group/version/flags word
     * @param blockC payload C
     * @param blockD payload D
     * @param blerC block error rate flag for C (0=ok)
     * @param blerD block error rate flag for D (0=ok)
     */
    fun parseRawGroup(
        blockA: Int, blockB: Int, blockC: Int, blockD: Int,
        blerC: Int = 0, blerD: Int = 0,
    ) {
        piCode = blockA
        val groupType = (blockB shr 12) and 0x0F
        val version = (blockB shr 11) and 0x01
        pty = (blockB shr 5) and 0x1F

        when {
            groupType == 0 -> parseGroup0(blockB, blockC, blockD, blerC, blerD)
            groupType == 2 -> parseGroup2(blockB, blockC, blockD, version, blerC, blerD)
            groupType == 0x0A && version == 0 -> parseGroup10A(blockD) // PTY Name
            groupType == 14 -> parseGroup14(blockB, blockC, blockD, version)
        }
    }

    /** Group 0: PS name + AF list */
    private fun parseGroup0(blockB: Int, blockC: Int, blockD: Int, blerC: Int, blerD: Int) {
        val segAddr = blockB and 0x03
        // AF codes in block C (Group 0A only)
        if ((blockB shr 11 and 1) == 0 && blerC == 0) {
            decodeAfPair((blockC shr 8) and 0xFF, blockC and 0xFF)
        }
        if (blerD == 0) {
            val offset = segAddr * 2
            if (offset + 1 < psBuffer.size) {
                psBuffer[offset] = ((blockD shr 8) and 0x7F).toChar()
                psBuffer[offset + 1] = (blockD and 0x7F).toChar()
            }
        }
        psSegmentsReceived = psSegmentsReceived or (1 shl segAddr)
    }

    /** Group 2: RadioText */
    private fun parseGroup2(blockB: Int, blockC: Int, blockD: Int, version: Int, blerC: Int, blerD: Int) {
        val textABFlag = (blockB shr 4) and 0x01
        val segAddr = blockB and 0x0F
        if (version == 0) { // 2A: 4 chars per group
            if (blerC == 0) {
                val offset = segAddr * 4
                if (offset + 3 < rtBuffer.size) {
                    rtBuffer[offset] = ((blockC shr 8) and 0x7F).toChar()
                    rtBuffer[offset + 1] = (blockC and 0x7F).toChar()
                    rtBuffer[offset + 2] = ((blockD shr 8) and 0x7F).toChar()
                    rtBuffer[offset + 3] = (blockD and 0x7F).toChar()
                }
            }
            hasRtA = true
        } else { // 2B: 2 chars per group
            if (blerD == 0) {
                val offset = segAddr * 2
                if (offset + 1 < rtBuffer.size) {
                    rtBuffer[offset] = ((blockD shr 8) and 0x7F).toChar()
                    rtBuffer[offset + 1] = (blockD and 0x7F).toChar()
                }
            }
            hasRtA = false
        }
        rtSegmentsReceived = rtSegmentsReceived or (1 shl segAddr)
    }

    /** Group 10A: Programme Type Name */
    private fun parseGroup10A(blockD: Int) {
        // TODO(rds-parser): parse PTYN if needed for display
    }

    /** Group 14: Enhanced Other Networks (for AF list on EON) */
    private fun parseGroup14(blockB: Int, blockC: Int, blockD: Int, version: Int) {
        if (version == 0) {
            val variantCode = blockB and 0x0F
            if (variantCode in 0..3) {
                decodeAfPair((blockC shr 8) and 0xFF, blockC and 0xFF)
            }
        }
    }

    private fun decodeAfPair(af1: Int, af2: Int) {
        // AF method: Method B. Values 1–204 = 87.6 + (value * 0.1) MHz
        if (af1 in 1..204) alternativeFrequencies.add(87.5f + af1 * 0.1f)
        if (af2 in 1..204) alternativeFrequencies.add(87.5f + af2 * 0.1f)
    }

    /** Build the current [RdsMetadata] snapshot from accumulated data. */
    fun buildMetadata(): RdsMetadata {
        val ps = String(psBuffer).trim().takeIf { it.isNotBlank() }
        val rtRaw = String(rtBuffer).trimEnd()
        // Find end-of-text marker (0x0D carriage return)
        val rtEnd = rtRaw.indexOf('\r').let { if (it >= 0) it else rtRaw.length }
        val rt = rtRaw.substring(0, rtEnd).trim().takeIf { it.isNotBlank() }

        return RdsMetadata(
            programService = ps,
            radioText = rt,
            programType = pty.takeIf { it > 0 },
            programTypeLabel = ptyLabel(pty),
            piCode = if (piCode != 0) "%04X".format(piCode) else null,
            alternativeFrequencies = alternativeFrequencies.sorted(),
            lastRefreshedEpochMs = System.currentTimeMillis(),
        )
    }

    /** Returns true when the PS name has been fully received (all 4 segments). */
    val isPsComplete: Boolean get() = psSegmentsReceived == 0x0F

    /** Returns 0.0–1.0 quality estimate of RDS lock. */
    val lockQuality: Float
        get() = Integer.bitCount(psSegmentsReceived) / 4f

    companion object {
        /** PTY labels per IEC 62106 Table 15 (RBDS program types). */
        fun ptyLabel(pty: Int): String? = when (pty) {
            0 -> null
            1 -> "News"
            2 -> "Information"
            3 -> "Sports"
            4 -> "Talk"
            5 -> "Rock"
            6 -> "Classic Rock"
            7 -> "Adult Hits"
            8 -> "Soft Rock"
            9 -> "Top 40"
            10 -> "Country"
            11 -> "Oldies"
            12 -> "Soft"
            13 -> "Nostalgia"
            14 -> "Jazz"
            15 -> "Classical"
            16 -> "Rhythm & Blues"
            17 -> "Soft R&B"
            18 -> "Foreign Language"
            19 -> "Religious Music"
            20 -> "Religious Talk"
            21 -> "Personality"
            22 -> "Public"
            23 -> "College"
            24 -> "Spanish Talk"
            25 -> "Spanish Music"
            26 -> "Hip Hop"
            31 -> "Weather"
            else -> "Unknown ($pty)"
        }
    }
}
