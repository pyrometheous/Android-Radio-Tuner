package com.drivewave.sdr.recording

import android.content.ContentValues
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.provider.MediaStore
import com.drivewave.sdr.domain.model.RadioBand
import com.drivewave.sdr.domain.model.RdsMetadata
import com.drivewave.sdr.domain.model.Recording
import com.drivewave.sdr.domain.repository.RecordingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages audio recording of SDR output.
 *
 * Records demodulated FM audio to AAC/M4A format via MediaStore (no WRITE_EXTERNAL_STORAGE needed).
 * Embeds available station metadata into the saved file.
 *
 * TODO(recording): Replace AudioRecord with actual demodulated audio from the SDR engine.
 * Currently records from the device microphone as a placeholder.
 * When the FM demodulator is implemented, pipe its PCM output here instead.
 *
 * File naming: StationName_101.5FM_2026-03-22_14-35.m4a
 */
@Singleton
class RecordingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordingRepository: RecordingRepository,
) {
    private var activeRecording: ActiveRecording? = null
    val isRecording: Boolean get() = activeRecording?.isActive == true

    /**
     * Start recording demodulated audio.
     * @param stationName Station display name for the filename/metadata
     * @param frequencyMhz Current tuned frequency
     * @param band Current band
     * @param rdsMetadata Current RDS metadata for embedding
     * @return the Recording object if started successfully, null on error
     */
    suspend fun startRecording(
        stationName: String,
        frequencyMhz: Float,
        band: RadioBand,
        rdsMetadata: RdsMetadata,
    ): Result<Recording> = withContext(Dispatchers.IO) {
        try {
            if (isRecording) return@withContext Result.failure(IllegalStateException("Already recording"))

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
            val bandStr = if (band == RadioBand.FM) "FM" else "AM"
            val freqStr = if (band == RadioBand.FM) "%.1f".format(frequencyMhz) else "%.0f".format(frequencyMhz * 1000)
            val safeName = stationName.replace(Regex("[^A-Za-z0-9 ._-]"), "_").take(40)
            val fileName = "${safeName}_${freqStr}${bandStr}_${timestamp}.m4a"

            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.ALBUM, "DriveWave SDR")
                put(MediaStore.Audio.Media.ARTIST, stationName)
                put(MediaStore.Audio.Media.TITLE, rdsMetadata.radioText ?: stationName)
                put(MediaStore.Audio.Media.YEAR, SimpleDateFormat("yyyy", Locale.US).format(Date()).toInt())
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/DriveWave SDR")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }

            val uri = context.contentResolver.insert(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                contentValues,
            ) ?: return@withContext Result.failure(Exception("MediaStore insert failed"))

            val outputStream = context.contentResolver.openOutputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open output stream"))

            val recordingId = UUID.randomUUID().toString()
            val recording = Recording(
                id = recordingId,
                filePath = uri.toString(),
                fileName = fileName,
                stationName = stationName,
                frequencyMhz = frequencyMhz,
                band = band,
                startedAtEpochMs = System.currentTimeMillis(),
                durationMs = 0L,
                fileSizeBytes = 0L,
                radioText = rdsMetadata.radioText,
            )

            val active = ActiveRecording(
                id = recordingId,
                uri = uri,
                outputStream = outputStream,
                startedAtMs = System.currentTimeMillis(),
                contentValues = contentValues,
                recording = recording,
            )

            activeRecording = active
            // TODO(recording): Start reading PCM from SDR FM demodulator output.
            // For now, begin a real AudioRecord from mic as structural placeholder.
            active.startAudioCapture()

            Result.success(recording)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Stop the active recording, finalize the file, and save to the repository.
     * @return the finalized [Recording] or null if nothing was recording.
     */
    suspend fun stopRecording(): Recording? = withContext(Dispatchers.IO) {
        val active = activeRecording ?: return@withContext null
        activeRecording = null
        try {
            active.stop()
            val durationMs = System.currentTimeMillis() - active.startedAtMs
            val fileSizeBytes = try {
                context.contentResolver.openFileDescriptor(active.uri, "r")?.use {
                    it.statSize
                } ?: 0L
            } catch (_: Exception) { 0L }

            // Mark file as no longer pending — makes it visible in gallery/files
            val finalValues = ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
            context.contentResolver.update(active.uri, finalValues, null, null)

            val finalized = active.recording.copy(
                durationMs = durationMs,
                fileSizeBytes = fileSizeBytes,
            )
            recordingRepository.saveRecording(finalized)
            finalized
        } catch (e: Exception) {
            null
        }
    }

    fun cancelRecording() {
        activeRecording?.cancel()
        activeRecording = null
    }
}

private class ActiveRecording(
    val id: String,
    val uri: android.net.Uri,
    val outputStream: OutputStream,
    val startedAtMs: Long,
    val contentValues: ContentValues,
    val recording: Recording,
) {
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    var isActive: Boolean = false
        private set

    fun startAudioCapture() {
        // TODO(recording): Replace this AudioRecord (microphone) with demodulated FM PCM stream.
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_STEREO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            minBuffer * 4,
        )

        isActive = true
        audioRecord?.startRecording()

        captureThread = Thread {
            val buffer = ByteArray(minBuffer * 2)
            while (isActive) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (read > 0) {
                    try {
                        outputStream.write(buffer, 0, read)
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        }.also { it.start() }
    }

    fun stop() {
        isActive = false
        captureThread?.join(2000)
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        try { outputStream.flush() } catch (_: Exception) {}
        try { outputStream.close() } catch (_: Exception) {}
    }

    fun cancel() {
        stop()
    }
}
