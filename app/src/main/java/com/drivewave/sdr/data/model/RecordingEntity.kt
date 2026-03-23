package com.drivewave.sdr.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.drivewave.sdr.domain.model.RadioBand
import com.drivewave.sdr.domain.model.Recording

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey val id: String,
    val filePath: String,
    val fileName: String,
    val stationName: String,
    val frequencyMhz: Float,
    val band: String,
    val startedAtEpochMs: Long,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val radioText: String?,
)

fun RecordingEntity.toDomain() = Recording(
    id = id,
    filePath = filePath,
    fileName = fileName,
    stationName = stationName,
    frequencyMhz = frequencyMhz,
    band = RadioBand.valueOf(band),
    startedAtEpochMs = startedAtEpochMs,
    durationMs = durationMs,
    fileSizeBytes = fileSizeBytes,
    radioText = radioText,
)

fun Recording.toEntity() = RecordingEntity(
    id = id,
    filePath = filePath,
    fileName = fileName,
    stationName = stationName,
    frequencyMhz = frequencyMhz,
    band = band.name,
    startedAtEpochMs = startedAtEpochMs,
    durationMs = durationMs,
    fileSizeBytes = fileSizeBytes,
    radioText = radioText,
)
