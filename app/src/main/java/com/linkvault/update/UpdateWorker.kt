package com.linkvault.update

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class UpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val updateManager: UpdateManager,
    private val notificationHelper: UpdateNotificationHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val newVersion = updateManager.checkForUpdates()
        if (newVersion != null) {
            notificationHelper.showUpdateNotification(newVersion)
        }
        return Result.success()
    }
}
