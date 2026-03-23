package com.drivewave.sdr.data.repository

import com.drivewave.sdr.data.model.toDomain
import com.drivewave.sdr.data.model.toEntity
import com.drivewave.sdr.data.storage.RecordingDao
import com.drivewave.sdr.domain.model.Recording
import com.drivewave.sdr.domain.repository.RecordingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingRepositoryImpl @Inject constructor(
    private val recordingDao: RecordingDao,
) : RecordingRepository {

    override fun observeRecordings(): Flow<List<Recording>> =
        recordingDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getRecording(id: String): Recording? =
        recordingDao.getById(id)?.toDomain()

    override suspend fun saveRecording(recording: Recording) =
        recordingDao.insert(recording.toEntity())

    override suspend fun deleteRecording(id: String) =
        recordingDao.deleteById(id)
}
