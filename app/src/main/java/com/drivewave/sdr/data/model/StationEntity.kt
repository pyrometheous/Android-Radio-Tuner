package com.drivewave.sdr.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.drivewave.sdr.domain.model.RadioBand
import com.drivewave.sdr.domain.model.RdsMetadata
import com.drivewave.sdr.domain.model.Station

@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey val id: String,
    val frequencyMhz: Float,
    val band: String,
    val displayName: String,
    val userLabel: String?,
    // RDS fields flattened
    @ColumnInfo(name = "rds_ps") val rdsPs: String?,
    @ColumnInfo(name = "rds_rt") val rdsRt: String?,
    @ColumnInfo(name = "rds_pty") val rdsPty: Int?,
    @ColumnInfo(name = "rds_pty_label") val rdsPtyLabel: String?,
    @ColumnInfo(name = "rds_pi") val rdsPi: String?,
    @ColumnInfo(name = "rds_afs") val rdsAfs: String?, // comma-separated floats
    @ColumnInfo(name = "rds_refreshed_ms") val rdsRefreshedMs: Long,
    val signalConfidence: Float,
    val isFavorite: Boolean,
    val favoriteOrderIndex: Int,
    val discoveredAtEpochMs: Long,
    val scanSessionId: String,
)

fun StationEntity.toDomain(): Station = Station(
    id = id,
    frequencyMhz = frequencyMhz,
    band = RadioBand.valueOf(band),
    displayName = displayName,
    userLabel = userLabel,
    rdsMetadata = RdsMetadata(
        programService = rdsPs,
        radioText = rdsRt,
        programType = rdsPty,
        programTypeLabel = rdsPtyLabel,
        piCode = rdsPi,
        alternativeFrequencies = rdsAfs?.split(",")?.mapNotNull { it.trim().toFloatOrNull() } ?: emptyList(),
        lastRefreshedEpochMs = rdsRefreshedMs,
    ),
    signalConfidence = signalConfidence,
    isFavorite = isFavorite,
    favoriteOrderIndex = favoriteOrderIndex,
    discoveredAtEpochMs = discoveredAtEpochMs,
    scanSessionId = scanSessionId,
)

fun Station.toEntity(): StationEntity = StationEntity(
    id = id,
    frequencyMhz = frequencyMhz,
    band = band.name,
    displayName = displayName,
    userLabel = userLabel,
    rdsPs = rdsMetadata.programService,
    rdsRt = rdsMetadata.radioText,
    rdsPty = rdsMetadata.programType,
    rdsPtyLabel = rdsMetadata.programTypeLabel,
    rdsPi = rdsMetadata.piCode,
    rdsAfs = rdsMetadata.alternativeFrequencies.joinToString(",").takeIf { it.isNotEmpty() },
    rdsRefreshedMs = rdsMetadata.lastRefreshedEpochMs,
    signalConfidence = signalConfidence,
    isFavorite = isFavorite,
    favoriteOrderIndex = favoriteOrderIndex,
    discoveredAtEpochMs = discoveredAtEpochMs,
    scanSessionId = scanSessionId,
)
