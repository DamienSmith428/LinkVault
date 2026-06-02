package com.linkvault.data.backup

import android.content.Context
import android.net.Uri
import com.linkvault.data.repository.FolderWithLinks
import com.linkvault.data.repository.LinkExport
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun exportToZip(data: List<FolderWithLinks>, destinationUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val jsonString = dataToJson(data)
            context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zipOut ->
                    val zipEntry = ZipEntry("links_backup.json")
                    zipOut.putNextEntry(zipEntry)
                    zipOut.write(jsonString.toByteArray())
                    zipOut.closeEntry()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importFromZip(sourceUri: Uri): Result<List<FolderWithLinks>> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipIn ->
                    var entry = zipIn.getNextEntry()
                    while (entry != null) {
                        if (entry.name == "links_backup.json") {
                            val jsonString = zipIn.bufferedReader().readText()
                            return@withContext Result.success(jsonToData(jsonString))
                        }
                        entry = zipIn.getNextEntry()
                    }
                }
            }
            Result.failure(Exception("Backup file not found in ZIP"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun dataToJson(data: List<FolderWithLinks>): String {
        val root = JSONArray()
        data.forEach { folder ->
            val folderObj = JSONObject().apply {
                put("folderName", folder.folderName)
                val linksArr = JSONArray()
                folder.links.forEach { link ->
                    linksArr.put(JSONObject().apply {
                        put("url", link.url)
                        put("title", link.title)
                    })
                }
                put("links", linksArr)
            }
            root.put(folderObj)
        }
        return root.toString(2)
    }

    private fun jsonToData(jsonString: String): List<FolderWithLinks> {
        val data = mutableListOf<FolderWithLinks>()
        val root = JSONArray(jsonString)
        for (i in 0 until root.length()) {
            val folderObj = root.getJSONObject(i)
            val folderName = folderObj.getString("folderName")
            val linksArr = folderObj.getJSONArray("links")
            val links = mutableListOf<LinkExport>()
            for (j in 0 until linksArr.length()) {
                val linkObj = linksArr.getJSONObject(j)
                links.add(LinkExport(
                    url = linkObj.getString("url"),
                    title = linkObj.optString("title", "")
                ))
            }
            data.add(FolderWithLinks(folderName, links))
        }
        return data
    }
}
