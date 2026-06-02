package com.linkvault.data.repository

import com.linkvault.data.local.dao.DownloadDao
import com.linkvault.data.local.dao.LinkDao
import com.linkvault.data.local.entity.DownloadEntity
import com.linkvault.data.local.entity.DownloadStatus
import com.linkvault.data.local.entity.LinkEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadDao: DownloadDao,
    private val linkDao: LinkDao
) {
    val allDownloads: Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()

    suspend fun addToQueue(linkId: Long, url: String, title: String, folderName: String, format: String, path: String) {
        if (!downloadDao.isAlreadyInQueue(url)) {
            val download = DownloadEntity(
                linkId = linkId,
                url = url,
                title = title,
                folderName = folderName,
                formatSelection = format,
                downloadPath = path,
                status = DownloadStatus.WAITING
            )
            downloadDao.insertDownload(download)
        }
    }

    suspend fun updateStatus(id: Long, status: DownloadStatus) {
        downloadDao.updateStatus(id, status)
    }

    suspend fun updateProgress(id: Long, progress: Float, speed: String, eta: String) {
        downloadDao.updateProgress(id, progress, speed, eta)
    }

    suspend fun updateDownload(download: DownloadEntity) {
        downloadDao.updateDownload(download)
    }

    suspend fun getNextWaitingDownload(): DownloadEntity? {
        return downloadDao.getNextWaitingDownload()
    }

    suspend fun deleteDownload(download: DownloadEntity) {
        downloadDao.deleteDownload(download)
    }

    suspend fun clearCompleted() {
        downloadDao.clearCompleted()
    }

    suspend fun getDownloadById(id: Long): DownloadEntity? {
        return downloadDao.getDownloadById(id)
    }

    suspend fun getLinkById(id: Long): LinkEntity? {
        return linkDao.getLinkById(id)
    }
}
