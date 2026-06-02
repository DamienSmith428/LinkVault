package com.linkvault.ui.screens.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkvault.data.repository.Folder
import com.linkvault.data.repository.LinkVaultRepository
import com.linkvault.data.repository.SaveLinkResult
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import javax.inject.Inject
import android.util.Log

data class ShareUiState(
    val url: String = "",
    val title: String = "",
    val isSaving: Boolean = false,
    val isFetchingMetadata: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val showDuplicateDialog: Boolean = false,
    val newFolderName: String = "",
    val showNewFolderInput: Boolean = false,
    val errorMessage: String? = null,
    val pendingFolderId: Long? = null
)

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val repository: LinkVaultRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    val folders: StateFlow<List<Folder>> = repository.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setUrl(url: String) {
        _uiState.update { it.copy(url = url) }
        fetchMetadata(url)
    }

    private fun fetchMetadata(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isFetchingMetadata = true) }
            
            val quickTitle = fetchJsoupMetadata(url)
            if (quickTitle != null) {
                val cleanTitle = quickTitle.replace(" - YouTube", "").trim()
                _uiState.update { it.copy(title = cleanTitle) }
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
                        _uiState.update { it.copy(title = cleanTitle) }
                    }
                } catch (e: Exception) {
                    Log.e("ShareViewModel", "YoutubeDL metadata fetch failed", e)
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

    fun onTitleChange(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun saveToFolder(folderId: Long, forceInsert: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, pendingFolderId = folderId) }
            val result = repository.saveLink(
                folderId = folderId, 
                url = _uiState.value.url, 
                title = _uiState.value.title,
                forceInsert = forceInsert
            )
            when (result) {
                is SaveLinkResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            savedSuccessfully = true,
                            showDuplicateDialog = false
                        )
                    }
                }
                is SaveLinkResult.DuplicateFound -> {
                    _uiState.update {
                        it.copy(isSaving = false, showDuplicateDialog = true)
                    }
                }
                is SaveLinkResult.Error -> {
                    _uiState.update {
                        it.copy(isSaving = false, errorMessage = result.message)
                    }
                }
            }
        }
    }

    fun saveAnyway() {
        val folderId = _uiState.value.pendingFolderId ?: return
        saveToFolder(folderId, forceInsert = true)
    }

    fun dismissDuplicateDialog() {
        _uiState.update { it.copy(showDuplicateDialog = false) }
    }

    fun createFolderAndSave() {
        val name = _uiState.value.newFolderName.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            val folderId = repository.createFolder(name)
            saveToFolder(folderId)
        }
    }

    fun onNewFolderNameChange(name: String) {
        _uiState.update { it.copy(newFolderName = name) }
    }

    fun toggleNewFolderInput() {
        _uiState.update { it.copy(showNewFolderInput = !it.showNewFolderInput, newFolderName = "") }
    }
}
