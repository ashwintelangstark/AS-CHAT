package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

enum class AppTheme {
    WHITE, BLACK, LOVE
}

private val WhiteColorScheme = lightColorScheme(
    primary = WhitePrimary,
    secondary = WhiteSecondary,
    background = WhiteBackground,
    surface = WhiteSurface,
    onPrimary = WhiteOnPrimary,
    onBackground = WhiteOnBackground,
    onSurface = WhiteOnSurface,
    primaryContainer = WhitePrimary.copy(alpha = 0.1f),
    onPrimaryContainer = WhitePrimary
)

private val BlackColorScheme = darkColorScheme(
    primary = BlackPrimary,
    secondary = BlackSecondary,
    background = BlackBackground,
    surface = BlackSurface,
    onPrimary = BlackOnPrimary,
    onBackground = BlackOnBackground,
    onSurface = BlackOnSurface,
    primaryContainer = BlackSurface,
    onPrimaryContainer = BlackPrimary
)

private val LoveColorScheme = darkColorScheme(
    primary = LovePrimary,
    secondary = LoveSecondary,
    background = LoveBackground,
    surface = LoveSurface,
    onPrimary = LoveOnPrimary,
    onBackground = LoveOnBackground,
    onSurface = LoveOnSurface,
    primaryContainer = LoveSurface,
    onPrimaryContainer = LovePrimary
)

@Composable
fun MyApplicationTheme(
    appTheme: AppTheme = AppTheme.WHITE,
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.WHITE -> WhiteColorScheme
        AppTheme.BLACK -> BlackColorScheme
        AppTheme.LOVE -> LoveColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
