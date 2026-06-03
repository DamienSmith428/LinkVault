package com.linkvault.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linkvault.data.preferences.UserPreferences
import com.linkvault.update.UpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferences,
    private val updateManager: UpdateManager
) : ViewModel() {

    val themeMode: StateFlow<String> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val downloadLocation: StateFlow<String> = preferences.downloadLocation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val lastUpdateCheck: StateFlow<Long> = preferences.lastUpdateCheck
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val latestVersion: StateFlow<String> = preferences.latestVersion
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _isCheckingUpdates = MutableStateFlow(false)
    val isCheckingUpdates = _isCheckingUpdates.asStateFlow()

    val isUpdateAvailable: StateFlow<Boolean> = combine(latestVersion) { versions ->
        val latest = versions[0]
        if (latest.isEmpty()) false else updateManager.isNewerVersion(latest)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setThemeMode(mode: String) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setDownloadLocation(path: String) {
        viewModelScope.launch { preferences.setDownloadLocation(path) }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _isCheckingUpdates.value = true
            updateManager.checkForUpdates()
            _isCheckingUpdates.value = false
        }
    }
}
