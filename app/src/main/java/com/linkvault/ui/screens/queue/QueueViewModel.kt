package com.linkvault.ui.screens.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkvault.data.local.entity.DownloadEntity
import com.linkvault.data.repository.DownloadManagerRepository
import com.linkvault.data.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val repository: DownloadRepository,
    private val manager: DownloadManagerRepository
) : ViewModel() {

    val downloads: StateFlow<List<DownloadEntity>> = repository.allDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun pauseQueue() {
        manager.pauseQueue()
    }

    fun resumeQueue() {
        manager.resumeQueue()
    }

    fun removeDownload(download: DownloadEntity) {
        manager.removeDownload(download)
    }

    fun retryDownload(download: DownloadEntity) {
        manager.retryDownload(download)
    }

    fun clearCompleted() {
        viewModelScope.launch {
            repository.clearCompleted()
        }
    }
}
