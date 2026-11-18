package com.sumanonline.sumanmovies.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumanonline.sumanmovies.data.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val autoPlayNextEpisode: Boolean = true,
    val preferredSubtitleLanguage: String = "English",
    val subtitlesByDefault: Boolean = true,
    val downloadQuality: String = "Auto",
    val autoDeleteWatchedDownloads: Boolean = false,
    val wifiOnly: Boolean = false,
    val downloadsFolder: String = "SumanMovies",
    val downloadStorage: String = "0 KB",
    val cacheSize: String = "2.4 MB"
)

class SettingsViewModel(private val repository: MediaRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun toggleAutoPlay() {
        _uiState.value = _uiState.value.copy(autoPlayNextEpisode = !_uiState.value.autoPlayNextEpisode)
    }

    fun toggleSubtitlesByDefault() {
        _uiState.value = _uiState.value.copy(subtitlesByDefault = !_uiState.value.subtitlesByDefault)
    }

    fun toggleAutoDeleteDownloads() {
        _uiState.value = _uiState.value.copy(autoDeleteWatchedDownloads = !_uiState.value.autoDeleteWatchedDownloads)
    }

    fun toggleWifiOnly() {
        _uiState.value = _uiState.value.copy(wifiOnly = !_uiState.value.wifiOnly)
    }

    fun clearImageCache() {
        _uiState.value = _uiState.value.copy(cacheSize = "0 KB")
    }

    fun clearSearchHistory() {
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
