package com.owlcoders.chitti.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ChittiColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = TextHigh,
    primaryContainer = AccentDeep,
    onPrimaryContainer = TextHigh,
    secondary = Iris,
    onSecondary = TextHigh,
    secondaryContainer = Surface3,
    onSecondaryContainer = TextHigh,
    tertiary = Sky,
    onTertiary = Ink,
    background = Ink,
    onBackground = TextHigh,
    surface = Surface1,
    onSurface = TextHigh,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextMid,
    surfaceContainer = Surface2,
    surfaceContainerHigh = Surface3,
    outline = Hairline,
    outlineVariant = Hairline,
    error = Rose,
    onError = TextHigh,
    scrim = Scrim
)

/** Corner scale: controls 10, chips 12, cards 16, hero cards 20, sheets 28. */
val ChittiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun ChittiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChittiColorScheme,
        typography = ChittiTypography,
        shapes = ChittiShapes,
        content = content
    )
}
