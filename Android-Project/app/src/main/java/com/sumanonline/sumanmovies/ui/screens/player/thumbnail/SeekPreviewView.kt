package com.sumanonline.sumanmovies.ui.screens.player.thumbnail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val previewAccent = Color(0xFFFFAD32)
private val previewBg = Color(0xEE11141B)

@Composable
fun SeekPreviewView(
    previewFrame: ThumbnailFrame?,
    progressFraction: Float,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val horizontalBias = (-1f + (progressFraction.coerceIn(0f, 1f) * 2f)).coerceIn(-0.84f, 0.84f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        contentAlignment = BiasAlignment(horizontalBias, 0f)
    ) {
        AnimatedVisibility(
            visible = previewFrame != null,
            enter = fadeIn() + scaleIn(initialScale = 0.88f),
            exit = fadeOut() + scaleOut(targetScale = 0.88f)
        ) {
            if (previewFrame != null) {
                Box(
                    modifier = Modifier
                        .width(148.dp)
                        .height(90.dp)
                        .shadow(12.dp, RoundedCornerShape(12.dp), spotColor = previewAccent.copy(alpha = 0.4f))
                        .clip(RoundedCornerShape(12.dp))
                        .background(previewBg)
                        .border(BorderStroke(1.5.dp, previewAccent.copy(alpha = 0.85f)), RoundedCornerShape(12.dp))
                ) {
                    val bitmap = previewFrame.bitmap
                    if (bitmap != null && !bitmap.isRecycled) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Seek Preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (previewFrame.isPlaceholder && !previewFrame.placeholderUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = previewFrame.placeholderUrl,
                            contentDescription = "Seek Preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            alpha = 0.45f
                        )
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF1B202A)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = previewAccent,
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.85f)
                                    ),
                                    startY = 40f
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = formatPreviewTime(previewFrame.timestampMs),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

private fun formatPreviewTime(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000L).coerceAtLeast(0L)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}
