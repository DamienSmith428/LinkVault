package com.linkvault.data.repository

data class Folder(
    val id: Long,
    val name: String,
    val linkCount: Int = 0,
    val dateCreated: Long
)

data class Link(
    val id: Long,
    val folderId: Long,
    val url: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val releaseYear: String = "",
    val dateAdded: Long
)

data class FolderWithLinks(
    val folderName: String,
    val links: List<LinkExport>
)

data class LinkExport(
    val url: String,
    val title: String = ""
)

sealed class SaveLinkResult {
    object Success : SaveLinkResult()
    object DuplicateFound : SaveLinkResult()
    data class Error(val message: String) : SaveLinkResult()
}
