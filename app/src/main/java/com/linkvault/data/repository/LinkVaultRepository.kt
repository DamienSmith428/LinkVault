package com.linkvault.data.repository

import com.linkvault.data.local.dao.FolderDao
import com.linkvault.data.local.dao.LinkDao
import com.linkvault.data.local.entity.FolderEntity
import com.linkvault.data.local.entity.LinkEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LinkVaultRepository @Inject constructor(
    private val folderDao: FolderDao,
    private val linkDao: LinkDao
) {


    fun getAllFolders(): Flow<List<Folder>> {
        return folderDao.getAllFolders().flatMapLatest { folders ->
            if (folders.isEmpty()) {
                flow { emit(emptyList()) }
            } else {
                val countFlows = folders.map { folder ->
                    linkDao.getLinkCountForFolder(folder.id).map { count ->
                        folder to count
                    }
                }
                combine(countFlows) { pairs ->
                    pairs.map { (folder, count) ->
                        folder.toDomain(count)
                    }
                }
            }
        }
    }

    fun searchFolders(query: String): Flow<List<Folder>> {
        return folderDao.searchFolders(query).flatMapLatest { folders ->
            if (folders.isEmpty()) {
                flow { emit(emptyList()) }
            } else {
                val countFlows = folders.map { folder ->
                    linkDao.getLinkCountForFolder(folder.id).map { count ->
                        folder to count
                    }
                }
                combine(countFlows) { pairs ->
                    pairs.map { (folder, count) ->
                        folder.toDomain(count)
                    }
                }
            }
        }
    }

    suspend fun getFolderById(id: Long): Folder? {
        val entity = folderDao.getFolderById(id) ?: return null
        val count = linkDao.getLinkCountSync(id)
        return entity.toDomain(count)
    }

    suspend fun createFolder(name: String): Long {
        return folderDao.insertFolder(FolderEntity(name = name))
    }

    suspend fun renameFolder(id: Long, newName: String) {
        val existing = folderDao.getFolderById(id) ?: return
        folderDao.updateFolder(existing.copy(name = newName))
    }

    suspend fun deleteFolder(id: Long) {
        folderDao.deleteFolderById(id)
    }


    fun getLinksForFolder(folderId: Long): Flow<List<Link>> {
        return linkDao.getLinksForFolder(folderId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun searchLinks(query: String): Flow<List<Link>> {
        return linkDao.searchLinks(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun saveLink(
        folderId: Long, 
        url: String, 
        title: String = "", 
        artist: String = "",
        album: String = "",
        releaseYear: String = "",
        forceInsert: Boolean = false
    ): SaveLinkResult {
        return try {
            if (!forceInsert) {
                val duplicate = linkDao.findDuplicate(folderId, url)
                if (duplicate != null) {
                    return SaveLinkResult.DuplicateFound
                }
            }
            linkDao.insertLink(
                LinkEntity(
                    folderId = folderId, 
                    url = url, 
                    title = title,
                    artist = artist,
                    album = album,
                    releaseYear = releaseYear
                )
            )
            SaveLinkResult.Success
        } catch (e: Exception) {
            SaveLinkResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun updateLink(link: Link) {
        val entity = LinkEntity(
            id = link.id,
            folderId = link.folderId,
            url = link.url,
            title = link.title,
            artist = link.artist,
            album = link.album,
            releaseYear = link.releaseYear,
            dateAdded = link.dateAdded
        )
        linkDao.updateLink(entity)
    }

    suspend fun deleteLink(id: Long) {
        val entity = linkDao.getLinkById(id) ?: return
        linkDao.deleteLink(entity)
    }

    suspend fun deleteLinks(ids: List<Long>) {
        linkDao.deleteLinksById(ids)
    }

    suspend fun moveLinks(ids: List<Long>, targetFolderId: Long) {
        linkDao.moveLinksToFolder(ids, targetFolderId)
    }


    suspend fun getFoldersWithLinksForExport(): List<FolderWithLinks> {
        val folderEntities = folderDao.getAllFoldersSync()
        return folderEntities.map { folderEntity ->
            val linkEntities = linkDao.getLinksForFolderSync(folderEntity.id)
            FolderWithLinks(
                folderName = folderEntity.name,
                links = linkEntities.map { LinkExport(url = it.url, title = it.title) }
            )
        }
    }

    suspend fun getFoldersWithLinksForExportByIds(ids: List<Long>): List<FolderWithLinks> {
        return ids.mapNotNull { id ->
            val folderEntity = folderDao.getFolderByIdSync(id) ?: return@mapNotNull null
            val linkEntities = linkDao.getLinksForFolderSync(folderEntity.id)
            FolderWithLinks(
                folderName = folderEntity.name,
                links = linkEntities.map { LinkExport(url = it.url, title = it.title) }
            )
        }
    }

    suspend fun importFoldersWithLinks(data: List<FolderWithLinks>) {
        data.forEach { folderWithLinks ->
            var folderId = folderDao.getFolderByName(folderWithLinks.folderName)?.id
            if (folderId == null) {
                folderId = folderDao.insertFolder(FolderEntity(name = folderWithLinks.folderName))
            }

            folderWithLinks.links.forEach { linkExport ->
                val duplicate = linkDao.findDuplicate(folderId!!, linkExport.url)
                if (duplicate == null) {
                    linkDao.insertLink(
                        LinkEntity(
                            folderId = folderId!!,
                            url = linkExport.url,
                            title = linkExport.title
                        )
                    )
                }
            }
        }
    }


    private fun FolderEntity.toDomain(linkCount: Int = 0) = Folder(
        id = id,
        name = name,
        linkCount = linkCount,
        dateCreated = dateCreated
    )

    private fun LinkEntity.toDomain() = Link(
        id = id,
        folderId = folderId,
        url = url,
        title = title,
        artist = artist,
        album = album,
        releaseYear = releaseYear,
        dateAdded = dateAdded
    )
}
