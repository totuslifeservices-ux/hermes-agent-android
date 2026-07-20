package com.nousresearch.hermes.agent.ui.themes

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Dark Palette ──────────────────────────────────────────────────
private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CB8FF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497C),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFB2C9E1),
    onSecondary = Color(0xFF1C3146),
    secondaryContainer = Color(0xFF33485E),
    onSecondaryContainer = Color(0xFFD1E4FF),
    tertiary = Color(0xFF8FBFD5),
    onTertiary = Color(0xFF003546),
    background = Color(0xFF0F1419),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF0F1419),
    onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF1E2329),
    onSurfaceVariant = Color(0xFFC1C7CF),
    outline = Color(0xFF3A4047),
    outlineVariant = Color(0xFF262C33),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    inverseSurface = Color(0xFFE1E2E8),
    inverseOnSurface = Color(0xFF1A1C1E),
    inversePrimary = Color(0xFF0061A4),
)

// ── Light Palette ─────────────────────────────────────────────────
private val LightColors = lightColorScheme(
    primary = Color(0xFF0061A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D35),
    secondary = Color(0xFF555F69),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E3ED),
    onSecondaryContainer = Color(0xFF121C25),
    tertiary = Color(0xFF3C6473),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE0E3E8),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C7CF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF2F3033),
    inverseOnSurface = Color(0xFFF1F0F4),
    inversePrimary = Color(0xFF9ACAFF),
)

@Composable
fun HermesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
