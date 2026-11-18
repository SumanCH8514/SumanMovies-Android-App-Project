package com.sumanonline.sumanmovies.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalAppAccent = staticCompositionLocalOf { AppAccent.ROYAL_INDIGO }
val LocalThemeIsDark = staticCompositionLocalOf { true }

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
            background = RawBgDark,
            onBackground = RawTextPrimary,
            surface = RawSurfaceDark,
            onSurface = RawTextPrimary,
            surfaceVariant = RawCardDark,
            onSurfaceVariant = RawTextSecondary,
            outline = RawBorderDark
        )
    } else {
        lightColorScheme(
            primary = accent.color,
            onPrimary = Color.White,
            primaryContainer = accent.color.copy(alpha = 0.15f),
            onPrimaryContainer = accent.color,
            background = RawBgLight,
            onBackground = RawTextPrimaryLight,
            surface = RawSurfaceLight,
            onSurface = RawTextPrimaryLight,
            surfaceVariant = RawCardElevatedLight,
            onSurfaceVariant = RawTextSecondaryLight,
            outline = RawBorderLight
        )
    }

    CompositionLocalProvider(
        LocalAppAccent provides accent,
        LocalThemeIsDark provides isDark
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
