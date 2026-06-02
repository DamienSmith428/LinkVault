package com.linkvault.data.repository

import android.content.Context
import androidx.work.*
import com.linkvault.data.local.entity.DownloadEntity
import com.linkvault.data.local.entity.DownloadStatus
import com.linkvault.download.DownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManagerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DownloadRepository
) {
    // Initialize WorkManager lazily to avoid triggering it before Application.onCreate is ready
    private val workManager by lazy { WorkManager.getInstance(context) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Observe queue to start next download if idle
        scope.launch {
            repository.allDownloads
                .distinctUntilChanged()
                .collect { downloads ->
                    val hasActive = downloads.any { 
                        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PREPARING 
                    }
                    if (!hasActive) {
                        startNextDownload()
                    }
                }
        }
    }

    private suspend fun startNextDownload() {
        val next = repository.getNextWaitingDownload()
        if (next != null) {
            enqueueDownload(next)
        }
    }

    private fun enqueueDownload(download: DownloadEntity) {
        val data = Data.Builder()
            .putLong("download_id", download.id)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .addTag("download")
            .addTag("download_${download.id}")
            .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        // Use REPLACE to ensure we can recover from a stuck worker
        workManager.enqueueUniqueWork(
            "download_work_${download.id}",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun pauseQueue() {
        workManager.cancelAllWorkByTag("download")
    }

    fun resumeQueue() {
        scope.launch {
            startNextDownload()
        }
    }

    fun retryDownload(download: DownloadEntity) {
        scope.launch {
            repository.updateDownload(download.copy(status = DownloadStatus.WAITING, error = null, progress = 0f))
            startNextDownload()
        }
    }

    fun removeDownload(download: DownloadEntity) {
        scope.launch {
            workManager.cancelAllWorkByTag("download_${download.id}")
            repository.deleteDownload(download)
        }
    }
}
