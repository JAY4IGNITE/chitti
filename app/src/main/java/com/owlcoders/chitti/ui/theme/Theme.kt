package com.owlcoders.chitti.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ChittiColorScheme = lightColorScheme(
    primary = ActionRed,
    background = CorkboardBrown,
    surface = PaperWhite,
    onPrimary = PaperWhite,
    onBackground = InkBlack,
    onSurface = InkBlack
)

@Composable
fun ChittiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChittiColorScheme,
        typography = ChittiTypography,
        content = content
    )
}
