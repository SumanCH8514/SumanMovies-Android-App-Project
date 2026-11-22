package com.sumanonline.sumanmovies.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sumanonline.sumanmovies.data.models.StreamMirror
import com.sumanonline.sumanmovies.data.models.StreamSource
import com.sumanonline.sumanmovies.data.models.StreamingServer
import com.sumanonline.sumanmovies.data.models.getDisplayTitle

data class QualityCategoryMeta(
    val id: String,
    val title: String,
    val subtitle: String,
    val defaultBadge: String,
    val badgeTextColor: Color,
    val badgeBgColor: Color
)

fun getQualityBucket(stream: StreamSource): String {
    val q = stream.quality.lowercase()
    val c = stream.category.lowercase()
    val t = (stream.title ?: stream.label).lowercase()
    return when {
        q.contains("2160") || q.contains("4k") || c.contains("2160") || c.contains("4k") || t.contains("2160") || t.contains("4k") -> "4K"
        q.contains("1080") || q.contains("fhd") || c.contains("1080") || t.contains("1080") || t.contains("fhd") -> "1080p"
        q.contains("720") || q.contains("hd") || c.contains("720") || t.contains("720") -> "720p"
        q.contains("360") || q.contains("480") || q.contains("sd") || c.contains("360") || c.contains("480") || t.contains("360") || t.contains("480") -> "360p"
        else -> "1080p"
    }
}

