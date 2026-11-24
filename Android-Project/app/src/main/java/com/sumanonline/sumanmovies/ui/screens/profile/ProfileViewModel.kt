package com.sumanonline.sumanmovies.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumanonline.sumanmovies.data.local.AppPreferencesManager
import com.sumanonline.sumanmovies.data.remote.update.AppUpdateInfo
import com.sumanonline.sumanmovies.data.remote.update.AppUpdateManager
import com.sumanonline.sumanmovies.data.repository.MediaRepository
import com.sumanonline.sumanmovies.ui.screens.customization.AppearanceMode
import com.sumanonline.sumanmovies.ui.theme.AppAccent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class ProfileUiState(
    val name: String = "SumanMovies User",
    val avatarLetter: String = "S",
    val statusBadge: String = "Local Account • Change it",
    val bookmarksCount: Int = 0,
    val historyCount: Int = 0,
    val downloadsCount: Int = 0,
    val appearanceMode: AppearanceMode = AppearanceMode.DARK,
    val selectedAccent: AppAccent = AppAccent.SUNSET_GOLD,
    val isCheckingForUpdates: Boolean = false,
    val updateInfo: AppUpdateInfo? = null,
    val showUpdateDialog: Boolean = false,
    val showUpToDateDialog: Boolean = false,
    val showEditNameDialog: Boolean = false,
    val userToastMessage: String? = null
)

class ProfileViewModel(
    private val repository: MediaRepository,
    private val preferencesManager: AppPreferencesManager,
    private val updateManager: AppUpdateManager = AppUpdateManager()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ProfileUiState(
            name = preferencesManager.userName.value,
            avatarLetter = preferencesManager.userName.value.firstOrNull()?.uppercase() ?: "S",
            appearanceMode = preferencesManager.appearanceMode.value,
            selectedAccent = preferencesManager.selectedAccent.value
        )
    )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.userName.collectLatest { name ->
                val letter = name.trim().firstOrNull()?.uppercase() ?: "S"
                _uiState.value = _uiState.value.copy(name = name, avatarLetter = letter)
            }
        }
        viewModelScope.launch {
            preferencesManager.appearanceMode.collectLatest { mode ->
                _uiState.value = _uiState.value.copy(appearanceMode = mode)
            }
        }
        viewModelScope.launch {
            preferencesManager.selectedAccent.collectLatest { accent ->
                _uiState.value = _uiState.value.copy(selectedAccent = accent)
            }
        }
        viewModelScope.launch {
            repository.getFavoritesFlow().collectLatest { list ->
                _uiState.value = _uiState.value.copy(bookmarksCount = list.size)
            }
        }
        viewModelScope.launch {
            repository.getAllHistoryFlow().collectLatest { list ->
                _uiState.value = _uiState.value.copy(historyCount = list.size)
            }
        }
        viewModelScope.launch {
            repository.getDownloadsFlow().collectLatest { list ->
                _uiState.value = _uiState.value.copy(downloadsCount = list.size)
            }
        }
    }

    fun setUserName(name: String) {
        preferencesManager.setUserName(name)
        _uiState.value = _uiState.value.copy(showEditNameDialog = false)
    }

    fun setShowEditNameDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showEditNameDialog = show)
    }

    fun checkForUpdates(currentVersion: String = com.sumanonline.sumanmovies.BuildConfig.VERSION_NAME) {
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
