package com.sumanonline.sumanmovies.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sumanonline.sumanmovies.data.models.MediaItem
import com.sumanonline.sumanmovies.ui.theme.CardElevated
import com.sumanonline.sumanmovies.ui.theme.LocalAppAccent
import com.sumanonline.sumanmovies.ui.theme.TextMuted
import com.sumanonline.sumanmovies.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HeroBannerCarousel(
    items: List<MediaItem>,
    isBookmarked: (MediaItem) -> Boolean,
    onPlayClick: (MediaItem) -> Unit,
    onBookmarkClick: (MediaItem) -> Unit,
    onInfoClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(440.dp)
                .background(MaterialTheme.colorScheme.background)
        )
        return
    }

    val accent = LocalAppAccent.current.color
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { items.size }
    )
    val coroutineScope = rememberCoroutineScope()
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(items) {
        if (items.isNotEmpty()) {
            pagerState.scrollToPage(0)
        }
    }

    LaunchedEffect(items.size, isDragged) {
        if (items.size > 1 && !isDragged) {
            while (true) {
                delay(6000L)
                if (!pagerState.isScrollInProgress && !isDragged) {
                    val nextPage = (pagerState.currentPage + 1) % items.size
                    pagerState.animateScrollToPage(
                        page = nextPage,
                        animationSpec = tween(durationMillis = 650)
                    )
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(440.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { index -> items.getOrNull(index)?.id ?: index.toString() }
        ) { page ->
            val item = items.getOrNull(page)
            if (item != null) {
                HeroSlide(
                    item = item,
                    isBookmarked = isBookmarked(item),
                    onPlayClick = { onPlayClick(item) },
                    onBookmarkClick = { onBookmarkClick(item) },
                    onInfoClick = { onInfoClick(item) }
                )
            }
        }

        if (items.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.indices.forEach { index ->
                    val isSelected = pagerState.currentPage == index
                    val dotWidth by animateDpAsState(
                        targetValue = if (isSelected) 18.dp else 5.dp,
                        animationSpec = tween(durationMillis = 300),
                        label = "heroDotWidth"
                    )

                    Box(
                        modifier = Modifier
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                            .padding(horizontal = 3.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .height(4.dp)
                                .width(dotWidth)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (isSelected) accent else TextMuted.copy(alpha = 0.45f)
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HeroBannerCarousel(
    item: MediaItem?,
    isBookmarked: Boolean,
    onPlayClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HeroBannerCarousel(
        items = listOfNotNull(item),
        isBookmarked = { isBookmarked },
        onPlayClick = { onPlayClick() },
        onBookmarkClick = { onBookmarkClick() },
        onInfoClick = { onInfoClick() },
        modifier = modifier
    )
}

@Composable
private fun HeroSlide(
    item: MediaItem,
    isBookmarked: Boolean,
    onPlayClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val accent = LocalAppAccent.current.color
    val image = item.backdropUrl ?: item.posterUrl

    Box(modifier = Modifier.fillMaxSize()) {
        if (!image.isNullOrBlank()) {
            AsyncImage(
                model = image,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        val bg = MaterialTheme.colorScheme.background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            bg.copy(alpha = 0.4f),
                            bg.copy(alpha = 0.85f),
                            bg
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 16.dp, bottom = 26.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (item.rating != null && item.rating > 0f) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = String.format("%.1f", item.rating),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                item.year?.let { y ->
                    Text(
                        text = y,
                        color = TextPrimary.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = if (item.mediaType.name == "SERIES") "Series" else "Movies",
                    color = TextPrimary.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.title,
                color = TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onPlayClick,
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Play Now",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                IconButton(
                    onClick = onBookmarkClick,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CardElevated.copy(alpha = 0.85f))
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = "Bookmark",
                        tint = if (isBookmarked) accent else TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onInfoClick,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CardElevated.copy(alpha = 0.85f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
