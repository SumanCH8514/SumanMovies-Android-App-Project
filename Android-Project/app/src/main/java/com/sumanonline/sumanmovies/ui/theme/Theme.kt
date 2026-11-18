package com.sumanonline.sumanmovies.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalAppAccent = staticCompositionLocalOf { AppAccent.ROYAL_INDIGO }

@Composable
fun SumanMoviesTheme(
    accent: AppAccent = AppAccent.ROYAL_INDIGO,
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = accent.color,
        onPrimary = Color.White,
        primaryContainer = accent.color.copy(alpha = 0.2f),
        onPrimaryContainer = Color.White,
        background = BgDark,
        onBackground = TextPrimary,
        surface = SurfaceDark,
        onSurface = TextPrimary,
        surfaceVariant = CardDark,
        onSurfaceVariant = TextSecondary,
        outline = BorderDark
    )

    CompositionLocalProvider(LocalAppAccent provides accent) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
