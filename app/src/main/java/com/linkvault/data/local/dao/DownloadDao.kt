package com.linkvault.data.local.dao

import androidx.room.*
import com.linkvault.data.local.entity.DownloadEntity
import com.linkvault.data.local.entity.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY addedAt ASC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY addedAt ASC")
    suspend fun getDownloadsByStatus(status: DownloadStatus): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: Long): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity): Long

    @Update
    suspend fun updateDownload(download: DownloadEntity)

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DownloadStatus)

    @Query("UPDATE downloads SET progress = :progress, speed = :speed, eta = :eta WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Float, speed: String, eta: String)

    @Delete
    suspend fun deleteDownload(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()

    @Query("SELECT * FROM downloads WHERE status = 'WAITING' ORDER BY addedAt ASC LIMIT 1")
    suspend fun getNextWaitingDownload(): DownloadEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE url = :url AND status != 'COMPLETED')")
    suspend fun isAlreadyInQueue(url: String): Boolean
}
