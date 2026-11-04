package com.nrworld.duelshot.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DuelColorScheme = darkColorScheme(
    primary = DuelBlue,
    onPrimary = Color.White,
    secondary = DuelMagenta,
    onSecondary = Color.White,
    tertiary = DuelCyan,
    background = DuelBg,
    onBackground = DuelText,
    surface = DuelSurface,
    onSurface = DuelText,
    surfaceVariant = DuelSurface2,
    onSurfaceVariant = DuelTextMuted,
    outline = DuelTextFaint,
    error = DuelRecord
)

@Composable
fun DuelShotsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.Transparent.toArgbOrNull()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = DuelColorScheme,
        typography = DuelTypography,
        content = content
    )
}

// Hjälp: sätter fönsterbakgrund utan att kräva API-helskinning.
private fun Color.toArgbOrNull(): Int = run {
    val a = (alpha * 255).toInt() shl 24
    val r = (red * 255).toInt() shl 16
    val g = (green * 255).toInt() shl 8
    val b = (blue * 255).toInt()
    a or r or g or b
}
