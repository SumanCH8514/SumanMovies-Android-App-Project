package com.sumanonline.sumanmovies.ui.screens.library

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.data.models.MediaType
import com.sumanonline.sumanmovies.data.models.StreamSource
import com.sumanonline.sumanmovies.ui.components.SectionHeader
import com.sumanonline.sumanmovies.ui.components.VerticalPosterCard
import com.sumanonline.sumanmovies.ui.theme.BgDark
import com.sumanonline.sumanmovies.ui.theme.CardDark
import com.sumanonline.sumanmovies.ui.theme.CardElevated
import com.sumanonline.sumanmovies.ui.theme.LocalAppAccent
import com.sumanonline.sumanmovies.ui.theme.TextMuted
import com.sumanonline.sumanmovies.ui.theme.TextPrimary
import com.sumanonline.sumanmovies.ui.theme.TextSecondary

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onMediaClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    onPlayDownload: (MediaItem, StreamSource) -> Unit = { _, _ -> }
) {
    val state by viewModel.uiState.collectAsState()
    val accent = LocalAppAccent.current.color
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        if (state.selectedTab == LibraryTab.DOWNLOADS) {
            if (state.downloads.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LibraryHeader(
                        selectedTab = state.selectedTab,
                        onSelectTab = { viewModel.selectTab(it) }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(CardElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "No downloads yet",
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Downloaded movies and episodes will be available offline here.",
                                color = TextMuted,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        LibraryHeader(
                            selectedTab = state.selectedTab,
                            onSelectTab = { viewModel.selectTab(it) }
                        )
                    }
                    items(state.downloads, key = { it.id }) { dl ->
                        val liveProgress = state.progressMap[dl.id]
                        val downloadedBytes = liveProgress?.downloadedBytes?.takeIf { it > 0L } ?: dl.downloadedBytes
                        val totalBytes = liveProgress?.totalBytes?.takeIf { it > 0L } ?: dl.fileSizeBytes
                        val speedBytes = liveProgress?.speedBytesPerSec ?: 0L
                        val liveStatus = liveProgress?.status ?: dl.status

                        val percent = if (totalBytes > 0L) {
                            ((downloadedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }
                        val progressFloat = if (totalBytes > 0L) {
                            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(CardDark)
                                .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                                .clickable {
                                    val playUrl = dl.localFilePath ?: dl.streamUrl
                                    val item = MediaItem(
                                        id = dl.mediaId,
                                        title = dl.title,
                                        posterUrl = dl.posterUrl,
                                        mediaType = try { MediaType.valueOf(dl.mediaType) } catch (_: Exception) { MediaType.MOVIE }
                                    )
                                    val stream = StreamSource(
                                        quality = dl.quality,
                                        format = if (playUrl.contains(".mkv", ignoreCase = true)) "MKV" else "MP4",
                                        url = playUrl,
                                        label = dl.quality,
                                        provider = "offline"
                                    )
                                    onPlayDownload(item, stream)
                                }
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(64.dp)
                                        .height(92.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(CardElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!dl.posterUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = dl.posterUrl,
                                            contentDescription = dl.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.65f))
                                            .clickable {
                                                val playUrl = dl.localFilePath ?: dl.streamUrl
                                                val item = MediaItem(
                                                    id = dl.mediaId,
                                                    title = dl.title,
                                                    posterUrl = dl.posterUrl,
                                                    mediaType = try { MediaType.valueOf(dl.mediaType) } catch (_: Exception) { MediaType.MOVIE }
                                                )
                                                val stream = StreamSource(
                                                    quality = dl.quality,
                                                    format = if (playUrl.contains(".mkv", ignoreCase = true)) "MKV" else "MP4",
                                                    url = playUrl,
                                                    label = dl.quality,
                                                    provider = "offline"
                                                )
                                                onPlayDownload(item, stream)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = dl.title,
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .basicMarquee(
                                                iterations = Int.MAX_VALUE,
                                                initialDelayMillis = 1000
                                            )
                                    )

                                    Spacer(modifier = Modifier.height(5.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        QualityBadge(quality = dl.quality)

                                        Spacer(modifier = Modifier.width(8.dp))

                                        when (liveStatus) {
                                            "COMPLETED" -> {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Ready",
                                                        tint = Color(0xFF10B981),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Ready to watch",
                                                        color = Color(0xFF10B981),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                            "PAUSED" -> {
                                                Text(
                                                    text = "PAUSED",
                                                    color = Color(0xFFF59E0B),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            "FAILED" -> {
                                                Text(
                                                    text = "FAILED",
                                                    color = Color(0xFFEF4444),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            "FETCHING_PLAYLIST" -> {
                                                Text(
                                                    text = "PARSING PLAYLIST",
                                                    color = accent,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            "MERGING" -> {
                                                Text(
                                                    text = "FINALIZING MP4",
                                                    color = Color(0xFF10B981),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            else -> {
                                                Text(
                                                    text = "DOWNLOADING",
                                                    color = accent,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                                if (speedBytes > 0L) {
                                                    Text(
                                                        text = " • ${formatSpeed(speedBytes)}",
                                                        color = TextMuted,
                                                        fontSize = 12.sp,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    val failureReason = liveProgress?.failureReason ?: dl.failureReason

                                    Text(
                                        text = when (liveStatus) {
                                            "COMPLETED" -> {
                                                val sizeStr = if (totalBytes > 0L) formatBytes(totalBytes) else if (downloadedBytes > 0L) formatBytes(downloadedBytes) else ""
                                                if (sizeStr.isNotBlank()) "$sizeStr • Offline video" else "Offline video"
                                            }
                                            "FAILED" -> {
                                                if (!failureReason.isNullOrBlank()) {
                                                    "Failed: $failureReason"
                                                } else {
                                                    "Download failed • Tap Resume to retry"
                                                }
                                            }
                                            "FETCHING_PLAYLIST" -> "Resolving stream variants and manifest..."
                                            "MERGING" -> "Remuxing segments into single playable MP4 file..."
                                            else -> {
                                                val segSuffix = if (liveProgress != null && liveProgress.totalSegments > 0) {
                                                    " • ${liveProgress.downloadedSegments}/${liveProgress.totalSegments} segs"
                                                } else ""
                                                if (totalBytes > 0L) {
                                                    "${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)} ($percent%)$segSuffix"
                                                } else if (downloadedBytes > 0L) {
                                                    "${formatBytes(downloadedBytes)} downloaded$segSuffix"
                                                } else {
                                                    if (segSuffix.isNotEmpty()) "Downloading$segSuffix" else "Starting download..."
                                                }
                                            }
                                        },
                                        color = if (liveStatus == "FAILED") Color(0xFFF87171) else TextMuted,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (liveStatus != "COMPLETED") {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        if (liveStatus == "FETCHING_PLAYLIST" || liveStatus == "MERGING") {
                                            LinearProgressIndicator(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp)),
                                                color = if (liveStatus == "MERGING") Color(0xFF10B981) else accent,
                                                trackColor = Color.White.copy(alpha = 0.1f)
                                            )
                                        } else {
                                            LinearProgressIndicator(
                                                progress = { progressFloat },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp)),
                                                color = if (liveStatus == "PAUSED") Color(0xFFF59E0B) else accent,
                                                trackColor = Color.White.copy(alpha = 0.1f)
                                            )
                                        }
                                    }
                                }
                            }

                            val providerName = extractDownloadProvider(dl.headersJson, dl.mediaId, dl.streamUrl)
                            val formatType = extractDownloadFormat(dl.headersJson, dl.streamUrl, dl.localFilePath)

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .padding(end = 8.dp)
                                ) {
                                    ProviderBadge(provider = providerName)
                                    FormatBadge(format = formatType)
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (liveStatus == "DOWNLOADING" || liveStatus == "FETCHING_PLAYLIST" || liveStatus == "MERGING") {
                                        CardActionButton(
                                            icon = Icons.Default.Pause,
                                            label = "Pause",
                                            tint = accent,
                                            onClick = { viewModel.pauseDownload(dl) }
                                        )
                                        CardActionButton(
                                            icon = Icons.Default.Delete,
                                            label = "Delete",
                                            tint = Color(0xFFEF4444),
                                            onClick = { viewModel.deleteDownload(dl) }
                                        )
                                    } else if (liveStatus == "PAUSED" || liveStatus == "FAILED") {
                                        CardActionButton(
                                            icon = Icons.Default.PlayArrow,
                                            label = "Resume",
                                            tint = accent,
                                            onClick = { viewModel.resumeDownload(dl) }
                                        )
                                        CardActionButton(
                                            icon = Icons.Default.Delete,
                                            label = "Delete",
                                            tint = Color(0xFFEF4444),
                                            onClick = { viewModel.deleteDownload(dl) }
                                        )
                                    } else {
                                        CardActionButton(
                                            icon = Icons.Default.PlayArrow,
                                            label = "Play",
                                            tint = accent,
                                            onClick = {
                                                val playUrl = dl.localFilePath ?: dl.streamUrl
                                                val item = MediaItem(
                                                    id = dl.mediaId,
                                                    title = dl.title,
                                                    posterUrl = dl.posterUrl,
                                                    mediaType = try { MediaType.valueOf(dl.mediaType) } catch (_: Exception) { MediaType.MOVIE }
                                                )
                                                val stream = StreamSource(
                                                    quality = dl.quality,
                                                    format = if (playUrl.contains(".mkv", ignoreCase = true)) "MKV" else "MP4",
                                                    url = playUrl,
                                                    label = dl.quality,
                                                    provider = "offline"
                                                )
                                                onPlayDownload(item, stream)
                                            }
                                        )
                                        CardActionButton(
                                            icon = Icons.AutoMirrored.Filled.OpenInNew,
                                            label = "External",
                                            tint = TextSecondary,
                                            onClick = {
                                                openWithExternalPlayer(context, dl.localFilePath)
                                            }
                                        )
                                        CardActionButton(
                                            icon = Icons.Default.Delete,
                                            label = "Delete",
                                            tint = Color(0xFFEF4444),
                                            onClick = { viewModel.deleteDownload(dl) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item {
                    LibraryHeader(
                        selectedTab = state.selectedTab,
                        onSelectTab = { viewModel.selectTab(it) }
                    )
                }
                item {
                    SectionHeader(title = "Favorites", onSeeAllClick = {})

                    if (state.favorites.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp, horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Your library is empty",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Watch, favourite or add titles and they will show up here.",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(state.favorites) { fav ->
                                val item = MediaItem(
                                    id = fav.id,
                                    title = fav.title,
                                    mediaType = try { MediaType.valueOf(fav.mediaType) } catch (_: Exception) { MediaType.MOVIE },
                                    year = fav.year,
                                    rating = fav.rating,
                                    posterUrl = fav.posterUrl
                                )
                                VerticalPosterCard(
                                    item = item,
                                    onClick = { onMediaClick(item) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }

                item {
                    SectionHeader(title = "My playlists", onSeeAllClick = null)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Button(
                            onClick = { viewModel.addPlaylist("Playlist ${state.playlists.size + 1}") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CardElevated)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Add New",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.playlists) { pl ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CardDark)
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = pl,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    selectedTab: LibraryTab,
    onSelectTab: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topPadding = if (statusBarHeight > 0.dp) (statusBarHeight - 14.dp).coerceAtLeast(2.dp) else 4.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding)
    ) {
        Text(
            text = "Library",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CardElevated)
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf(
                Pair(LibraryTab.FAVORITES, "Favorites"),
                Pair(LibraryTab.DOWNLOADS, "Downloads")
            ).forEach { (tab, label) ->
                val isSelected = selectedTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) CardDark else Color.Transparent)
                        .clickable { onSelectTab(tab) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) TextPrimary else TextMuted,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(java.util.Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(java.util.Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0L) return ""
    val mb = bytesPerSec / (1024.0 * 1024.0)
    return if (mb >= 0.1) {
        String.format(java.util.Locale.US, "%.1f MB/s", mb)
    } else {
        val kb = bytesPerSec / 1024.0
        String.format(java.util.Locale.US, "%.0f KB/s", kb)
    }
}

private fun openWithExternalPlayer(context: Context, filePath: String?) {
    if (filePath.isNullOrBlank()) {
        Toast.makeText(context, "No local file available yet", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val file = java.io.File(filePath)
        if (!file.exists()) {
            Toast.makeText(context, "Downloaded file not found on device", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            val mime = if (filePath.endsWith(".mkv", ignoreCase = true)) "video/x-matroska" else "video/*"
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Play with External Player")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Error opening player: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun extractDisplayQuality(rawQuality: String): String {
    val q = rawQuality.trim()
    val match = Regex("""(4K|2160p?|1080p?|720p?|480p?|360p?)""", RegexOption.IGNORE_CASE).find(q)
    return when {
        match != null -> {
            val matched = match.value.lowercase()
            when {
                matched.startsWith("4k") || matched.startsWith("2160") -> "4K"
                matched.startsWith("1080") -> "1080p"
                matched.startsWith("720") -> "720p"
                matched.startsWith("480") -> "480p"
                matched.startsWith("360") -> "360p"
                else -> match.value.uppercase()
            }
        }
        q.contains("fhd", ignoreCase = true) -> "1080p"
        q.contains("hd", ignoreCase = true) -> "720p"
        q.isNotBlank() -> q
        else -> "HD"
    }
}

@Composable
private fun QualityBadge(
    quality: String,
    modifier: Modifier = Modifier
) {
    val display = extractDisplayQuality(quality)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = CardElevated,
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Text(
            text = display,
            color = Color(0xFFE2E8F0),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun CardActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDestructive = tint == Color(0xFFEF4444)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isDestructive) Color(0xFFEF4444).copy(alpha = 0.12f) else CardElevated,
        border = BorderStroke(0.5.dp, if (isDestructive) Color(0xFFEF4444).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f)),
        modifier = modifier.height(30.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = tint,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ProviderBadge(
    provider: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(30.dp),
        shape = RoundedCornerShape(8.dp),
        color = CardElevated,
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp)
        ) {
            Text(
                text = provider,
                color = Color(0xFFCBD5E1),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FormatBadge(
    format: String,
    modifier: Modifier = Modifier
) {
    val upper = format.uppercase()
    val (bgColor, textColor, borderColor) = when (upper) {
        "HLS" -> Triple(Color(0xFF064E3B).copy(alpha = 0.4f), Color(0xFF34D399), Color(0xFF059669).copy(alpha = 0.5f))
        "MKV" -> Triple(Color(0xFF4C1D95).copy(alpha = 0.4f), Color(0xFFA78BFA), Color(0xFF7C3AED).copy(alpha = 0.5f))
        "MP4" -> Triple(Color(0xFF1E3A8A).copy(alpha = 0.4f), Color(0xFF60A5FA), Color(0xFF2563EB).copy(alpha = 0.5f))
        "DASH" -> Triple(Color(0xFF78350F).copy(alpha = 0.4f), Color(0xFFFBBF24), Color(0xFFD97706).copy(alpha = 0.5f))
        else -> Triple(CardElevated, Color(0xFFE2E8F0), Color.White.copy(alpha = 0.15f))
    }
    Surface(
        modifier = modifier.height(30.dp),
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(0.5.dp, borderColor)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = upper,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                maxLines = 1
            )
        }
    }
}

private fun extractDownloadProvider(headersJson: String?, mediaId: String, streamUrl: String): String {
    if (!headersJson.isNullOrBlank()) {
        try {
            val json = JSONObject(headersJson)
            val p = json.optString("__stream_provider", "").trim()
            if (p.isNotBlank()) {
                return normalizeProviderName(p)
            }
        } catch (_: Exception) {}
    }

    if (mediaId.contains(":")) {
        val p = mediaId.substringBefore(":").trim()
        if (p.isNotBlank() && !p.equals("tmdb", ignoreCase = true) && !p.equals("imdb", ignoreCase = true)) {
            return normalizeProviderName(p)
        }
    }

    val lowerUrl = streamUrl.lowercase()
    return when {
        lowerUrl.contains("play4u") || lowerUrl.contains("p4u") -> "Play4U"
        lowerUrl.contains("4khdhub") || lowerUrl.contains("hubcloud") || lowerUrl.contains("streamwish") -> "4KHDHub"
        lowerUrl.contains("hdhub4u") -> "HDHub4u"
        lowerUrl.contains("dramachi") -> "Dramachi"
        lowerUrl.contains("moviebox") || lowerUrl.contains("mbcdn") || lowerUrl.contains("fshvd") -> "MovieBox"
        else -> "MovieBox"
    }
}

private fun normalizeProviderName(raw: String): String {
    val lower = raw.trim().lowercase()
    return when {
        lower == "moviebox" || lower.contains("moviebox") -> "MovieBox"
        lower == "play4u" || lower.contains("play4u") -> "Play4U"
        lower == "fourkhdhub" || lower == "4khdhub" || lower.contains("4khd") -> "4KHDHub"
        lower == "hdhub4u" || lower.contains("hdhub") -> "HDHub4u"
        lower == "dramachi" || lower.contains("dramachi") -> "Dramachi"
        lower == "tv" -> "Live TV"
        lower == "offline" -> "Offline"
        else -> raw.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

private fun extractDownloadFormat(headersJson: String?, streamUrl: String, localFilePath: String?): String {
    val lowerUrl = streamUrl.lowercase()
    val lowerPath = localFilePath?.lowercase() ?: ""

    if (!headersJson.isNullOrBlank()) {
        try {
            val json = JSONObject(headersJson)
            val f = json.optString("__stream_format", "").trim()
            if (f.isNotBlank()) {
                val upper = f.uppercase()
                return when {
                    upper.contains("MKV") -> "MKV"
                    upper.contains("HLS") || upper.contains("M3U8") -> "HLS"
                    upper.contains("DASH") || upper.contains("MPD") -> "DASH"
                    upper.contains("MP4") -> "MP4"
                    upper.contains("DIRECT") -> if (lowerPath.endsWith(".mkv") || lowerUrl.contains(".mkv")) "MKV" else "MP4"
                    else -> upper
                }
            }
        } catch (_: Exception) {}
    }

    return when {
        lowerUrl.contains(".m3u8") || lowerUrl.contains("/hls/") -> "HLS"
        lowerUrl.contains(".mpd") || lowerUrl.contains("/dash/") -> "DASH"
        lowerPath.endsWith(".mkv") || lowerUrl.contains(".mkv") -> "MKV"
        lowerPath.endsWith(".mp4") || lowerUrl.contains(".mp4") -> "MP4"
        else -> "MP4"
    }
}

