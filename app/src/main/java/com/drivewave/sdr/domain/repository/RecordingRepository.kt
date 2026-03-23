package com.drivewave.sdr.domain.repository

import com.drivewave.sdr.domain.model.Recording
import kotlinx.coroutines.flow.Flow

interface RecordingRepository {
    fun observeRecordings(): Flow<List<Recording>>
    suspend fun getRecording(id: String): Recording?
    suspend fun saveRecording(recording: Recording)
    suspend fun deleteRecording(id: String)
}
