package com.sumanonline.sumanmovies.ui.screens.providers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sumanonline.sumanmovies.ui.theme.BgDark
import com.sumanonline.sumanmovies.ui.theme.BorderDark
import com.sumanonline.sumanmovies.ui.theme.CardDark
import com.sumanonline.sumanmovies.ui.theme.CardElevated
import com.sumanonline.sumanmovies.ui.theme.LocalAppAccent
import com.sumanonline.sumanmovies.ui.theme.TextMuted
import com.sumanonline.sumanmovies.ui.theme.TextPrimary
import com.sumanonline.sumanmovies.ui.theme.TextSecondary

private data class ProviderInfo(
    val id: String,
    val name: String,
    val badge: String,
    val status: String,
    val description: String,
    val icon: ImageVector,
    val tags: List<String>,
    val actionLabel: String,
    val onAction: () -> Unit
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StreamingProvidersScreen(
    onBack: () -> Unit,
    onNavigateToPlay4U: () -> Unit,
    onNavigateToMovieBox: () -> Unit,
    onNavigateToLiveTv: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToKMDL: (() -> Unit)? = null,
    onNavigateToMoviesLeech: (() -> Unit)? = null,
    onNavigateToAnimeXin: (() -> Unit)? = null,
    onNavigateToMoviesMod: (() -> Unit)? = null,
    onNavigateToReAnime: (() -> Unit)? = null,
    onNavigateToCategory: ((String, String) -> Unit)? = null
) {
    val accent = LocalAppAccent.current.color
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val providers = remember(accent) {
        listOf(
            ProviderInfo(
                id = "play4u",
                name = "Play4U",
                badge = "1080p HLS",
                status = "Online • Ultra Fast",
                description = "Direct lossless 1080p HLS streams with multi-audio language tracks and instant playback start.",
                icon = Icons.Default.PlayArrow,
                tags = listOf("1080p HLS", "Multi-Audio", "Lossless", "Zero Buffer"),
                actionLabel = "Explore Play4U",
                onAction = onNavigateToPlay4U
            ),
            ProviderInfo(
                id = "moviebox",
                name = "MovieBox",
                badge = "1080p DASH",
                status = "Online • Default Server",
                description = "Massive global library with direct DASH streams, English & localized dubbing, and high bitrate.",
                icon = Icons.Default.Movie,
                tags = listOf("DASH Stream", "Global Catalog", "High Bitrate", "English & Dub"),
                actionLabel = "Explore MovieBox",
                onAction = onNavigateToMovieBox
            ),
            ProviderInfo(
                id = "kmdl",
                name = "KMDL",
                badge = "Cloud MKV",
                status = "Online • Lossless Cloud",
                description = "Direct Google Drive high-speed cloud index with full drama, movie & series catalogs in lossless MKV.",
                icon = Icons.Default.Cloud,
                tags = listOf("Cloud Drive", "Lossless MKV", "Direct Stream", "High Speed"),
                actionLabel = "Explore KMDL",
                onAction = { onNavigateToKMDL?.invoke() ?: onNavigateToPlay4U() }
            ),
            ProviderInfo(
                id = "moviesleech",
                name = "MoviesLeech",
                badge = "1080p / 4K",
                status = "Online • Fast CDN",
                description = "Specialized hub for Bollywood, South Indian dubbed cinema, web series & new theatrical releases.",
                icon = Icons.Default.CloudDownload,
                tags = listOf("Bollywood", "South Dubbed", "1080p / 4K", "Direct CDN"),
                actionLabel = "Explore MoviesLeech",
                onAction = { onNavigateToMoviesLeech?.invoke() ?: onNavigateToPlay4U() }
            ),
            ProviderInfo(
                id = "animexin",
                name = "AnimeXin",
                badge = "Donghua & Anime",
                status = "Online • Mediafire & Mirror",
                description = "Dedicated provider for Donghua, Chinese 3D anime, and animated series with Mediafire and mirror downloads.",
                icon = Icons.Default.AutoAwesome,
                tags = listOf("Donghua", "Anime", "Mediafire", "GoFile", "VikingFile"),
                actionLabel = "Explore AnimeXin",
                onAction = { onNavigateToAnimeXin?.invoke() ?: onNavigateToPlay4U() }
            ),
            ProviderInfo(
                id = "moviesmod",
                name = "MoviesMod",
                badge = "Dual Audio",
                status = "Online • High Bitrate",
                description = "Extensive collection of dual-audio movies, web series, anime series & multi-language releases.",
                icon = Icons.Default.VideoLibrary,
                tags = listOf("Dual Audio", "Web Series", "Anime Series", "Direct Links"),
                actionLabel = "Explore MoviesMod",
                onAction = { onNavigateToMoviesMod?.invoke() ?: onNavigateToPlay4U() }
            ),
            ProviderInfo(
                id = "hdhub4u",
                name = "HDHub4u",
                badge = "1080p / 4K",
                status = "Online • High Speed",
                description = "Specialized high-speed source for Bollywood, Hollywood, South Hindi Dubbed cinema & trending series.",
                icon = Icons.Default.CloudDownload,
                tags = listOf("Bollywood", "South Dubbed", "1080p / 4K", "Direct CDN"),
                actionLabel = "Browse Indian Cinema",
                onAction = { onNavigateToCategory?.invoke("indian_cinema", "HDHub4u - Bollywood & Dual Audio") ?: onNavigateToPlay4U() }
            ),
            ProviderInfo(
                id = "fourkhdhub",
                name = "4KHDHub",
                badge = "4K Ultra HD",
                status = "Online • Ultra HD",
                description = "Ultra high-definition 4K & 1080p cinema collection with premium visual bitrate and surround audio.",
                icon = Icons.Default.HighQuality,
                tags = listOf("4K UHD", "2160p", "High Bitrate", "Dual Audio"),
                actionLabel = "Browse 4K Collection",
                onAction = { onNavigateToCategory?.invoke("top_rated", "4KHDHub - Ultra HD Cinema") ?: onNavigateToPlay4U() }
            ),
            ProviderInfo(
                id = "dramachi",
                name = "Dramachi",
                badge = "Dramas & Series",
                status = "Online • Complete Series",
                description = "Dedicated Asian entertainment hub featuring popular K-dramas, C-dramas, J-dramas & global series.",
                icon = Icons.Default.Subtitles,
                tags = listOf("K-Drama", "C-Drama", "Multi-Language Subs", "Full Seasons"),
                actionLabel = "Browse Dramas",
                onAction = { onNavigateToCategory?.invoke("k_drama", "Dramachi - Asian Dramas") ?: onNavigateToPlay4U() }
            ),
            ProviderInfo(
                id = "reanime",
                name = "ReAnime",
                badge = "Anime HD",
                status = "Online • Anime Dedicated",
                description = "Dedicated anime streaming server with complete continuous episodes, Japanese subs & English dubs.",
                icon = Icons.Default.AutoAwesome,
                tags = listOf("Anime Series", "Continuous Episodes", "Japanese Sub", "English Dub"),
                actionLabel = "Browse Anime",
                onAction = { onNavigateToReAnime?.invoke() ?: onNavigateToPlay4U() }
            ),
            ProviderInfo(
                id = "iptv",
                name = "IpTV",
                badge = "500+ Channels",
                status = "Live • 24/7 Broadcast",
                description = "Over 500+ HD Live TV channels spanning Sports, News, Entertainment, Movies & Regional television.",
                icon = Icons.Default.LiveTv,
                tags = listOf("500+ Channels", "Live Sports", "News & Shows", "HD Streams"),
                actionLabel = "Watch IpTV",
                onAction = onNavigateToLiveTv
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(topInset))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = "Streaming Providers",
                        color = TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "High-speed streaming & live TV hubs",
                        color = TextMuted,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.15f))
                        .border(BorderStroke(1.dp, accent.copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "${providers.size} Providers",
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = (bottomInset + 24.dp).coerceAtLeast(32.dp)
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(providers, key = { it.id }) { provider ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardElevated)
                            .border(BorderStroke(1.dp, BorderDark), RoundedCornerShape(16.dp))
                            .clickable { provider.onAction() }
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(CardDark)
                                            .border(BorderStroke(1.dp, accent.copy(alpha = 0.28f)), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = provider.icon,
                                            contentDescription = provider.name,
                                            tint = accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = provider.name,
                                            color = TextPrimary,
                                            fontSize = 16.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF22C55E))
                                            )
                                            Text(
                                                text = provider.status,
                                                color = Color(0xFF22C55E),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(accent.copy(alpha = 0.15f))
                                        .border(BorderStroke(1.dp, accent.copy(alpha = 0.38f)), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = provider.badge,
                                        color = accent,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = provider.description,
                                color = TextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                provider.tags.forEach { tag ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(CardDark)
                                            .border(BorderStroke(1.dp, BorderDark), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = tag,
                                            color = TextMuted,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(accent.copy(alpha = 0.14f))
                                    .border(BorderStroke(1.dp, accent.copy(alpha = 0.35f)), RoundedCornerShape(10.dp))
                                    .clickable { provider.onAction() }
                                    .padding(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = provider.actionLabel,
                                    color = accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
