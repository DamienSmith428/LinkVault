package com.linkvault.download

import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.linkvault.data.local.entity.DownloadStatus
import com.linkvault.data.preferences.UserPreferences
import com.linkvault.data.repository.DownloadRepository
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: DownloadRepository,
    private val userPreferences: UserPreferences
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "download_channel"
        private const val TAG = "DownloadWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getLong("download_id", -1L)
        if (downloadId == -1L) return@withContext Result.failure()

        val download = repository.getDownloadById(downloadId) ?: return@withContext Result.failure()

        try {
            repository.updateStatus(downloadId, DownloadStatus.PREPARING)
            setForeground(createForegroundInfo("Preparing download...", 0))

            val globalDownloadLocation = userPreferences.downloadLocation.first()
            
            val tempBaseDir = context.getExternalFilesDir("temp_downloads") ?: context.cacheDir
            val downloadDir = File(tempBaseDir, "download_${downloadId}_${System.currentTimeMillis()}")
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val request = YoutubeDLRequest(download.url)
            request.addOption("-o", "${downloadDir.absolutePath}/%(title)s.%(ext)s")

            request.addOption("--write-thumbnail")
            request.addOption("--convert-thumbnails", "jpg")
            request.addOption("--embed-thumbnail")
            request.addOption("--add-metadata")
            request.addOption("--embed-chapters")
            
            val link = repository.getLinkById(download.linkId)
            if (link != null) {
                if (link.artist.isNotBlank() || link.title.isNotBlank()) {
                    val metadataTemplate = buildString {
                        if (link.artist.isNotBlank()) append("%(artist)s")
                        if (link.artist.isNotBlank() && link.title.isNotBlank()) append(" - ")
                        if (link.title.isNotBlank()) append("%(title)s")
                    }
                    if (metadataTemplate.isNotBlank()) {
                        request.addOption("--metadata-from-title", metadataTemplate)
                    }
                }
                
                if (link.artist.isNotBlank()) {
                    request.addOption("--postprocessor-args", "ffmpeg:-metadata artist=\"${link.artist}\"")
                }
                if (link.album.isNotBlank()) {
                    request.addOption("--postprocessor-args", "ffmpeg:-metadata album=\"${link.album}\"")
                }
                if (link.releaseYear.isNotBlank()) {
                    request.addOption("--postprocessor-args", "ffmpeg:-metadata date=\"${link.releaseYear}\"")
                }
            } else {
                request.addOption("--metadata-from-title", "%(artist)s - %(title)s")
            }
            
            request.addOption("--no-mtime")
            request.addOption("--no-check-certificate")
            request.addOption("--no-part") 
            request.addOption("--no-playlist")
            request.addOption("--socket-timeout", "30")
            request.addOption("--prefer-free-formats")
            request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            
            if (download.formatSelection == "audio") {
                request.addOption("-f", "bestaudio")
                request.addOption("--extract-audio")
                request.addOption("--audio-format", "mp3")
            } else if (download.formatSelection != "best") {
                request.addOption("-f", download.formatSelection)
            }

            repository.updateStatus(downloadId, DownloadStatus.DOWNLOADING)

            var lastProgress = -1
            YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                Log.v(TAG, "ytdlp output: $line")

                val currentProgress = progress.toInt()
                if (currentProgress > lastProgress) {
                    lastProgress = currentProgress
                    
                    val speedMatch = Regex("at\\s+([\\d.]+[kMG]B/s)").find(line)
                    val speed = speedMatch?.groupValues?.get(1) ?: ""
                    val eta = if (etaInSeconds > 0) "${etaInSeconds}s" else ""
                    
                    Log.d(TAG, "Progress: $currentProgress%, Speed: $speed")
                    
                    runBlocking {
                        repository.updateProgress(downloadId, progress, speed, eta)
                    }
                    setForegroundAsync(createForegroundInfo("Downloading: $currentProgress%", currentProgress))
                }
            }

            repository.updateStatus(downloadId, DownloadStatus.COMPLETED)
            
            val finalFiles = downloadDir.listFiles()
            Log.d(TAG, "Download finished. Files in temp dir: ${finalFiles?.joinToString { it.name } ?: "None"}")

            if (globalDownloadLocation.isNotEmpty()) {
                moveFilesToSaf(downloadDir, globalDownloadLocation)
            } else {
                moveToPublicDownloads(downloadDir)
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${download.url}", e)
            repository.updateDownload(download.copy(status = DownloadStatus.FAILED, error = e.message))
            Result.failure()
        }
    }

    private suspend fun moveToPublicDownloads(tempDir: File) = withContext(Dispatchers.IO) {
        try {
            val files = tempDir.listFiles() ?: return@withContext
            Log.d(TAG, "Moving ${files.size} files to Public Downloads")

            for (file in files) {
                if (file.isDirectory) continue
                
                val mimeType = getMimeType(file)
                
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/LinkVault")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                } else {
                    // Fallback for older versions (might require WRITE_EXTERNAL_STORAGE)
                    @Suppress("DEPRECATION")
                    Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
                    MediaStore.Files.getContentUri("external")
                }

                val uri = context.contentResolver.insert(collection, contentValues)
                if (uri != null) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            file.inputStream().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            contentValues.clear()
                            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                            context.contentResolver.update(uri, contentValues, null, null)
                        }
                        file.delete()
                        Log.d(TAG, "Successfully moved ${file.name} to Public Downloads: $uri")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to write data to MediaStore for ${file.name}", e)
                    }
                } else {
                    Log.e(TAG, "Failed to create MediaStore entry for ${file.name}")
                }
            }
            tempDir.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error moving files to Public Downloads", e)
        }
    }

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "webm" -> "video/webm"
            "mp4" -> "video/mp4"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }

    private suspend fun moveFilesToSaf(tempDir: File, treeUriString: String) = withContext(Dispatchers.IO) {
        try {
            val treeUri = Uri.parse(treeUriString)
            val docFile = DocumentFile.fromTreeUri(context, treeUri)
            if (docFile == null || !docFile.exists()) {
                Log.e(TAG, "SAF destination does not exist: $treeUriString")
                return@withContext
            }

            val files = tempDir.listFiles() ?: return@withContext
            Log.d(TAG, "Moving ${files.size} files to SAF location")

            for (file in files) {
                if (file.isDirectory) continue
                Log.d(TAG, "Attempting to move ${file.name} (${file.length()} bytes) to SAF")

                val mimeType = getMimeType(file)

                val existingFile = docFile.findFile(file.name)
                if (existingFile != null) {
                    Log.w(TAG, "File already exists in SAF, skipping: ${file.name}")
                    file.delete()
                    continue
                }

                val newFile = docFile.createFile(mimeType, file.name)
                if (newFile != null) {
                    try {
                        context.contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                            file.inputStream().use { inputStream ->
                                val bytesCopied = inputStream.copyTo(outputStream)
                                Log.d(TAG, "Copied $bytesCopied bytes for ${file.name}")
                            }
                        }
                        file.delete()
                        Log.d(TAG, "Successfully moved ${file.name} to SAF: ${newFile.uri}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to write data to SAF for ${file.name}", e)
                    }
                } else {
                    Log.e(TAG, "Failed to create document in SAF for ${file.name}")
                }
            }
            
            tempDir.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error moving files to SAF", e)
        }
    }

    private fun createForegroundInfo(content: String, progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("LinkVault Downloader")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, progress <= 0)
            .build()
            
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
