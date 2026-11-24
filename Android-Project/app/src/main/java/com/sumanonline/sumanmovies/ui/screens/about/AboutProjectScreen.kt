package com.sumanonline.sumanmovies.ui.screens.about

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sumanonline.sumanmovies.BuildConfig
import com.sumanonline.sumanmovies.ui.theme.BgDark
import com.sumanonline.sumanmovies.ui.theme.BorderDark
import com.sumanonline.sumanmovies.ui.theme.CardElevated
import com.sumanonline.sumanmovies.ui.theme.LocalAppAccent
import com.sumanonline.sumanmovies.ui.theme.TextMuted
import com.sumanonline.sumanmovies.ui.theme.TextPrimary
import com.sumanonline.sumanmovies.ui.theme.TextSecondary

@Composable
fun AboutProjectScreen(
    onBack: () -> Unit,
    onCheckForUpdates: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val accent = LocalAppAccent.current.color
    val context = LocalContext.current

    var showChangelogDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showDisclaimerDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "About Project",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Hero Branding Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    accent.copy(alpha = 0.22f),
                                    CardElevated
                                )
                            )
                        )
                        .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(accent, accent.copy(alpha = 0.7f))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "S",
                                color = Color.White,
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "SumanMovies",
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(accent.copy(alpha = 0.18f))
                                    .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME}",
                                    color = accent,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Build ${BuildConfig.VERSION_CODE}",
                                    color = TextMuted,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (BuildConfig.DEBUG) Color(0xFFEF5350).copy(alpha = 0.2f)
                                        else Color(0xFF66BB6A).copy(alpha = 0.2f)
                                    )
                                    .border(
                                        1.dp,
                                        if (BuildConfig.DEBUG) Color(0xFFEF5350).copy(alpha = 0.5f)
                                        else Color(0xFF66BB6A).copy(alpha = 0.5f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (BuildConfig.DEBUG) "Debug" else "Production",
                                    color = if (BuildConfig.DEBUG) Color(0xFFEF5350) else Color(0xFF66BB6A),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "A SumanOnline Project",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Developed by SumanOnline.Com",
                            color = TextMuted,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(18.dp)) }

            // About Description
            item {
                AboutSectionHeader(title = "OVERVIEW")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardElevated)
                        .border(1.dp, BorderDark, RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "SumanMovies is a modern, high-performance Android media streaming & offline download application. " +
                                "It unifies multi-provider streaming catalogs, hardware-accelerated Media3 playback, multi-track audio and subtitle selection, " +
                                "and robust offline media management into a seamless Jetpack Compose interface.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(18.dp)) }

            // Specifications Card
            item {
                AboutSectionHeader(title = "APPLICATION DETAILS")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardElevated)
                        .border(1.dp, BorderDark, RoundedCornerShape(14.dp))
                ) {
                    AboutDetailRow(label = "Package Name", value = BuildConfig.APPLICATION_ID)
                    AboutDivider()
                    AboutDetailRow(label = "Version Name", value = BuildConfig.VERSION_NAME)
                    AboutDivider()
                    AboutDetailRow(label = "Version Code", value = "${BuildConfig.VERSION_CODE}")
                    AboutDivider()
                    AboutDetailRow(label = "Release Channel", value = if (BuildConfig.DEBUG) "Development" else "Stable Release")
                    AboutDivider()
                    AboutDetailRow(label = "Target SDK", value = "Android 15 (API 35)")
                    AboutDivider()
                    AboutDetailRow(label = "Min SDK", value = "Android 8.0 (API 26)")
                    AboutDivider()
                    AboutDetailRow(label = "Attribution", value = "A SumanOnline Project")
                }
            }

            item { Spacer(modifier = Modifier.height(18.dp)) }

            // Features & Architecture
            item {
                AboutSectionHeader(title = "KEY ARCHITECTURE & HIGHLIGHTS")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardElevated)
                        .border(1.dp, BorderDark, RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ArchitectureHighlightItem(
                        title = "Multi-Provider Aggregation",
                        desc = "Play4U, MovieBox, KMDL, MoviesLeech, AnimeXin, MoviesMod, ReAnime, and Live TV IPTV channels with resilient fallback."
                    )
                    ArchitectureHighlightItem(
                        title = "Media3 ExoPlayer Engine",
                        desc = "Hardware decoding, dynamic caching, multi-track audio switching, styled subtitle rendering, and seek thumbnail previews."
                    )
                    ArchitectureHighlightItem(
                        title = "HLS Remuxing Downloader",
                        desc = "Foreground segmented HLS stream downloader with MP4 remuxing, retry backoff, and notification progress."
                    )
                    ArchitectureHighlightItem(
                        title = "Offline Persistence (Room v5)",
                        desc = "Structured Room database storing watch history, playback checkpoints, bookmarks, and custom color preferences."
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(18.dp)) }

            // Project Links & Community
            item {
                AboutSectionHeader(title = "RESOURCES & COMMUNITY")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardElevated)
                        .border(1.dp, BorderDark, RoundedCornerShape(14.dp))
                ) {
                    AboutActionRow(
                        icon = Icons.Default.Code,
                        title = "GitHub Repository",
                        subtitle = "Source code, releases, and issue tracker",
                        accentColor = accent,
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/SumanCH8514/SumanMovies-Android-App-Project")
                            )
                            context.startActivity(intent)
                        }
                    )
                    AboutDivider()
                    AboutActionRow(
                        icon = Icons.Default.Web,
                        title = "Official Web Companion",
                        subtitle = "v1.cinema.sumanonline.com",
                        accentColor = accent,
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://v1.cinema.sumanonline.com/")
                            )
                            context.startActivity(intent)
                        }
                    )
                    AboutDivider()
                    AboutActionRow(
                        icon = Icons.Default.NewReleases,
                        title = "What's New in v${BuildConfig.VERSION_NAME}",
                        subtitle = "View changes, new providers, and fixes",
                        accentColor = accent,
                        onClick = { showChangelogDialog = true }
                    )
                    AboutDivider()
                    AboutActionRow(
                        icon = Icons.Default.Description,
                        title = "Open Source Licenses",
                        subtitle = "Third-party libraries and licenses",
                        accentColor = accent,
                        onClick = { showLicensesDialog = true }
                    )
                    AboutDivider()
                    AboutActionRow(
                        icon = Icons.Default.Policy,
                        title = "Legal Disclaimer",
                        subtitle = "Terms of usage and media indexing policy",
                        accentColor = accent,
                        onClick = { showDisclaimerDialog = true }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(28.dp)) }

            // Footer
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "© 2026 SumanOnline.Com • All Rights Reserved",
                            color = TextMuted,
                            fontSize = 11.5.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "A SumanOnline Project",
                            color = accent.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // What's New Dialog
    if (showChangelogDialog) {
        AlertDialog(
            onDismissRequest = { showChangelogDialog = false },
            title = {
                Text(
                    text = "What's New in v${BuildConfig.VERSION_NAME}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextPrimary
                )
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            text = "Major Highlights:",
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    item {
                        Text(
                            text = "• Live TV & IPTV Integration: Stream curated live television channels directly within the application.",
                            color = TextSecondary,
                            fontSize = 12.5.sp
                        )
                    }
                    item {
                        Text(
                            text = "• New Anime Providers: AnimeXin & ReAnime dedicated catalogs with full multi-source resolution.",
                            color = TextSecondary,
                            fontSize = 12.5.sp
                        )
                    }
                    item {
                        Text(
                            text = "• In-Player Stream Drawer: Modern drawer for instant server switching, quality selection, and stream downloads.",
                            color = TextSecondary,
                            fontSize = 12.5.sp
                        )
                    }
                    item {
                        Text(
                            text = "• HLS Downloader & Remuxer: Multi-threaded background segment fetching and MP4 container remuxing.",
                            color = TextSecondary,
                            fontSize = 12.5.sp
                        )
                    }
                    item {
                        Text(
                            text = "• MovieBox Full Stream Fix: Solved 15-minute preview limitation and improved multi-audio and dub selection.",
                            color = TextSecondary,
                            fontSize = 12.5.sp
                        )
                    }
                    item {
                        Text(
                            text = "• Room Database v5: Seamless migration preserving all watch checkpoints and thumbnail caching.",
                            color = TextSecondary,
                            fontSize = 12.5.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showChangelogDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = CardElevated,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    // Licenses Dialog
    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = {
                Text(
                    text = "Open Source Licenses",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextPrimary
                )
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            text = "SumanMovies utilizes the following open-source libraries:",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    item {
                        LicenseItem(name = "Jetpack Compose & Material 3", license = "Apache License 2.0")
                        LicenseItem(name = "AndroidX Media3 (ExoPlayer)", license = "Apache License 2.0")
                        LicenseItem(name = "AndroidX Room & KSP", license = "Apache License 2.0")
                        LicenseItem(name = "Square OkHttp & Retrofit", license = "Apache License 2.0")
                        LicenseItem(name = "Coil Compose (Image Loading)", license = "Apache License 2.0")
                        LicenseItem(name = "Kotlinx Coroutines", license = "Apache License 2.0")
                        LicenseItem(name = "Google Gson", license = "Apache License 2.0")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showLicensesDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = CardElevated,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    // Disclaimer Dialog
    if (showDisclaimerDialog) {
        AlertDialog(
            onDismissRequest = { showDisclaimerDialog = false },
            title = {
                Text(
                    text = "Legal Disclaimer",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "SumanMovies is an indexing, aggregation, and player client for publicly available web media streams. " +
                            "SumanMovies does not host, upload, or control any video or media content on external servers. " +
                            "All trademarks, content titles, and metadata belong to their respective copyright holders. " +
                            "For DMCA inquiries or content concerns, please contact the respective hosting providers.",
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showDisclaimerDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text("Understood", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = CardElevated,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }
}

@Composable
private fun AboutSectionHeader(title: String) {
    Text(
        text = title,
        color = TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun AboutDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextMuted, fontSize = 12.5.sp)
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AboutActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 11.sp
            )
        }
        Icon(
            imageVector = Icons.Default.OpenInNew,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun AboutDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(BorderDark)
    )
}

@Composable
private fun ArchitectureHighlightItem(title: String, desc: String) {
    Column {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = desc,
            color = TextSecondary,
            fontSize = 11.5.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun LicenseItem(name: String, license: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = name, color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        Text(text = license, color = TextMuted, fontSize = 11.sp)
    }
}
