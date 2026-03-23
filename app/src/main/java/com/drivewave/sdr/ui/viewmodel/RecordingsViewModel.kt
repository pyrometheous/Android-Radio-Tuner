package com.drivewave.sdr.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivewave.sdr.domain.model.Recording
import com.drivewave.sdr.domain.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingsViewModel @Inject constructor(
    private val recordingRepository: RecordingRepository,
) : ViewModel() {

    val recordings = recordingRepository.observeRecordings().stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList()
    )

    fun deleteRecording(recording: Recording) {
        viewModelScope.launch {
            recordingRepository.deleteRecording(recording.id)
        }
    }
}
