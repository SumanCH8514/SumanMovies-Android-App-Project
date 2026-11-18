package com.sumanonline.sumanmovies.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalAppAccent = staticCompositionLocalOf { AppAccent.ROYAL_INDIGO }

@Composable
fun SumanMoviesTheme(
    accent: AppAccent = AppAccent.ROYAL_INDIGO,
    isDark: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (isDark) {
        darkColorScheme(
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
    } else {
        lightColorScheme(
            primary = accent.color,
            onPrimary = Color.White,
            primaryContainer = accent.color.copy(alpha = 0.15f),
            onPrimaryContainer = accent.color,
            background = Color(0xFFF8F9FA),
            onBackground = Color(0xFF111827),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF111827),
            surfaceVariant = Color(0xFFF3F4F6),
            onSurfaceVariant = Color(0xFF4B5563),
            outline = Color(0xFFE5E7EB)
        )
    }

    CompositionLocalProvider(LocalAppAccent provides accent) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
