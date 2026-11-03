package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class OrixSemanticColors(
    val isDark: Boolean,
    val background: Color,
    val surfaceCard: Color,
    val surfaceElevated: Color,
    val border: Color,
    val primaryText: Color,
    val mutedText: Color,
    val bullish: Color,
    val bearish: Color,
    val neutral: Color,
    val accent: Color
)

val LocalOrixColors = staticCompositionLocalOf {
    OrixSemanticColors(
        isDark = true,
        background = ObsidianBackground,
        surfaceCard = ObsidianSurfaceCard,
        surfaceElevated = ObsidianSurfaceElevated,
        border = ObsidianGlassBorder,
        primaryText = ObsidianPrimaryText,
        mutedText = ObsidianMutedText,
        bullish = ObsidianBullishEmerald,
        bearish = ObsidianBearishCrimson,
        neutral = ObsidianNeutralAmber,
        accent = ObsidianElectricAccent
    )
}

private val ObsidianColorScheme = darkColorScheme(
    primary = ObsidianElectricAccent,
    onPrimary = Color.White,
    secondary = ObsidianBullishEmerald,
    onSecondary = Color.Black,
    tertiary = ObsidianNeutralAmber,
    background = ObsidianBackground,
    onBackground = ObsidianPrimaryText,
    surface = ObsidianSurfaceCard,
    onSurface = ObsidianPrimaryText,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = ObsidianMutedText,
    outline = ObsidianGlassBorder,
    error = ObsidianBearishCrimson,
    onError = Color.White
)

private val AlabasterColorScheme = lightColorScheme(
    primary = AlabasterElectricAccent,
    onPrimary = Color.White,
    secondary = AlabasterBullishEmerald,
    onSecondary = Color.White,
    tertiary = AlabasterNeutralAmber,
    background = AlabasterBackground,
    onBackground = AlabasterPrimaryText,
    surface = AlabasterSurfaceCard,
    onSurface = AlabasterPrimaryText,
    surfaceVariant = AlabasterSurfaceElevated,
    onSurfaceVariant = AlabasterMutedText,
    outline = AlabasterCrispBorder,
    error = AlabasterBearishCrimson,
    onError = Color.White
)

@Composable
fun OrixVexTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ObsidianColorScheme else AlabasterColorScheme
    val semanticColors = if (darkTheme) {
        OrixSemanticColors(
            isDark = true,
            background = ObsidianBackground,
            surfaceCard = ObsidianSurfaceCard,
            surfaceElevated = ObsidianSurfaceElevated,
            border = ObsidianGlassBorder,
            primaryText = ObsidianPrimaryText,
            mutedText = ObsidianMutedText,
            bullish = ObsidianBullishEmerald,
            bearish = ObsidianBearishCrimson,
            neutral = ObsidianNeutralAmber,
            accent = ObsidianElectricAccent
        )
    } else {
        OrixSemanticColors(
            isDark = false,
            background = AlabasterBackground,
            surfaceCard = AlabasterSurfaceCard,
            surfaceElevated = AlabasterSurfaceElevated,
            border = AlabasterCrispBorder,
            primaryText = AlabasterPrimaryText,
            mutedText = AlabasterMutedText,
            bullish = AlabasterBullishEmerald,
            bearish = AlabasterBearishCrimson,
            neutral = AlabasterNeutralAmber,
            accent = AlabasterElectricAccent
        )
    }

    CompositionLocalProvider(LocalOrixColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
