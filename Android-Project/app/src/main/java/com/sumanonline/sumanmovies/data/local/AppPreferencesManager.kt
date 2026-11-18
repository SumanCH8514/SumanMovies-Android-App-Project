package com.sumanonline.sumanmovies.data.local

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import com.sumanonline.sumanmovies.ui.screens.customization.AppearanceMode
import com.sumanonline.sumanmovies.ui.screens.customization.HomeTabsStyle
import com.sumanonline.sumanmovies.ui.screens.customization.PlayerUiStyle
import com.sumanonline.sumanmovies.ui.theme.AppAccent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale

class AppPreferencesManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sumanmovies_app_prefs", Context.MODE_PRIVATE)

    private val _userName = MutableStateFlow(
        prefs.getString(KEY_USER_NAME, "Suman User") ?: "Suman User"
    )
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _appearanceMode = MutableStateFlow(
        try {
            AppearanceMode.valueOf(
                prefs.getString(KEY_APPEARANCE_MODE, AppearanceMode.DARK.name) ?: AppearanceMode.DARK.name
            )
        } catch (_: Exception) {
            AppearanceMode.DARK
        }
    )
    val appearanceMode: StateFlow<AppearanceMode> = _appearanceMode.asStateFlow()

    private val _selectedAccent = MutableStateFlow(
        try {
            AppAccent.valueOf(
                prefs.getString(KEY_SELECTED_ACCENT, AppAccent.SUNSET_GOLD.name) ?: AppAccent.SUNSET_GOLD.name
            )
        } catch (_: Exception) {
            AppAccent.SUNSET_GOLD
        }
    )
    val selectedAccent: StateFlow<AppAccent> = _selectedAccent.asStateFlow()

    private val _homeTabsStyle = MutableStateFlow(
        try {
            HomeTabsStyle.valueOf(
                prefs.getString(KEY_HOME_TABS_STYLE, HomeTabsStyle.TOP_GLASS.name) ?: HomeTabsStyle.TOP_GLASS.name
            )
        } catch (_: Exception) {
            HomeTabsStyle.TOP_GLASS
        }
    )
    val homeTabsStyle: StateFlow<HomeTabsStyle> = _homeTabsStyle.asStateFlow()

    private val _playerUiStyle = MutableStateFlow(
        try {
            PlayerUiStyle.valueOf(
                prefs.getString(KEY_PLAYER_UI_STYLE, PlayerUiStyle.MODERN.name) ?: PlayerUiStyle.MODERN.name
            )
        } catch (_: Exception) {
            PlayerUiStyle.MODERN
        }
    )
    val playerUiStyle: StateFlow<PlayerUiStyle> = _playerUiStyle.asStateFlow()

    private val _applyPaletteToPlayer = MutableStateFlow(
        prefs.getBoolean(KEY_APPLY_PALETTE_TO_PLAYER, true)
    )
    val applyPaletteToPlayer: StateFlow<Boolean> = _applyPaletteToPlayer.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(
        prefs.getString(KEY_SELECTED_LANGUAGE, "en-US") ?: "en-US"
    )
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _autoPlayNextEpisode = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_PLAY_NEXT, true)
    )
    val autoPlayNextEpisode: StateFlow<Boolean> = _autoPlayNextEpisode.asStateFlow()

    private val _preferredSubtitleLanguage = MutableStateFlow(
        prefs.getString(KEY_PREFERRED_SUBTITLE_LANG, "English") ?: "English"
    )
    val preferredSubtitleLanguage: StateFlow<String> = _preferredSubtitleLanguage.asStateFlow()

    private val _preferredAudioLanguage = MutableStateFlow(
        prefs.getString(KEY_PREFERRED_AUDIO_LANG, "English") ?: "English"
    )
    val preferredAudioLanguage: StateFlow<String> = _preferredAudioLanguage.asStateFlow()

    private val _subtitlesByDefault = MutableStateFlow(
        prefs.getBoolean(KEY_SUBTITLES_BY_DEFAULT, true)
    )
    val subtitlesByDefault: StateFlow<Boolean> = _subtitlesByDefault.asStateFlow()

    private val _defaultPlaybackSpeed = MutableStateFlow(
        prefs.getFloat(KEY_DEFAULT_PLAYBACK_SPEED, 1.0f)
    )
    val defaultPlaybackSpeed: StateFlow<Float> = _defaultPlaybackSpeed.asStateFlow()

    private val _doubleTapSeekDuration = MutableStateFlow(
        prefs.getInt(KEY_DOUBLE_TAP_SEEK_DURATION, 10)
    )
    val doubleTapSeekDuration: StateFlow<Int> = _doubleTapSeekDuration.asStateFlow()

    private val _backgroundPlayback = MutableStateFlow(
        prefs.getBoolean(KEY_BACKGROUND_PLAYBACK, false)
    )
    val backgroundPlayback: StateFlow<Boolean> = _backgroundPlayback.asStateFlow()

    private val _hardwareAcceleration = MutableStateFlow(
        prefs.getBoolean(KEY_HARDWARE_ACCELERATION, true)
    )
    val hardwareAcceleration: StateFlow<Boolean> = _hardwareAcceleration.asStateFlow()

    private val _downloadQuality = MutableStateFlow(
        prefs.getString(KEY_DOWNLOAD_QUALITY, "Auto") ?: "Auto"
    )
    val downloadQuality: StateFlow<String> = _downloadQuality.asStateFlow()

    private val _autoDeleteWatchedDownloads = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_DELETE_WATCHED, false)
    )
    val autoDeleteWatchedDownloads: StateFlow<Boolean> = _autoDeleteWatchedDownloads.asStateFlow()

    private val _wifiOnly = MutableStateFlow(
        prefs.getBoolean(KEY_WIFI_ONLY, false)
    )
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    init {
        if (!prefs.getBoolean(KEY_DEFAULT_PREFS_INITIALIZED, false)) {
            prefs.edit()
                .putString(KEY_APPEARANCE_MODE, AppearanceMode.DARK.name)
                .putString(KEY_HOME_TABS_STYLE, HomeTabsStyle.TOP_GLASS.name)
                .putString(KEY_PLAYER_UI_STYLE, PlayerUiStyle.MODERN.name)
                .putString(KEY_SELECTED_ACCENT, AppAccent.SUNSET_GOLD.name)
                .putBoolean(KEY_DEFAULT_PREFS_INITIALIZED, true)
                .apply()
            _appearanceMode.value = AppearanceMode.DARK
            _homeTabsStyle.value = HomeTabsStyle.TOP_GLASS
            _playerUiStyle.value = PlayerUiStyle.MODERN
            _selectedAccent.value = AppAccent.SUNSET_GOLD
        }
    }

    fun setUserName(name: String) {
        val clean = name.trim().ifBlank { "Suman User" }
        prefs.edit().putString(KEY_USER_NAME, clean).apply()
        _userName.value = clean
    }

    fun setAppearanceMode(mode: AppearanceMode) {
        prefs.edit().putString(KEY_APPEARANCE_MODE, mode.name).apply()
        _appearanceMode.value = mode
    }

    fun setSelectedAccent(accent: AppAccent) {
        prefs.edit().putString(KEY_SELECTED_ACCENT, accent.name).apply()
        _selectedAccent.value = accent
    }

    fun setHomeTabsStyle(style: HomeTabsStyle) {
        prefs.edit().putString(KEY_HOME_TABS_STYLE, style.name).apply()
        _homeTabsStyle.value = style
    }

    fun setPlayerUiStyle(style: PlayerUiStyle) {
        prefs.edit().putString(KEY_PLAYER_UI_STYLE, style.name).apply()
        _playerUiStyle.value = style
    }

    fun setApplyPaletteToPlayer(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APPLY_PALETTE_TO_PLAYER, enabled).apply()
        _applyPaletteToPlayer.value = enabled
    }

    fun setSelectedLanguage(lang: String) {
        prefs.edit().putString(KEY_SELECTED_LANGUAGE, lang).apply()
        _selectedLanguage.value = lang
    }

    fun setAutoPlayNextEpisode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PLAY_NEXT, enabled).apply()
        _autoPlayNextEpisode.value = enabled
    }

    fun setPreferredSubtitleLanguage(lang: String) {
        prefs.edit().putString(KEY_PREFERRED_SUBTITLE_LANG, lang).apply()
        _preferredSubtitleLanguage.value = lang
    }

    fun setPreferredAudioLanguage(lang: String) {
        prefs.edit().putString(KEY_PREFERRED_AUDIO_LANG, lang).apply()
        _preferredAudioLanguage.value = lang
    }

    fun setSubtitlesByDefault(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SUBTITLES_BY_DEFAULT, enabled).apply()
        _subtitlesByDefault.value = enabled
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_DEFAULT_PLAYBACK_SPEED, speed).apply()
        _defaultPlaybackSpeed.value = speed
    }

    fun setDoubleTapSeekDuration(duration: Int) {
        prefs.edit().putInt(KEY_DOUBLE_TAP_SEEK_DURATION, duration).apply()
        _doubleTapSeekDuration.value = duration
    }

    fun setBackgroundPlayback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKGROUND_PLAYBACK, enabled).apply()
        _backgroundPlayback.value = enabled
    }

    fun setHardwareAcceleration(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HARDWARE_ACCELERATION, enabled).apply()
        _hardwareAcceleration.value = enabled
    }

    fun setDownloadQuality(quality: String) {
        prefs.edit().putString(KEY_DOWNLOAD_QUALITY, quality).apply()
        _downloadQuality.value = quality
    }

    fun setAutoDeleteWatchedDownloads(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_DELETE_WATCHED, enabled).apply()
        _autoDeleteWatchedDownloads.value = enabled
    }

    fun setWifiOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
        _wifiOnly.value = enabled
    }

    fun calculateCacheSizeBytes(): Long {
        var size = 0L
        try {
            size += getFolderSize(context.cacheDir)
            val extCache = context.externalCacheDir
            if (extCache != null && extCache.exists()) {
                size += getFolderSize(extCache)
            }
        } catch (_: Exception) {}
        return size
    }

    fun calculateDownloadStorageBytes(): Long {
        var size = 0L
        try {
            val pubMovies = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "SumanMovies"
            )
            if (pubMovies.exists()) {
                size += getFolderSize(pubMovies)
            }
            val appMovies = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            if (appMovies != null && appMovies.exists()) {
                size += getFolderSize(appMovies)
            }
        } catch (_: Exception) {}
        return size
    }

    fun clearCache(): Boolean {
        return try {
            deleteFolderContents(context.cacheDir)
            val extCache = context.externalCacheDir
            if (extCache != null && extCache.exists()) {
                deleteFolderContents(extCache)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    private fun getFolderSize(folder: File?): Long {
        if (folder == null || !folder.exists()) return 0L
        var length = 0L
        val files = folder.listFiles() ?: return 0L
        for (file in files) {
            length += if (file.isDirectory) getFolderSize(file) else file.length()
        }
        return length
    }

    private fun deleteFolderContents(folder: File?): Boolean {
        if (folder == null || !folder.exists()) return true
        val files = folder.listFiles() ?: return true
        var success = true
        for (file in files) {
            if (file.isDirectory) {
                success = success && deleteFolderContents(file)
            }
            success = success && file.delete()
        }
        return success
    }

    companion object {
        private const val KEY_USER_NAME = "key_user_name"
        private const val KEY_APPEARANCE_MODE = "key_appearance_mode"
        private const val KEY_SELECTED_ACCENT = "key_selected_accent"
        private const val KEY_HOME_TABS_STYLE = "key_home_tabs_style"
        private const val KEY_PLAYER_UI_STYLE = "key_player_ui_style"
        private const val KEY_APPLY_PALETTE_TO_PLAYER = "key_apply_palette_to_player"
        private const val KEY_SELECTED_LANGUAGE = "key_selected_language"
        private const val KEY_AUTO_PLAY_NEXT = "key_auto_play_next"
        private const val KEY_PREFERRED_SUBTITLE_LANG = "key_preferred_subtitle_lang"
        private const val KEY_PREFERRED_AUDIO_LANG = "key_preferred_audio_lang"
        private const val KEY_SUBTITLES_BY_DEFAULT = "key_subtitles_by_default"
        private const val KEY_DEFAULT_PLAYBACK_SPEED = "key_default_playback_speed"
        private const val KEY_DOUBLE_TAP_SEEK_DURATION = "key_double_tap_seek_duration"
        private const val KEY_BACKGROUND_PLAYBACK = "key_background_playback"
        private const val KEY_HARDWARE_ACCELERATION = "key_hardware_acceleration"
        private const val KEY_DOWNLOAD_QUALITY = "key_download_quality"
        private const val KEY_AUTO_DELETE_WATCHED = "key_auto_delete_watched"
        private const val KEY_WIFI_ONLY = "key_wifi_only"
        private const val KEY_DEFAULT_PREFS_INITIALIZED = "key_default_prefs_initialized_v2"
    }
}
