package com.linkvault

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LinkVaultApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private val applicationScope = CoroutineScope(Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        // Ensure WorkManager is initialized with HiltWorkerFactory
        initWorkManager()
        
        initYoutubeDL()
        createNotificationChannel()
    }

    private fun initWorkManager() {
        try {
            WorkManager.initialize(this, workManagerConfiguration)
            Log.d("LinkVault", "WorkManager initialized successfully with HiltWorkerFactory")
        } catch (e: IllegalStateException) {
            Log.w("LinkVault", "WorkManager already initialized. If this happened via default initializer, Hilt injection might fail.")
        }
    }

    private fun initYoutubeDL() {
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            Log.d("LinkVault", "YoutubeDL and FFmpeg initialized successfully")
            
            // Update extractors in background to support latest YouTube changes
            applicationScope.launch {
                try {
                    YoutubeDL.getInstance().updateYoutubeDL(this@LinkVaultApplication)
                    Log.d("LinkVault", "YoutubeDL extractors updated successfully")
                } catch (e: Exception) {
                    Log.e("LinkVault", "Failed to update YoutubeDL extractors", e)
                }
            }
        } catch (e: Exception) {
            Log.e("LinkVault", "Failed to initialize YoutubeDL", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Downloads"
            val descriptionText = "Notifications for media downloads"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel("download_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
