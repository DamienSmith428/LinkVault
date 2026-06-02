package com.linkvault.data.local.dao

import androidx.room.*
import com.linkvault.data.local.entity.LinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao {

    @Query("SELECT * FROM links WHERE folderId = :folderId ORDER BY dateAdded DESC")
    fun getLinksForFolder(folderId: Long): Flow<List<LinkEntity>>

    @Query("SELECT * FROM links WHERE folderId = :folderId ORDER BY dateAdded DESC")
    suspend fun getLinksForFolderSync(folderId: Long): List<LinkEntity>

    @Query("SELECT * FROM links WHERE id = :linkId")
    suspend fun getLinkById(linkId: Long): LinkEntity?

    @Query("SELECT COUNT(*) FROM links WHERE folderId = :folderId")
    fun getLinkCountForFolder(folderId: Long): Flow<Int>

    @Query("SELECT * FROM links WHERE url LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' ORDER BY dateAdded DESC")
    fun searchLinks(query: String): Flow<List<LinkEntity>>

    @Query("SELECT * FROM links WHERE folderId = :folderId AND url = :url LIMIT 1")
    suspend fun findDuplicate(folderId: Long, url: String): LinkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: LinkEntity): Long

    @Update
    suspend fun updateLink(link: LinkEntity)

    @Delete
    suspend fun deleteLink(link: LinkEntity)

    @Query("DELETE FROM links WHERE id IN (:ids)")
    suspend fun deleteLinksById(ids: List<Long>)

    @Query("UPDATE links SET folderId = :newFolderId WHERE id IN (:ids)")
    suspend fun moveLinksToFolder(ids: List<Long>, newFolderId: Long)

    @Query("SELECT COUNT(*) FROM links WHERE folderId = :folderId")
    suspend fun getLinkCountSync(folderId: Long): Int
}
