package com.sumanonline.sumanmovies.ui.screens.customization

import androidx.lifecycle.ViewModel
import com.sumanonline.sumanmovies.ui.theme.AppAccent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppearanceMode {
    DEVICE,
    DARK,
    LIGHT
}

enum class HomeTabsStyle {
    SEGMENTED,
    TOP_GLASS
}

data class CustomizationUiState(
    val appearanceMode: AppearanceMode = AppearanceMode.DARK,
    val homeTabsStyle: HomeTabsStyle = HomeTabsStyle.TOP_GLASS,
    val selectedAccent: AppAccent = AppAccent.ROYAL_INDIGO,
    val applyPaletteToPlayer: Boolean = true,
    val selectedLanguage: String = "en-US"
)

class CustomizationViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CustomizationUiState())
    val uiState: StateFlow<CustomizationUiState> = _uiState.asStateFlow()

    fun setAppearanceMode(mode: AppearanceMode) {
        _uiState.value = _uiState.value.copy(appearanceMode = mode)
    }

    fun setHomeTabsStyle(style: HomeTabsStyle) {
        _uiState.value = _uiState.value.copy(homeTabsStyle = style)
    }

    fun setAccent(accent: AppAccent) {
        _uiState.value = _uiState.value.copy(selectedAccent = accent)
    }

    fun toggleApplyPaletteToPlayer() {
        _uiState.value = _uiState.value.copy(applyPaletteToPlayer = !_uiState.value.applyPaletteToPlayer)
    }

    fun setLanguage(lang: String) {
        _uiState.value = _uiState.value.copy(selectedLanguage = lang)
    }
}