@Composable
fun DownloadFullScreenModal(
    server: StreamingServer,
    pageTitle: String,
    streams: List<StreamSource>,
    isLoading: Boolean,
    isAnime: Boolean = false,
    resolvingStreamUrl: String? = null,
    onSelectServer: (StreamingServer) -> Unit,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onDownloadStream: (StreamSource, StreamMirror?) -> Unit
) {
    val standardCategories = remember {
        listOf(
            QualityCategoryMeta("4K", "4K", "Ultra HD (2160p)", "HDR10 / DV", Color(0xFFF59E0B), Color(0xFF78350F).copy(alpha = 0.5f)),
            QualityCategoryMeta("1080p", "1080p", "Full HD", "60FPS BluRay", Color(0xFF10B981), Color(0xFF064E3B).copy(alpha = 0.5f)),
            QualityCategoryMeta("720p", "720p", "HD Standard", "WEB-DL", Color(0xFF3B82F6), Color(0xFF1E3A8A).copy(alpha = 0.5f)),
            QualityCategoryMeta("360p", "360p", "Data Saver", "HEVC x265", Color(0xFFA855F7), Color(0xFF4C1D95).copy(alpha = 0.5f))
        )
    }

    val groupedStreams = remember(streams) {
        streams
            .filterNot { s ->
                val title = s.title?.trim() ?: ""
                val url = s.url.trim().lowercase()
                ((title.equals("4K Movies", ignoreCase = true) || title.equals("4K Movie", ignoreCase = true)) &&
                    (url.contains("4khdhub") || s.provider.equals("hdhub4u", ignoreCase = true))) ||
                    (url.contains("4khdhub.one") && (title.equals("4K Movies", ignoreCase = true) || url.trimEnd('/') == "https://4khdhub.one"))
            }
            .groupBy { getQualityBucket(it) }
    }

    val expandedCategories = remember(server, streams) {
        mutableStateMapOf<String, Boolean>().apply {
            val defaultCategory = when {
                groupedStreams.containsKey("720p") && groupedStreams["720p"]?.isNotEmpty() == true -> "720p"
                groupedStreams.containsKey("1080p") && groupedStreams["1080p"]?.isNotEmpty() == true -> "1080p"
                else -> standardCategories.firstOrNull {
                    groupedStreams.containsKey(it.id) && groupedStreams[it.id]?.isNotEmpty() == true
                }?.id
            }
            if (defaultCategory != null) {
                put(defaultCategory, true)
            }
        }
    }

    val downloadProviders = remember(isAnime) {
        if (isAnime) {
            listOf(
                StreamingServer.FOURKHDHUB,
                StreamingServer.HDHUB4U,
                StreamingServer.PLAY4U,
                StreamingServer.MOVIEBOX,
                StreamingServer.REANIME
            )
        } else {
            listOf(
                StreamingServer.FOURKHDHUB,
                StreamingServer.HDHUB4U,
                StreamingServer.PLAY4U,
                StreamingServer.MOVIEBOX
            )
        }
    }

    val activeProviderLabel = when (server) {
        StreamingServer.FOURKHDHUB -> "4KHub4U"
        else -> server.displayName
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF08080A)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color(0xFFE50914)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Suman",
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Movies",
                            color = Color(0xFFE50914),
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = pageTitle,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SELECT PROVIDER",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$activeProviderLabel Active",
                            color = Color(0xFF22C55E),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(downloadProviders) { srv ->
                        val isSelected = server == srv
                        val label = when (srv) {
                            StreamingServer.MOVIEBOX -> "MovieBox"
                            StreamingServer.PLAY4U -> "Play4U"
                            StreamingServer.HDHUB4U -> "HDHub4U"
                            StreamingServer.FOURKHDHUB -> "4KHub4U"
                            StreamingServer.DRAMACHI -> "Dramachi"
                            StreamingServer.REANIME -> "ReAnime"
                            StreamingServer.KMDL -> "KMDL"
                            StreamingServer.MOVIESLEECH -> "MoviesLeech"
                            StreamingServer.MOVIESMOD -> "MoviesMod"
                            StreamingServer.ANIMEXIN -> "AnimeXin"
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) Color(0xFFE50914) else Color(0xFF16161D))
                                .clickable { onSelectServer(srv) }
                                .padding(horizontal = 20.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Quality Categories",
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "Tap to expand / close",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFE50914),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Fetching all download options from $activeProviderLabel...",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Categorizing 4K, 1080p, 720p, 360p download links",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }
                } else if (streams.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No download options found on $activeProviderLabel.",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRefresh,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Retry Live Fetch", color = Color.White, fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val activeCategories = standardCategories.filter {
                            groupedStreams[it.id]?.isNotEmpty() == true
                        }.ifEmpty {
                            groupedStreams.keys.map { key ->
                                QualityCategoryMeta(
                                    id = key,
                                    title = key,
                                    subtitle = "Available Options",
                                    defaultBadge = "Direct",
                                    badgeTextColor = Color(0xFF10B981),
                                    badgeBgColor = Color(0xFF064E3B).copy(alpha = 0.5f)
                                )
                            }
                        }

                        items(activeCategories, key = { it.id }) { cat ->
                            val categoryItems = groupedStreams[cat.id] ?: emptyList()
                            val isExpanded = expandedCategories[cat.id] == true
                            val rotation by animateFloatAsState(
                                targetValue = if (isExpanded) 180f else 0f,
                                label = "rotation"
                            )

                            val rawBadge = categoryItems.firstOrNull()?.sourceQualityTag
                                ?: cat.defaultBadge
                            val cleanBadge = when {
                                rawBadge.contains("IMAX", ignoreCase = true) -> "IMAX"
                                rawBadge.contains("BluRay", ignoreCase = true) -> "BluRay"
                                rawBadge.contains("HDR", ignoreCase = true) -> "HDR10 / DV"
                                rawBadge.contains("WEB-DL", ignoreCase = true) -> "WEB-DL"
                                else -> rawBadge
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF121217))
                                    .border(1.dp, Color(0xFF1F1F27), RoundedCornerShape(16.dp))
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                expandedCategories[cat.id] = !isExpanded
                                            }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f, fill = false)
                                        ) {
                                            Text(
                                                text = cat.title,
                                                color = Color.White,
                                                fontSize = 16.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = cat.subtitle,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 12.5.sp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(cat.badgeBgColor)
                                                    .border(
                                                        0.5.dp,
                                                        cat.badgeTextColor.copy(alpha = 0.4f),
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = cleanBadge,
                                                    color = cat.badgeTextColor,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color.White.copy(alpha = 0.06f))
                                                    .padding(horizontal = 9.dp, vertical = 3.5.dp)
                                            ) {
                                                val count = categoryItems.size
                                                Text(
                                                    text = "$count ${if (count == 1) "Link" else "Links"}",
                                                    color = Color(0xFFCBD5E1),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White.copy(alpha = 0.06f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                                    tint = Color.White,
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .rotate(rotation)
                                                )
                                            }
                                        }
                                    }

                                    AnimatedVisibility(
                                        visible = isExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp)
                                                .padding(bottom = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            val sortedCategoryItems = categoryItems.sortedWith(
                                                compareByDescending<StreamSource> {
                                                    if (it.directFile || it.format.equals("MP4", ignoreCase = true)) 1 else 0
                                                }
                                            )
                                            sortedCategoryItems.forEach { stream ->
                                                DownloadOptionCardV2(
                                                    stream = stream,
                                                    resolvingStreamUrl = resolvingStreamUrl,
                                                    onDownload = { mirror -> onDownloadStream(stream, mirror) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadOptionCardV2(
    stream: StreamSource,
    resolvingStreamUrl: String?,
    onDownload: (StreamMirror?) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val displayTitle = stream.getDisplayTitle()
    val isResolvingThisStream = resolvingStreamUrl != null &&
            (resolvingStreamUrl == stream.url || stream.mirrors.any { it.url == resolvingStreamUrl })

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF16161E))
            .border(1.dp, Color(0xFF1F1F27), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = displayTitle,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(7.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF10B981))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stream.quality.ifBlank { "HD" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp
                    )
                }

                if (!stream.sizeFormatted.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stream.sizeFormatted ?: "",
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))
                val formatCodec = "${stream.format}${stream.codec?.let { " • $it" } ?: ""}"
                Text(
                    text = "•  $formatCodec",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp
                )
            }

            if (isResolvingThisStream) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = Color(0xFFE50914),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = "Unpacking direct CDN download link...",
                        color = Color(0xFFE50914),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (stream.mirrors.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    stream.mirrors.forEach { mirror ->
                        val isMirrorResolving = resolvingStreamUrl == mirror.url
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = mirror.name,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { onDownload(mirror) },
                                    enabled = !isMirrorResolving,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFE50914),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    if (isMirrorResolving) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 1.5.dp,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.FileDownload,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Download",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .clickable {
                                            clipboardManager.setText(AnnotatedString(mirror.url))
                                            Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share Link",
                                        tint = Color(0xFFCBD5E1),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        val fileDesc = when {
                            stream.directFile || stream.format.equals("MP4", ignoreCase = true) -> "Direct MP4 File"
                            stream.format.equals("DASH", ignoreCase = true) -> "DASH Media Stream"
                            stream.format.equals("HLS", ignoreCase = true) -> "HLS Media Stream"
                            else -> "Direct Server File"
                        }
                        Text(
                            text = fileDesc,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.5.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { onDownload(null) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE50914),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Download",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(stream.url))
                                    Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Link",
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
