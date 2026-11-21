package com.sumanonline.sumanmovies.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumanonline.sumanmovies.data.local.AppPreferencesManager
import com.sumanonline.sumanmovies.data.remote.update.AppUpdateInfo
import com.sumanonline.sumanmovies.data.remote.update.AppUpdateManager
import com.sumanonline.sumanmovies.data.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val autoPlayNextEpisode: Boolean = true,
    val autoSkipIntro: Boolean = false,
    val autoSkipCredits: Boolean = false,
    val preferredSubtitleLanguage: String = "English",
    val preferredAudioLanguage: String = "English",
    val subtitlesByDefault: Boolean = true,
    val defaultPlaybackSpeed: Float = 1.0f,
    val doubleTapSeekDuration: Int = 10,
    val backgroundPlayback: Boolean = false,
    val hardwareAcceleration: Boolean = true,
    val downloadQuality: String = "Auto",
    val autoDeleteWatchedDownloads: Boolean = false,
    val wifiOnly: Boolean = false,
    val downloadsFolder: String = "Movies/SumanMovies",
    val downloadStorage: String = "0 B",
    val cacheSize: String = "0 B",
    val isCheckingForUpdates: Boolean = false,
    val updateInfo: AppUpdateInfo? = null,
    val showUpdateDialog: Boolean = false,
    val showUpToDateDialog: Boolean = false,
    val showSubtitleLanguageDialog: Boolean = false,
    val showAudioLanguageDialog: Boolean = false,
    val showPlaybackSpeedDialog: Boolean = false,
    val showSeekDurationDialog: Boolean = false,
    val showClearSearchHistoryDialog: Boolean = false,
    val showClearWatchHistoryDialog: Boolean = false,
    val showDownloadQualityDialog: Boolean = false,
    val userToastMessage: String? = null
)

