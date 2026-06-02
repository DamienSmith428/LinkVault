package com.linkvault.ui.screens.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkvault.data.backup.BackupManager
import com.linkvault.data.repository.Folder
import com.linkvault.data.repository.LinkVaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val showCreateFolderDialog: Boolean = false,
    val newFolderName: String = "",
    val folderToRename: Folder? = null,
    val folderToDelete: Folder? = null,
    val selectedFolderIds: Set<Long> = emptySet(),
    val isImporting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: LinkVaultRepository,
    private val backupManager: BackupManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val folders: StateFlow<List<Folder>> = repository.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun showCreateFolderDialog() {
        _uiState.update { it.copy(showCreateFolderDialog = true, newFolderName = "") }
    }

    fun dismissCreateFolderDialog() {
        _uiState.update { it.copy(showCreateFolderDialog = false, newFolderName = "") }
    }

    fun onNewFolderNameChange(name: String) {
        _uiState.update { it.copy(newFolderName = name) }
    }

    fun createFolder() {
        val name = _uiState.value.newFolderName.trim()
        if (name.isBlank()) return

        viewModelScope.launch {
            repository.createFolder(name)
            _uiState.update {
                it.copy(
                    showCreateFolderDialog = false,
                    newFolderName = "",
                    successMessage = "Folder \"$name\" created"
                )
            }
        }
    }

    fun showRenameDialog(folder: Folder) {
        _uiState.update { it.copy(folderToRename = folder, newFolderName = folder.name) }
    }

    fun dismissRenameDialog() {
        _uiState.update { it.copy(folderToRename = null, newFolderName = "") }
    }

    fun renameFolder() {
        val folder = _uiState.value.folderToRename ?: return
        val name = _uiState.value.newFolderName.trim()
        if (name.isBlank()) return

        viewModelScope.launch {
            repository.renameFolder(folder.id, name)
            _uiState.update {
                it.copy(
                    folderToRename = null,
                    newFolderName = "",
                    successMessage = "Folder renamed"
                )
            }
        }
    }

    fun showDeleteDialog(folder: Folder) {
        _uiState.update { it.copy(folderToDelete = folder) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(folderToDelete = null) }
    }

    fun deleteFolder() {
        val folder = _uiState.value.folderToDelete ?: return
        viewModelScope.launch {
            repository.deleteFolder(folder.id)
            _uiState.update {
                it.copy(
                    folderToDelete = null,
                    successMessage = "Folder deleted"
                )
            }
        }
    }

    fun toggleFolderSelection(folderId: Long) {
        _uiState.update { state ->
            val newSelection = state.selectedFolderIds.toMutableSet()
            if (newSelection.contains(folderId)) {
                newSelection.remove(folderId)
            } else {
                newSelection.add(folderId)
            }
            state.copy(selectedFolderIds = newSelection)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedFolderIds = emptySet()) }
    }

    fun exportSelectedFolders(uri: Uri) {
        val ids = _uiState.value.selectedFolderIds.toList()
        if (ids.isEmpty()) return

        viewModelScope.launch {
            val data = repository.getFoldersWithLinksForExportByIds(ids)
            backupManager.exportToZip(data, uri).onSuccess {
                _uiState.update { 
                    it.copy(
                        selectedFolderIds = emptySet(),
                        successMessage = "Exported ${ids.size} folder(s)"
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(errorMessage = "Export failed: ${e.message}") }
            }
        }
    }

    fun importData(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            backupManager.importFromZip(uri)
                .onSuccess { data ->
                    repository.importFoldersWithLinks(data)
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            successMessage = "Imported ${data.size} folder(s)"
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            errorMessage = "Import failed: ${e.message}"
                        )
                    }
                }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
