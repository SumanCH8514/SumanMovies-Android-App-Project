package com.sumanonline.sumanmovies.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumanonline.sumanmovies.data.download.DownloadProgress
import com.sumanonline.sumanmovies.data.local.entities.DownloadEntity
import com.sumanonline.sumanmovies.data.local.entities.FavoriteEntity
import com.sumanonline.sumanmovies.data.repository.MediaRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class LibraryTab {
    FAVORITES,
    DOWNLOADS
}

data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.FAVORITES,
    val favorites: List<FavoriteEntity> = emptyList(),
    val downloads: List<DownloadEntity> = emptyList(),
    val progressMap: Map<String, DownloadProgress> = emptyMap(),
    val playlists: List<String> = listOf("My Weekend Watchlist")
)

class LibraryViewModel(private val repository: MediaRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        observeFavorites()
        observeDownloads()
        observeProgress()
        startDownloadSync()
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            repository.getFavoritesFlow().collectLatest { list ->
                _uiState.value = _uiState.value.copy(favorites = list)
            }
        }
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            repository.getDownloadsFlow().collectLatest { list ->
                _uiState.value = _uiState.value.copy(downloads = list)
            }
        }
    }

    private fun observeProgress() {
        viewModelScope.launch {
            repository.getDownloadProgressFlow().collectLatest { map ->
                _uiState.value = _uiState.value.copy(progressMap = map)
            }
        }
    }

    private fun startDownloadSync() {
        viewModelScope.launch {
            while (isActive) {
                repository.syncDownloads()
                delay(3000)
            }
        }
    }

    fun selectTab(tab: LibraryTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun pauseDownload(download: DownloadEntity) {
        viewModelScope.launch {
            repository.pauseDownload(download.id)
        }
    }

    fun resumeDownload(download: DownloadEntity) {
        viewModelScope.launch {
            repository.resumeDownload(download.id)
        }
    }

    fun deleteDownload(download: DownloadEntity) {
        viewModelScope.launch {
            repository.deleteDownloadById(download.id)
        }
    }

    fun addPlaylist(name: String) {
        if (name.isNotBlank()) {
            _uiState.value = _uiState.value.copy(playlists = _uiState.value.playlists + name)
        }
    }
}
