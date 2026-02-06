package com.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val StatusRunning = Color(0xFF48BB78)
val StatusStopped = Color(0xFF718096)
val StatusStarting = Color(0xFFED8936)
val StatusError = Color(0xFFFC8181)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D1B2A),
    primaryContainer = Color(0xFF1A365D),
    onPrimaryContainer = Color(0xFFBBDEFB),
    secondary = Color(0xFFA5D6A7),
    onSecondary = Color(0xFF1B3A1B),
    surface = Color(0xFF1A1A2E),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF2D2D44),
    onSurfaceVariant = Color(0xFFA0AEC0),
    background = Color(0xFF0F0F1A),
    onBackground = Color(0xFFE2E8F0),
    error = Color(0xFFFC8181),
    onError = Color(0xFF1A1A2E),
    outline = Color(0xFF4A5568)
)

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
