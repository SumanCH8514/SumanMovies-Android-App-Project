package com.sumanonline.sumanmovies.ui.screens.customization

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Web
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sumanonline.sumanmovies.ui.theme.AppAccent
import com.sumanonline.sumanmovies.ui.theme.BgDark
import com.sumanonline.sumanmovies.ui.theme.CardElevated
import com.sumanonline.sumanmovies.ui.theme.LocalAppAccent
import com.sumanonline.sumanmovies.ui.theme.TextMuted
import com.sumanonline.sumanmovies.ui.theme.TextPrimary
import com.sumanonline.sumanmovies.ui.theme.TextSecondary

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme

@Composable
fun CustomizationScreen(
    viewModel: CustomizationViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val accent = LocalAppAccent.current.color

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = "App Customization",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            item {
                SectionLabel(title = "APPEARANCE MODE")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            RoundedCornerShape(14.dp)
                        )
                ) {
                    RadioOptionItem(
                        icon = Icons.Default.PhoneAndroid,
                        title = "Device",
                        subtitle = "Follow your device theme",
                        isSelected = state.appearanceMode == AppearanceMode.DEVICE,
                        accentColor = accent,
                        onClick = { viewModel.setAppearanceMode(AppearanceMode.DEVICE) }
                    )
                    DividerLine()
                    RadioOptionItem(
                        icon = Icons.Default.DarkMode,
                        title = "Dark",
                        subtitle = "Optimized for low light",
                        isSelected = state.appearanceMode == AppearanceMode.DARK,
                        accentColor = accent,
                        onClick = { viewModel.setAppearanceMode(AppearanceMode.DARK) }
                    )
                    DividerLine()
                    RadioOptionItem(
                        icon = Icons.Default.LightMode,
                        title = "Light",
                        subtitle = "Clean bright interface",
                        isSelected = state.appearanceMode == AppearanceMode.LIGHT,
                        accentColor = accent,
                        onClick = { viewModel.setAppearanceMode(AppearanceMode.LIGHT) }
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            item {
                SectionLabel(title = "HOME CATEGORY TABS")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            RoundedCornerShape(14.dp)
                        )
                ) {
                    RadioOptionItem(
                        icon = Icons.Default.ViewCarousel,
                        title = "Segmented control",
                        subtitle = "Pill segmented under the hero (reference look)",
                        isSelected = state.homeTabsStyle == HomeTabsStyle.SEGMENTED,
                        accentColor = accent,
                        onClick = { viewModel.setHomeTabsStyle(HomeTabsStyle.SEGMENTED) }
                    )
                    DividerLine()
                    RadioOptionItem(
                        icon = Icons.Default.Web,
                        title = "Top glass bar",
                        subtitle = "Glassmorphic tabs in the top bar",
                        isSelected = state.homeTabsStyle == HomeTabsStyle.TOP_GLASS,
                        accentColor = accent,
                        onClick = { viewModel.setHomeTabsStyle(HomeTabsStyle.TOP_GLASS) }
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            item {
                SectionLabel(title = "MEDIA PLAYER UI")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            RoundedCornerShape(14.dp)
                        )
                ) {
                    RadioOptionItem(
                        icon = Icons.Default.SmartDisplay,
                        title = "Modern (OTT Cinematic)",
                        subtitle = "Clean distraction-free interface with quick audio/subtitle badges",
                        isSelected = state.playerUiStyle == PlayerUiStyle.MODERN,
                        accentColor = accent,
                        onClick = { viewModel.setPlayerUiStyle(PlayerUiStyle.MODERN) }
                    )
                    DividerLine()
                    RadioOptionItem(
                        icon = Icons.Default.VideoSettings,
                        title = "Classic",
                        subtitle = "Full drawer & modal sheet player interface",
                        isSelected = state.playerUiStyle == PlayerUiStyle.CLASSIC,
                        accentColor = accent,
                        onClick = { viewModel.setPlayerUiStyle(PlayerUiStyle.CLASSIC) }
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            item {
                SectionLabel(title = "ACCENT COLORS")
                Text(
                    text = "Select your preferred highlight color across the app",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                )

                val accents = AppAccent.values()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    accents.toList().chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            row.forEach { acc ->
                                AccentColorCard(
                                    accent = acc,
                                    isSelected = state.selectedAccent == acc,
                                    onClick = { viewModel.setAccent(acc) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Apply palette to player",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Color the player controls with your accent palette. Turn off for the classic white player.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = state.applyPaletteToPlayer,
                            onCheckedChange = { viewModel.toggleApplyPaletteToPlayer() },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accent)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            item {
                SectionLabel(title = "APP & METADATA LANGUAGE")
                Text(
                    text = "Content metadata, titles, and genres will refresh in selected language.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            RoundedCornerShape(14.dp)
                        )
                ) {
                    listOf(
                        Pair("en-US", "English (TMDB: en-US)"),
                        Pair("fr-FR", "Français (TMDB: fr-FR)"),
                        Pair("es-ES", "Español (TMDB: es-ES)"),
                        Pair("ar-SA", "العربية (TMDB: ar-SA)")
                    ).forEachIndexed { index, (code, label) ->
                        RadioOptionItem(
                            icon = Icons.Default.Language,
                            title = label.substringBefore(" ("),
                            subtitle = label.substringAfter(" (").removeSuffix(")"),
                            isSelected = state.selectedLanguage == code,
                            accentColor = accent,
                            onClick = { viewModel.setLanguage(code) }
                        )
                        if (index < 3) DividerLine()
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✦ All preferences saved automatically to local storage",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    )
}

@Composable
private fun RadioOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun AccentColorCard(
    accent: AppAccent,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                if (isSelected) BorderStroke(2.dp, accent.color) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(accent.color)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = accent.displayName,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = accent.color,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
