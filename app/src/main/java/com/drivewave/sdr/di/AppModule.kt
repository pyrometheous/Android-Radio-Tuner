package com.drivewave.sdr.di

import android.content.Context
import androidx.room.Room
import com.drivewave.sdr.data.repository.RecordingRepositoryImpl
import com.drivewave.sdr.data.repository.SettingsRepositoryImpl
import com.drivewave.sdr.data.repository.StationRepositoryImpl
import com.drivewave.sdr.data.storage.DriveWaveDatabase
import com.drivewave.sdr.data.storage.RecordingDao
import com.drivewave.sdr.data.storage.StationDao
import com.drivewave.sdr.domain.repository.RecordingRepository
import com.drivewave.sdr.domain.repository.SettingsRepository
import com.drivewave.sdr.domain.repository.StationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DriveWaveDatabase =
        Room.databaseBuilder(context, DriveWaveDatabase::class.java, "drivewave_radio.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideStationDao(db: DriveWaveDatabase): StationDao = db.stationDao()

    @Provides
    fun provideRecordingDao(db: DriveWaveDatabase): RecordingDao = db.recordingDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindStationRepository(impl: StationRepositoryImpl): StationRepository

    @Binds
    @Singleton
    abstract fun bindRecordingRepository(impl: RecordingRepositoryImpl): RecordingRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
