package com.linkvault.update

import com.linkvault.BuildConfig
import com.linkvault.data.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ConfigResponse(
    val app: AppConfig
)

@Serializable
data class AppConfig(
    val latestVersion: String
)

@Singleton
class UpdateManager @Inject constructor(
    private val preferences: UserPreferences,
    private val okHttpClient: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdates(): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://raw.githubusercontent.com/DamienSmith428/LinkVault-web/main/docs/config.json")
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                val config = json.decodeFromString<ConfigResponse>(body)
                val remoteVersion = config.app.latestVersion
                
                preferences.setLatestVersion(remoteVersion)
                preferences.setLastUpdateCheck(System.currentTimeMillis())

                if (isNewerVersion(remoteVersion)) {
                    remoteVersion
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun isNewerVersion(remoteVersion: String): Boolean {
        val currentVersionName = BuildConfig.VERSION_NAME
        val normalizedCurrent = currentVersionName.removeSuffix(".0")
        
        // Simple version comparison logic
        // Assuming X.Y format for remote and normalized current
        val currentParts = normalizedCurrent.split(".").mapNotNull { it.toIntOrNull() }
        val remoteParts = remoteVersion.split(".").mapNotNull { it.toIntOrNull() }
        
        for (i in 0 until minOf(currentParts.size, remoteParts.size)) {
            if (remoteParts[i] > currentParts[i]) return true
            if (remoteParts[i] < currentParts[i]) return false
        }
        
        return remoteParts.size > currentParts.size
    }
}
