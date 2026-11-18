package com.sumanonline.sumanmovies.ui.screens.customization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sumanonline.sumanmovies.data.local.AppPreferencesManager
import com.sumanonline.sumanmovies.ui.theme.AppAccent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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

class CustomizationViewModel(
    private val preferencesManager: AppPreferencesManager
) : ViewModel() {

    val uiState: StateFlow<CustomizationUiState> = combine(
        preferencesManager.appearanceMode,
        preferencesManager.homeTabsStyle,
        preferencesManager.selectedAccent,
        preferencesManager.applyPaletteToPlayer,
        preferencesManager.selectedLanguage
    ) { mode, style, accent, playerPalette, lang ->
        CustomizationUiState(
            appearanceMode = mode,
            homeTabsStyle = style,
            selectedAccent = accent,
            applyPaletteToPlayer = playerPalette,
            selectedLanguage = lang
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CustomizationUiState(
            appearanceMode = preferencesManager.appearanceMode.value,
            homeTabsStyle = preferencesManager.homeTabsStyle.value,
            selectedAccent = preferencesManager.selectedAccent.value,
            applyPaletteToPlayer = preferencesManager.applyPaletteToPlayer.value,
            selectedLanguage = preferencesManager.selectedLanguage.value
        )
    )

    fun setAppearanceMode(mode: AppearanceMode) {
        preferencesManager.setAppearanceMode(mode)
    }

    fun setHomeTabsStyle(style: HomeTabsStyle) {
        preferencesManager.setHomeTabsStyle(style)
    }

    fun setAccent(accent: AppAccent) {
        preferencesManager.setSelectedAccent(accent)
    }

    fun toggleApplyPaletteToPlayer() {
        preferencesManager.setApplyPaletteToPlayer(!preferencesManager.applyPaletteToPlayer.value)
    }

    fun setLanguage(lang: String) {
        preferencesManager.setSelectedLanguage(lang)
    }
}
