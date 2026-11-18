package com.sumanonline.sumanmovies.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material.icons.filled.Web
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sumanonline.sumanmovies.ui.theme.BgDark
import com.sumanonline.sumanmovies.ui.theme.CardElevated
import com.sumanonline.sumanmovies.ui.theme.LocalAppAccent
import com.sumanonline.sumanmovies.ui.theme.TextMuted
import com.sumanonline.sumanmovies.ui.theme.TextPrimary
import com.sumanonline.sumanmovies.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val accent = LocalAppAccent.current.color
    val context = LocalContext.current

    LaunchedEffect(state.userToastMessage) {
        state.userToastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToastMessage()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CardElevated)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = "Settings",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            item {
                SettingsSectionLabel("PLAYBACK")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardElevated)
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                ) {
                    SettingsSwitchItem(
                        icon = Icons.Default.PlayCircle,
                        title = "Auto-play next episode",
                        subtitle = "Automatically start the next TV episode when playback finishes.",
                        checked = state.autoPlayNextEpisode,
                        accentColor = accent,
                        onCheckedChange = { viewModel.toggleAutoPlay() }
                    )
                    SettingsDivider()
                    SettingsValueItem(
                        icon = Icons.Default.Subtitles,
                        title = "Preferred Subtitle Language",
                        subtitle = "Preferred language track when available.",
                        value = state.preferredSubtitleLanguage,
                        onClick = { viewModel.setShowSubtitleLanguageDialog(true) }
                    )
                    SettingsDivider()
                    SettingsValueItem(
                        icon = Icons.Default.Audiotrack,
                        title = "Preferred Audio Language",
                        subtitle = "Default audio language for movies & series.",
                        value = state.preferredAudioLanguage,
                        onClick = { viewModel.setShowAudioLanguageDialog(true) }
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = Icons.Default.ClosedCaption,
                        title = "Subtitles by default",
                        subtitle = "Always enable subtitles automatically on video start.",
                        checked = state.subtitlesByDefault,
                        accentColor = accent,
                        onCheckedChange = { viewModel.toggleSubtitlesByDefault() }
                    )
                    SettingsDivider()
                    SettingsValueItem(
                        icon = Icons.Default.Speed,
                        title = "Default Playback Speed",
                        subtitle = "Standard speed applied when starting playback.",
                        value = "${state.defaultPlaybackSpeed}x",
                        onClick = { viewModel.setShowPlaybackSpeedDialog(true) }
                    )
                    SettingsDivider()
                    SettingsValueItem(
                        icon = Icons.Default.FastForward,
                        title = "Double-Tap Seek Duration",
                        subtitle = "Seek jump time on double-tapping player sides.",
                        value = "${state.doubleTapSeekDuration}s",
                        onClick = { viewModel.setShowSeekDurationDialog(true) }
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = Icons.Default.PictureInPictureAlt,
                        title = "Background Playback",
                        subtitle = "Continue audio playback when screen is locked or app minimized.",
                        checked = state.backgroundPlayback,
                        accentColor = accent,
                        onCheckedChange = { viewModel.toggleBackgroundPlayback() }
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = Icons.Default.VideoSettings,
                        title = "Hardware Acceleration",
                        subtitle = "Use hardware GPU decoding for smoother video and battery savings.",
                        checked = state.hardwareAcceleration,
                        accentColor = accent,
                        onCheckedChange = { viewModel.toggleHardwareAcceleration() }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                SettingsSectionLabel("STORAGE & DOWNLOADS")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardElevated)
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                ) {
                    SettingsValueItem(
                        icon = Icons.Default.Speed,
                        title = "Default Download Quality",
                        subtitle = "Target quality format for one-click downloading.",
                        value = state.downloadQuality,
                        onClick = { viewModel.setShowDownloadQualityDialog(true) }
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = Icons.Default.Delete,
                        title = "Auto-delete finished downloads",
                        subtitle = "Remove media files from storage once marked completed.",
                        checked = state.autoDeleteWatchedDownloads,
                        accentColor = accent,
                        onCheckedChange = { viewModel.toggleAutoDeleteDownloads() }
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = Icons.Default.Wifi,
                        title = "Download over Wi-Fi only",
                        subtitle = "Prevent downloads over cellular data connections.",
                        checked = state.wifiOnly,
                        accentColor = accent,
                        onCheckedChange = { viewModel.toggleWifiOnly() }
                    )
                    SettingsDivider()
                    SettingsValueItem(
                        icon = Icons.Default.Folder,
                        title = "Download folder",
                        subtitle = "Storage directory on device.",
                        value = state.downloadsFolder,
                        onClick = { }
                    )
                    SettingsDivider()
                    SettingsValueItem(
                        icon = Icons.Default.Storage,
                        title = "Downloads storage",
                        subtitle = "Total disk space occupied by downloads.",
                        value = state.downloadStorage,
                        onClick = { viewModel.refreshStorageMetrics() }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                SettingsSectionLabel("CACHE & HISTORY")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardElevated)
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                ) {
                    SettingsActionItem(
                        icon = Icons.Default.Storage,
                        title = "Clear Image & Network Cache",
                        subtitle = "Reclaim temporary cache storage (${state.cacheSize}).",
                        color = TextPrimary,
                        onClick = { viewModel.clearImageCache() }
                    )
                    SettingsDivider()
                    SettingsActionItem(
                        icon = Icons.Default.Search,
                        title = "Clear Search History",
                        subtitle = "Remove your saved search queries from this device.",
                        color = Color(0xFFEF4444),
                        onClick = { viewModel.setShowClearSearchHistoryDialog(true) }
                    )
                    SettingsDivider()
                    SettingsActionItem(
                        icon = Icons.Default.History,
                        title = "Clear Watch History",
                        subtitle = "Reset all progress and watch records.",
                        color = Color(0xFFEF4444),
                        onClick = { viewModel.setShowClearWatchHistoryDialog(true) }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                SettingsSectionLabel("UPDATES & ABOUT")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardElevated)
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!state.isCheckingForUpdates) {
                                    viewModel.checkForUpdates("1.0.0")
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Check for Updates", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (state.isCheckingForUpdates) "Checking GitHub for new releases..." else "Version 1.0.0 • Tap to check GitHub releases",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        if (state.isCheckingForUpdates) {
                            CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                    SettingsDivider()
                    SettingsActionItem(
                        icon = Icons.Default.Web,
                        title = "Visit Website",
                        subtitle = "v1.cinema.sumanonline.com • Official Web Portal",
                        color = TextPrimary,
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://v1.cinema.sumanonline.com/")
                            )
                            context.startActivity(intent)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SumanMovies • 1.0.0 - a SumanOnline Project",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }

    if (state.showSubtitleLanguageDialog) {
        val languages = listOf("English", "Hindi", "Spanish", "French", "Arabic", "German", "Japanese", "None")
        AlertDialog(
            onDismissRequest = { viewModel.setShowSubtitleLanguageDialog(false) },
            title = {
                Text(text = "Preferred Subtitle Language", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    languages.forEach { lang ->
                        val isSelected = state.preferredSubtitleLanguage.equals(lang, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) accent.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { viewModel.setPreferredSubtitleLanguage(lang) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = lang, color = if (isSelected) accent else TextPrimary, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            if (isSelected) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setShowSubtitleLanguageDialog(false) }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = Color(0xFF1B1B22)
        )
    }

    if (state.showAudioLanguageDialog) {
        val languages = listOf("English", "Hindi", "Spanish", "French", "Arabic", "German", "Japanese", "Korean", "Chinese", "Tamil", "Telugu", "Bengali")
        AlertDialog(
            onDismissRequest = { viewModel.setShowAudioLanguageDialog(false) },
            title = {
                Text(text = "Preferred Audio Language", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    languages.forEach { lang ->
                        val isSelected = state.preferredAudioLanguage.equals(lang, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) accent.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { viewModel.setPreferredAudioLanguage(lang) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = lang, color = if (isSelected) accent else TextPrimary, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            if (isSelected) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setShowAudioLanguageDialog(false) }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = Color(0xFF1B1B22)
        )
    }

    if (state.showPlaybackSpeedDialog) {
        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        AlertDialog(
            onDismissRequest = { viewModel.setShowPlaybackSpeedDialog(false) },
            title = {
                Text(text = "Default Playback Speed", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    speeds.forEach { sp ->
                        val isSelected = state.defaultPlaybackSpeed == sp
                        val label = if (sp == 1.0f) "1.0x (Normal)" else "${sp}x"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) accent.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { viewModel.setDefaultPlaybackSpeed(sp) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = label, color = if (isSelected) accent else TextPrimary, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            if (isSelected) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setShowPlaybackSpeedDialog(false) }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = Color(0xFF1B1B22)
        )
    }

    if (state.showSeekDurationDialog) {
        val durations = listOf(5, 10, 15, 20, 30)
        AlertDialog(
            onDismissRequest = { viewModel.setShowSeekDurationDialog(false) },
            title = {
                Text(text = "Double-Tap Seek Duration", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    durations.forEach { dur ->
                        val isSelected = state.doubleTapSeekDuration == dur
                        val label = if (dur == 10) "10 seconds (Default)" else "$dur seconds"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) accent.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { viewModel.setDoubleTapSeekDuration(dur) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = label, color = if (isSelected) accent else TextPrimary, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            if (isSelected) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setShowSeekDurationDialog(false) }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = Color(0xFF1B1B22)
        )
    }

    if (state.showDownloadQualityDialog) {
        val qualities = listOf(
            "Auto (Best Available)",
            "4K Ultra HD (2160p)",
            "Full HD (1080p)",
            "Standard HD (720p)",
            "Data Saver (480p)"
        )
        AlertDialog(
            onDismissRequest = { viewModel.setShowDownloadQualityDialog(false) },
            title = {
                Text(text = "Default Download Quality", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    qualities.forEach { q ->
                        val isSelected = state.downloadQuality == q
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) accent.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { viewModel.setDownloadQuality(q) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = q, color = if (isSelected) accent else TextPrimary, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            if (isSelected) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setShowDownloadQualityDialog(false) }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = Color(0xFF1B1B22)
        )
    }

    if (state.showUpdateDialog && state.updateInfo != null) {
        val info = state.updateInfo!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            title = {
                Text(text = "New Update Available! 🎉", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
            },
            text = {
                Column {
                    Text(text = "A new release of SumanMovies is available on GitHub.", color = TextSecondary, fontSize = 13.5.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Latest: v${info.latestVersion}  (Current: v${info.currentVersion})", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    if (info.releaseNotes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = info.releaseNotes.take(300), color = TextMuted, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissUpdateDialog()
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text("Download now", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.dismissUpdateDialog() }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = Color(0xFF1B1B22)
        )
    }

    if (state.showUpToDateDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            title = {
                Text(text = "You're Up to Date", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
            },
            text = {
                Text(text = "SumanMovies v1.0.0 is the latest version available on GitHub.", color = TextSecondary, fontSize = 13.5.sp)
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissUpdateDialog() },
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1B1B22)
        )
    }

    AnimatedConfirmClearDialog(
        visible = state.showClearSearchHistoryDialog,
        title = "Clear Search History?",
        message = "Are you sure you want to remove all search queries? This cannot be undone.",
        confirmButtonText = "Clear Search",
        onConfirm = { viewModel.confirmClearSearchHistory() },
        onDismiss = { viewModel.setShowClearSearchHistoryDialog(false) }
    )

    AnimatedConfirmClearDialog(
        visible = state.showClearWatchHistoryDialog,
        title = "Clear Watch History?",
        message = "Are you sure you want to clear your entire watch history and playback records?",
        confirmButtonText = "Clear History",
        onConfirm = { viewModel.confirmClearWatchHistory() },
        onDismiss = { viewModel.setShowClearWatchHistoryDialog(false) }
    )
}

@Composable
private fun SettingsSectionLabel(title: String) {
    Text(
        text = title,
        color = TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(BgDark)
    )
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    accentColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = TextMuted, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color(0xFFE2E8F0),
                uncheckedTrackColor = Color(0xFF262A36),
                uncheckedBorderColor = Color(0xFF4A5568)
            )
        )
    }
}

@Composable
private fun AnimatedConfirmClearDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmButtonText: String = "Clear Now",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (visible) {
        Dialog(onDismissRequest = onDismiss) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1B1B22))
                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                    .padding(22.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = message,
                        color = TextSecondary,
                        fontSize = 13.5.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                        ) {
                            Text("Cancel", fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                        }

                        Button(
                            onClick = onConfirm,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                        ) {
                            Text(confirmButtonText, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsValueItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, color = TextMuted, fontSize = 11.sp)
            }
        }
        Text(text = value, color = TextMuted, fontSize = 12.sp)
        Spacer(modifier = Modifier.width(4.dp))
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SettingsActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = TextMuted, fontSize = 11.sp)
        }
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
    }
}
