package com.owlcoders.chitti.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val GeminiColorScheme = darkColorScheme(
    primary = GeminiBlue,
    onPrimary = TextPrimary,
    primaryContainer = GeminiSurfaceElevated,
    onPrimaryContainer = GeminiCyan,
    secondary = GeminiPurple,
    onSecondary = TextPrimary,
    secondaryContainer = GeminiSurfaceCard,
    onSecondaryContainer = GeminiPink,
    tertiary = GeminiCyan,
    onTertiary = GeminiDarkBg,
    background = GeminiDarkBg,
    onBackground = TextPrimary,
    surface = GeminiSurface,
    onSurface = TextPrimary,
    surfaceVariant = GeminiSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = GeminiBorder,
    error = GeminiRed,
    onError = TextPrimary
)

@Composable
fun ChittiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GeminiColorScheme,
        typography = ChittiTypography,
        content = content
    )
}
