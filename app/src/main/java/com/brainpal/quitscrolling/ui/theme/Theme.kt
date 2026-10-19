package com.brainpal.quitscrolling.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BrainPalDarkColorScheme = darkColorScheme(
    primary = AccentGold,
    onPrimary = ButtonDarkText,
    secondary = BadgeTextRed,
    onSecondary = TextPrimaryWhite,
    tertiary = ButtonCream,
    background = ObsidianBackground,
    onBackground = TextPrimaryWhite,
    surface = CardSurface,
    onSurface = TextPrimaryWhite,
    surfaceVariant = CardSurfaceElevated,
    onSurfaceVariant = TextSecondaryMuted,
    outline = CardBorder
)

@Composable
fun BrainPalTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BrainPalDarkColorScheme,
        typography = BrainPalTypography,
        content = content
    )
}
