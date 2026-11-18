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

enum class PlayerUiStyle {
    MODERN,
    CLASSIC
}

data class CustomizationUiState(
    val appearanceMode: AppearanceMode = AppearanceMode.DARK,
    val homeTabsStyle: HomeTabsStyle = HomeTabsStyle.TOP_GLASS,
    val selectedAccent: AppAccent = AppAccent.SUNSET_GOLD,
    val applyPaletteToPlayer: Boolean = true,
    val selectedLanguage: String = "en-US",
    val playerUiStyle: PlayerUiStyle = PlayerUiStyle.MODERN
)

class CustomizationViewModel(
    private val preferencesManager: AppPreferencesManager
) : ViewModel() {

    val uiState: StateFlow<CustomizationUiState> = combine(
        combine(
            preferencesManager.appearanceMode,
            preferencesManager.homeTabsStyle,
            preferencesManager.selectedAccent
        ) { mode, style, accent -> Triple(mode, style, accent) },
        combine(
            preferencesManager.applyPaletteToPlayer,
            preferencesManager.selectedLanguage,
            preferencesManager.playerUiStyle
        ) { playerPalette, lang, playerStyle -> Triple(playerPalette, lang, playerStyle) }
    ) { (mode, style, accent), (playerPalette, lang, playerStyle) ->
        CustomizationUiState(
            appearanceMode = mode,
            homeTabsStyle = style,
            selectedAccent = accent,
            applyPaletteToPlayer = playerPalette,
            selectedLanguage = lang,
            playerUiStyle = playerStyle
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CustomizationUiState(
            appearanceMode = preferencesManager.appearanceMode.value,
            homeTabsStyle = preferencesManager.homeTabsStyle.value,
            selectedAccent = preferencesManager.selectedAccent.value,
            applyPaletteToPlayer = preferencesManager.applyPaletteToPlayer.value,
            selectedLanguage = preferencesManager.selectedLanguage.value,
            playerUiStyle = preferencesManager.playerUiStyle.value
        )
    )

    fun setAppearanceMode(mode: AppearanceMode) {
        preferencesManager.setAppearanceMode(mode)
    }

    fun setHomeTabsStyle(style: HomeTabsStyle) {
        preferencesManager.setHomeTabsStyle(style)
    }

    fun setPlayerUiStyle(style: PlayerUiStyle) {
        preferencesManager.setPlayerUiStyle(style)
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
