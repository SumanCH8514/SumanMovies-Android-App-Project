package com.sumanonline.sumanmovies.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

val RawBgDark = Color(0xFF08080A)
val RawSurfaceDark = Color(0xFF121217)
val RawCardDark = Color(0xFF16161D)
val RawCardElevated = Color(0xFF121217)
val RawTextPrimary = Color(0xFFF3F4F6)
val RawTextSecondary = Color(0xFF9CA3AF)
val RawTextMuted = Color(0xFF6B7280)
val RawBorderDark = Color(0xFF1F1F27)

val RawBgLight = Color(0xFFF1F5F9)
val RawSurfaceLight = Color(0xFFFFFFFF)
val RawCardLight = Color(0xFFFFFFFF)
val RawCardElevatedLight = Color(0xFFE2E8F0)
val RawTextPrimaryLight = Color(0xFF0F172A)
val RawTextSecondaryLight = Color(0xFF475569)
val RawTextMutedLight = Color(0xFF64748B)
val RawBorderLight = Color(0xFFCBD5E1)

val BgDark: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalThemeIsDark.current) RawBgDark else RawBgLight

val SurfaceDark: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalThemeIsDark.current) RawSurfaceDark else RawSurfaceLight

val CardDark: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalThemeIsDark.current) RawCardDark else RawCardLight

val CardElevated: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalThemeIsDark.current) RawCardElevated else RawCardElevatedLight

val TextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalThemeIsDark.current) RawTextPrimary else RawTextPrimaryLight

val TextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalThemeIsDark.current) RawTextSecondary else RawTextSecondaryLight

val TextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalThemeIsDark.current) RawTextMuted else RawTextMutedLight

val BorderDark: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalThemeIsDark.current) RawBorderDark else RawBorderLight

val AccentRoyalIndigo = Color(0xFF4F46E5)
val AccentCrimsonRed = Color(0xFFE11D48)
val AccentSunsetGold = Color(0xFFF59E0B)
val AccentNeonPurple = Color(0xFFA855F7)
val AccentElectricCyan = Color(0xFF06B6D4)
val AccentEmeraldMint = Color(0xFF10B981)
val AccentMono = Color(0xFFFFFFFF)

enum class AppAccent(val displayName: String, val color: Color) {
    ROYAL_INDIGO("Royal Indigo", AccentRoyalIndigo),
    CRIMSON_RED("Crimson Red", AccentCrimsonRed),
    SUNSET_GOLD("Sunset Gold", AccentSunsetGold),
    NEON_PURPLE("Neon Purple", AccentNeonPurple),
    ELECTRIC_CYAN("Electric Cyan", AccentElectricCyan),
    EMERALD_MINT("Emerald Mint", AccentEmeraldMint),
    MONO("Mono", AccentMono)
}
