package com.sumanonline.sumanmovies.ui.screens.settings

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                ) {
                    SettingsSwitchItem(
                        icon = Icons.Default.PlayCircle,
                        title = "Auto-play next episode",
                        subtitle = "Automatically start the next TV episode when the countdown finishes.",
                        checked = state.autoPlayNextEpisode,
                        accentColor = accent,
                        onCheckedChange = { viewModel.toggleAutoPlay() }
                    )
                    SettingsDivider()
                    SettingsValueItem(
                        icon = Icons.Default.Subtitles,
                        title = "Preferred subtitle language",
                        value = state.preferredSubtitleLanguage,
                        onClick = {}
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = Icons.Default.ClosedCaption,
                        title = "Subtitles by default",
                        subtitle = "Show subtitles by default when they are enabled for playback.",
                        checked = state.subtitlesByDefault,
                        accentColor = accent,
                        onCheckedChange = { viewModel.toggleSubtitlesByDefault() }
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            item {
                SettingsSectionLabel("DOWNLOADS")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardElevated)
                ) {
                    SettingsValueItem(
                        icon = Icons.Default.Speed,
                        title = "Download quality",
                        value = state.downloadQuality,
                        onClick = {}
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = Icons.Default.Delete,
                        title = "Auto-delete watched downloads",
                        subtitle = "Remove an offline download after you watch at least 90% of it.",
                        checked = state.autoDeleteWatchedDownloads,
                        accentColor = accent,
                        onCheckedChange = { viewModel.toggleAutoDeleteDownloads() }
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = Icons.Default.Wifi,
                        title = "Wi-Fi only",
                        subtitle = "Only prepare downloads when connected to Wi-Fi or Ethernet.",
                        checked = state.wifiOnly,
                        accentColor = accent,
                        onCheckedChange = { viewModel.toggleWifiOnly() }
                    )
                    SettingsDivider()
                    SettingsValueItem(
                        icon = Icons.Default.Folder,
                        title = "Downloads folder",
                        subtitle = "Name of the folder inside Download where finished do...",
                        value = state.downloadsFolder,
                        onClick = {}
                    )
                    SettingsDivider()
                    SettingsValueItem(
                        icon = Icons.Default.Storage,
                        title = "Download storage",
                        value = state.downloadStorage,
                        onClick = {}
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            item {
                SettingsSectionLabel("STORAGE")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardElevated)
                ) {
                    SettingsValueItem(
                        icon = Icons.Default.Storage,
                        title = "Cache size",
                        value = state.cacheSize,
                        onClick = {}
                    )
                    SettingsDivider()
                    SettingsActionItem(
                        icon = Icons.Default.Delete,
                        title = "Clear Image Cache",
                        subtitle = "Free up space used by cached artwork",
                        color = Color(0xFFEF4444),
                        onClick = { viewModel.clearImageCache() }
                    )
                    SettingsDivider()
                    SettingsActionItem(
                        icon = Icons.Default.Search,
                        title = "Clear search history",
                        subtitle = "Remove your saved search queries from this device.",
                        color = Color(0xFFEF4444),
                        onClick = { viewModel.clearSearchHistory() }
                    )
                    SettingsDivider()
                    SettingsActionItem(
                        icon = Icons.Default.History,
                        title = "Clear History",
                        subtitle = "Reset all progress and watch records.",
                        color = Color(0xFFEF4444),
                        onClick = { viewModel.clearWatchHistory() }
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
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
        )
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
