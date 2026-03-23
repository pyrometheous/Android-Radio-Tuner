package com.drivewave.sdr.util

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple file-based debug logger.
 *
 * Writes timestamped log entries to:
 *   internal storage: files/logs/debug.log
 *
 * Call [exportToDownloads] to copy the log file to the device Downloads folder
 * as "DriveWave-debug.log" where it can be pulled via USB or a file manager.
 *
 * Usage:
 *   appLogger.d("ScanEngine", "Starting scan at 87.9 MHz")
 *   appLogger.e("RadioService", "Audio track failed", exception)
 */
@Singleton
class AppLogger @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val logFile: File by lazy {
        val dir = File(context.filesDir, "logs")
        dir.mkdirs()
        File(dir, "debug.log").also { f ->
            // Trim to last 512 KB on every cold start to prevent unbounded growth
            if (f.length() > 512 * 1024) f.delete()
        }
    }

    /** Log a debug message. */
    fun d(tag: String, message: String) = write("D", tag, message)

    /** Log a warning message. */
    fun w(tag: String, message: String, throwable: Throwable? = null) =
        write("W", tag, if (throwable != null) "$message — ${throwable.format()}" else message)

    /** Log an error message. */
    fun e(tag: String, message: String, throwable: Throwable? = null) =
        write("E", tag, if (throwable != null) "$message — ${throwable.format()}" else message)

    private fun Throwable.format(): String =
        "${javaClass.simpleName}: ${message ?: "(no message)"}\n" +
        stackTrace.take(8).joinToString("\n") { "    at $it" }

    private fun write(level: String, tag: String, message: String) {
        val timestamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "$timestamp $level/$tag: $message\n"
        Log.println(
            when (level) { "E" -> Log.ERROR; "W" -> Log.WARN; else -> Log.DEBUG },
            tag, message,
        )
        try { synchronized(this) { logFile.appendText(line) } } catch (_: Exception) {}
    }

    /**
     * Copy the accumulated log file to the Downloads folder as "DriveWave-debug.log".
     * Safe to call from any coroutine; uses MediaStore (no storage permission needed on API 29+).
     * @return true if successful.
     */
    fun exportToDownloads(): Boolean {
        return try {
            val logText = synchronized(this) { logFile.readText() }
            val fileName = "DriveWave-debug.log"

            // Delete any pre-existing file with the same name first (best-effort)
            val existing = context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf(fileName),
                null,
            )
            existing?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(0)
                    context.contentResolver.delete(
                        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY).buildUpon()
                            .appendPath(id.toString()).build(),
                        null, null,
                    )
                }
            }

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values,
            ) ?: return false

            context.contentResolver.openOutputStream(uri)?.use { it.write(logText.toByteArray()) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            e("AppLogger", "exportToDownloads failed", e)
            false
        }
    }

    /** Delete the internal log file. */
    fun clear() {
        try { synchronized(this) { logFile.delete() } } catch (_: Exception) {}
    }
}
