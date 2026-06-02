package com.linkvault.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus {
    WAITING, PREPARING, DOWNLOADING, COMPLETED, FAILED, CANCELED
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val linkId: Long,
    val url: String,
    val title: String = "",
    val folderName: String = "",
    val status: DownloadStatus = DownloadStatus.WAITING,
    val progress: Float = 0f,
    val speed: String = "",
    val eta: String = "",
    val formatSelection: String = "best", // e.g., "bestvideo+bestaudio/best", "bestaudio"
    val downloadPath: String = "",
    val error: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)
