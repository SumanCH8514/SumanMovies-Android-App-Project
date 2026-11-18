package com.sumanonline.sumanmovies.ui.theme

import androidx.compose.ui.graphics.Color

val BgDark = Color(0xFF08080A)
val SurfaceDark = Color(0xFF121217)
val CardDark = Color(0xFF1B1B22)
val CardElevated = Color(0xFF24242E)
val TextPrimary = Color(0xFFF3F4F6)
val TextSecondary = Color(0xFF9CA3AF)
val TextMuted = Color(0xFF6B7280)
val BorderDark = Color(0xFF2E2E38)

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