class SettingsViewModel(
    private val repository: MediaRepository,
    private val preferencesManager: AppPreferencesManager,
    private val updateManager: AppUpdateManager = AppUpdateManager()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            autoPlayNextEpisode = preferencesManager.autoPlayNextEpisode.value,
            autoSkipIntro = preferencesManager.autoSkipIntro.value,
            autoSkipCredits = preferencesManager.autoSkipCredits.value,
            preferredSubtitleLanguage = preferencesManager.preferredSubtitleLanguage.value,
            preferredAudioLanguage = preferencesManager.preferredAudioLanguage.value,
            subtitlesByDefault = preferencesManager.subtitlesByDefault.value,
            defaultPlaybackSpeed = preferencesManager.defaultPlaybackSpeed.value,
            doubleTapSeekDuration = preferencesManager.doubleTapSeekDuration.value,
            backgroundPlayback = preferencesManager.backgroundPlayback.value,
            hardwareAcceleration = preferencesManager.hardwareAcceleration.value,
            downloadQuality = preferencesManager.downloadQuality.value,
            autoDeleteWatchedDownloads = preferencesManager.autoDeleteWatchedDownloads.value,
            wifiOnly = preferencesManager.wifiOnly.value,
            cacheSize = preferencesManager.formatBytes(preferencesManager.calculateCacheSizeBytes()),
            downloadStorage = preferencesManager.formatBytes(preferencesManager.calculateDownloadStorageBytes())
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refreshStorageMetrics()
    }

    fun refreshStorageMetrics() {
        val cache = preferencesManager.formatBytes(preferencesManager.calculateCacheSizeBytes())
        val downloads = preferencesManager.formatBytes(preferencesManager.calculateDownloadStorageBytes())
        _uiState.value = _uiState.value.copy(
            cacheSize = cache,
            downloadStorage = downloads
        )
    }

    fun toggleAutoPlay() {
        val newVal = !_uiState.value.autoPlayNextEpisode
        preferencesManager.setAutoPlayNextEpisode(newVal)
        _uiState.value = _uiState.value.copy(autoPlayNextEpisode = newVal)
    }

    fun toggleAutoSkipIntro() {
        val newVal = !_uiState.value.autoSkipIntro
        preferencesManager.setAutoSkipIntro(newVal)
        _uiState.value = _uiState.value.copy(autoSkipIntro = newVal)
    }

    fun toggleAutoSkipCredits() {
        val newVal = !_uiState.value.autoSkipCredits
        preferencesManager.setAutoSkipCredits(newVal)
        _uiState.value = _uiState.value.copy(autoSkipCredits = newVal)
    }

    fun toggleSubtitlesByDefault() {
        val newVal = !_uiState.value.subtitlesByDefault
        preferencesManager.setSubtitlesByDefault(newVal)
        _uiState.value = _uiState.value.copy(subtitlesByDefault = newVal)
    }

    fun toggleAutoDeleteDownloads() {
        val newVal = !_uiState.value.autoDeleteWatchedDownloads
        preferencesManager.setAutoDeleteWatchedDownloads(newVal)
        _uiState.value = _uiState.value.copy(autoDeleteWatchedDownloads = newVal)
    }

    fun toggleWifiOnly() {
        val newVal = !_uiState.value.wifiOnly
        preferencesManager.setWifiOnly(newVal)
        _uiState.value = _uiState.value.copy(wifiOnly = newVal)
    }

    fun toggleBackgroundPlayback() {
        val newVal = !_uiState.value.backgroundPlayback
        preferencesManager.setBackgroundPlayback(newVal)
        _uiState.value = _uiState.value.copy(backgroundPlayback = newVal)
    }

    fun toggleHardwareAcceleration() {
        val newVal = !_uiState.value.hardwareAcceleration
        preferencesManager.setHardwareAcceleration(newVal)
        _uiState.value = _uiState.value.copy(hardwareAcceleration = newVal)
    }

    fun setPreferredSubtitleLanguage(lang: String) {
        preferencesManager.setPreferredSubtitleLanguage(lang)
        _uiState.value = _uiState.value.copy(
            preferredSubtitleLanguage = lang,
            showSubtitleLanguageDialog = false
        )
    }

    fun setPreferredAudioLanguage(lang: String) {
        preferencesManager.setPreferredAudioLanguage(lang)
        _uiState.value = _uiState.value.copy(
            preferredAudioLanguage = lang,
            showAudioLanguageDialog = false
        )
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        preferencesManager.setDefaultPlaybackSpeed(speed)
        _uiState.value = _uiState.value.copy(
            defaultPlaybackSpeed = speed,
            showPlaybackSpeedDialog = false
        )
    }

    fun setDoubleTapSeekDuration(duration: Int) {
        preferencesManager.setDoubleTapSeekDuration(duration)
        _uiState.value = _uiState.value.copy(
            doubleTapSeekDuration = duration,
            showSeekDurationDialog = false
        )
    }

    fun setDownloadQuality(quality: String) {
        preferencesManager.setDownloadQuality(quality)
        _uiState.value = _uiState.value.copy(
            downloadQuality = quality,
            showDownloadQualityDialog = false
        )
    }

    fun setShowSubtitleLanguageDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSubtitleLanguageDialog = show)
    }

    fun setShowAudioLanguageDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAudioLanguageDialog = show)
    }

    fun setShowPlaybackSpeedDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showPlaybackSpeedDialog = show)
    }

    fun setShowSeekDurationDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSeekDurationDialog = show)
    }

    fun setShowClearSearchHistoryDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showClearSearchHistoryDialog = show)
    }

    fun setShowClearWatchHistoryDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showClearWatchHistoryDialog = show)
    }

    fun setShowDownloadQualityDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showDownloadQualityDialog = show)
    }

    fun clearImageCache() {
        preferencesManager.clearCache()
        refreshStorageMetrics()
        _uiState.value = _uiState.value.copy(
            userToastMessage = "Cache cleaned successfully"
        )
    }

    fun confirmClearSearchHistory() {
        repository.clearSearchHistory()
        _uiState.value = _uiState.value.copy(
            showClearSearchHistoryDialog = false,
            userToastMessage = "Search history cleared"
        )
    }

    fun confirmClearWatchHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _uiState.value = _uiState.value.copy(
                showClearWatchHistoryDialog = false,
                userToastMessage = "Watch history cleared"
            )
        }
    }

    fun checkForUpdates(currentVersion: String = "1.0.0") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCheckingForUpdates = true)
            val result = updateManager.checkForUpdates(currentVersion)
            _uiState.value = _uiState.value.copy(
                isCheckingForUpdates = false,
                updateInfo = result,
                showUpdateDialog = result.hasUpdate,
                showUpToDateDialog = !result.hasUpdate && !result.isError,
                userToastMessage = if (result.isError) result.errorMessage else null
            )
        }
    }

    fun dismissUpdateDialog() {
        _uiState.value = _uiState.value.copy(
            showUpdateDialog = false,
            showUpToDateDialog = false
        )
    }

    fun clearToastMessage() {
        _uiState.value = _uiState.value.copy(userToastMessage = null)
    }
}
