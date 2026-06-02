package com.linkvault.ui.screens.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkvault.data.repository.Folder
import com.linkvault.data.repository.Link
import com.linkvault.data.repository.LinkVaultRepository
import com.linkvault.data.repository.DownloadRepository
import com.linkvault.data.repository.SaveLinkResult
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import javax.inject.Inject
import android.util.Log

data class FolderUiState(
    val folder: Folder? = null,
    val selectedIds: Set<Long> = emptySet(),
    val showDeleteConfirm: Boolean = false,
    val showMoveSheet: Boolean = false,
    val showDownloadOptions: Boolean = false,
    val showAddLinkDialog: Boolean = false,
    val isFetchingMetadata: Boolean = false,
    val newLinkUrl: String = "",
    val newLinkTitle: String = "",
    val linkToRename: Link? = null,
    val renameTitle: String = "",
    val renameArtist: String = "",
    val renameAlbum: String = "",
    val renameYear: String = "",
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class FolderViewModel @Inject constructor(
    private val repository: LinkVaultRepository,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FolderUiState())
    val uiState: StateFlow<FolderUiState> = _uiState.asStateFlow()

    private val folderIdFlow = MutableStateFlow(-1L)

    val links: StateFlow<List<Link>> = folderIdFlow
        .flatMapLatest { id ->
            if (id == -1L) flow { emit(emptyList()) }
            else repository.getLinksForFolder(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFolders: StateFlow<List<Folder>> = repository.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun initialize(folderId: Long) {
        folderIdFlow.value = folderId
        viewModelScope.launch {
            val folder = repository.getFolderById(folderId)
            _uiState.update { it.copy(folder = folder) }
        }
    }

    fun toggleSelection(linkId: Long) {
        _uiState.update { state ->
            val newSet = state.selectedIds.toMutableSet()
            if (newSet.contains(linkId)) newSet.remove(linkId) else newSet.add(linkId)
            state.copy(selectedIds = newSet)
        }
    }

    fun selectAll() {
        _uiState.update { it.copy(selectedIds = links.value.map { l -> l.id }.toSet()) }
    }

    fun deselectAll() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun clearSelection() {
        deselectAll()
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val ids = _uiState.value.selectedIds.toList()
            repository.deleteLinks(ids)
            _uiState.update {
                it.copy(
                    selectedIds = emptySet(),
                    showDeleteConfirm = false,
                    successMessage = "Deleted ${ids.size} link(s)"
                )
            }
        }
    }

    fun deleteLink(linkId: Long) {
        viewModelScope.launch {
            repository.deleteLink(linkId)
            _uiState.update { it.copy(successMessage = "Link deleted") }
        }
    }

    fun showRenameLinkDialog(link: Link) {
        _uiState.update { 
            it.copy(
                linkToRename = link,
                renameTitle = link.title,
                renameArtist = link.artist,
                renameAlbum = link.album,
                renameYear = link.releaseYear
            )
        }
    }

    fun dismissRenameLinkDialog() {
        _uiState.update { it.copy(linkToRename = null) }
    }

    fun onRenameTitleChange(value: String) {
        _uiState.update { it.copy(renameTitle = value) }
    }

    fun onRenameArtistChange(value: String) {
        _uiState.update { it.copy(renameArtist = value) }
    }

    fun onRenameAlbumChange(value: String) {
        _uiState.update { it.copy(renameAlbum = value) }
    }

    fun onRenameYearChange(value: String) {
        _uiState.update { it.copy(renameYear = value) }
    }

    fun renameLink() {
        val link = _uiState.value.linkToRename ?: return
        viewModelScope.launch {
            val updatedLink = link.copy(
                title = _uiState.value.renameTitle,
                artist = _uiState.value.renameArtist,
                album = _uiState.value.renameAlbum,
                releaseYear = _uiState.value.renameYear
            )
            repository.updateLink(updatedLink)
            _uiState.update { it.copy(linkToRename = null, successMessage = "Link updated") }
        }
    }

    fun showDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = true) }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    fun showMoveSheet() {
        _uiState.update { it.copy(showMoveSheet = true) }
    }

    fun dismissMoveSheet() {
        _uiState.update { it.copy(showMoveSheet = false) }
    }

    fun showDownloadOptions() {
        _uiState.update { it.copy(showDownloadOptions = true) }
    }

    fun dismissDownloadOptions() {
        _uiState.update { it.copy(showDownloadOptions = false) }
    }

    fun showAddLinkDialog() {
        _uiState.update { it.copy(showAddLinkDialog = true, newLinkUrl = "", newLinkTitle = "") }
    }

    fun dismissAddLinkDialog() {
        _uiState.update { it.copy(showAddLinkDialog = false, newLinkUrl = "", newLinkTitle = "") }
        fetchJob?.cancel()
    }

    private var fetchJob: Job? = null

    fun onNewLinkUrlChange(url: String) {
        _uiState.update { it.copy(newLinkUrl = url) }
        
        fetchJob?.cancel()
        if (url.startsWith("http")) {
            fetchJob = viewModelScope.launch {
                delay(800)
                fetchMetadata(url)
            }
        }
    }

    private fun fetchMetadata(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingMetadata = true) }
            
            val quickTitle = fetchJsoupMetadata(url)
            if (quickTitle != null && _uiState.value.newLinkTitle.isBlank()) {
                val cleanTitle = quickTitle.replace(" - YouTube", "").trim()
                _uiState.update { it.copy(newLinkTitle = cleanTitle) }
            }

            if (url.contains("youtube.com") || url.contains("youtu.be")) {
                try {
                    val metadata = withContext(Dispatchers.IO) {
                        val request = YoutubeDLRequest(url)
                        request.addOption("--dump-json")
                        val info = YoutubeDL.getInstance().getInfo(request)
                        val artist = info.uploader ?: ""
                        val title = info.title ?: ""
                        if (artist.isNotEmpty() && title.isNotEmpty() && !title.contains(artist)) {
                            "$artist - $title"
                        } else {
                            title
                        }
                    }
                    
                    if (metadata.isNotEmpty()) {
                        val cleanTitle = metadata.replace(" - YouTube", "").trim()
                        _uiState.update { it.copy(newLinkTitle = cleanTitle) }
                    }
                } catch (e: Exception) {
                }
            }
            
            _uiState.update { it.copy(isFetchingMetadata = false) }
        }
    }

    private suspend fun fetchJsoupMetadata(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .timeout(5000)
                .get()
            
            doc.select("meta[property=og:title]").attr("content").takeIf { it.isNotBlank() }
                ?: doc.title().takeIf { it.isNotBlank() }
                ?: doc.select("meta[name=title]").attr("content")
        } catch (e: Exception) {
            null
        }
    }

    fun onNewLinkTitleChange(title: String) {
        _uiState.update { it.copy(newLinkTitle = title) }
    }

    fun addLink() {
        val folderId = folderIdFlow.value
        if (folderId == -1L) return
        
        val url = _uiState.value.newLinkUrl.trim()
        val title = _uiState.value.newLinkTitle.trim()
        
        if (url.isBlank()) return

        viewModelScope.launch {
            val result = repository.saveLink(folderId, url, title)
            when (result) {
                is SaveLinkResult.Success -> {
                    _uiState.update { 
                        it.copy(
                            showAddLinkDialog = false, 
                            successMessage = "Link added successfully"
                        ) 
                    }
                }
                is SaveLinkResult.DuplicateFound -> {
                    _uiState.update { it.copy(errorMessage = "Link already exists in this folder") }
                }
                is SaveLinkResult.Error -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }

    fun addToQueue(format: String) {
        val selectedLinks = links.value.filter { it.id in _uiState.value.selectedIds }
        val folderName = _uiState.value.folder?.name ?: "Unknown"
        
        viewModelScope.launch {
            selectedLinks.forEach { link ->
                downloadRepository.addToQueue(
                    linkId = link.id,
                    url = link.url,
                    title = link.title,
                    folderName = folderName,
                    format = format,
                    path = "" 
                )
            }
            _uiState.update {
                it.copy(
                    selectedIds = emptySet(),
                    showDownloadOptions = false,
                    successMessage = "Added ${selectedLinks.size} item(s) to queue"
                )
            }
        }
    }

    fun moveSelectedToFolder(targetFolderId: Long) {
        viewModelScope.launch {
            val ids = _uiState.value.selectedIds.toList()
            repository.moveLinks(ids, targetFolderId)
            _uiState.update {
                it.copy(
                    selectedIds = emptySet(),
                    showMoveSheet = false,
                    successMessage = "Moved ${ids.size} link(s)"
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
