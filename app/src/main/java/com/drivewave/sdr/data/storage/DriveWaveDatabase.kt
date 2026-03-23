package com.drivewave.sdr.data.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import com.drivewave.sdr.data.model.RecordingEntity
import com.drivewave.sdr.data.model.StationEntity

@Database(
    entities = [StationEntity::class, RecordingEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class DriveWaveDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
    abstract fun recordingDao(): RecordingDao
}
