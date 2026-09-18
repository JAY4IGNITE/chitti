package com.owlcoders.chitti.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Gemini & Google Assistant Theme Colors
val GeminiDarkBg = Color(0xFF0A0E17)
val GeminiSurface = Color(0xFF131A29)
val GeminiSurfaceElevated = Color(0xFF1C2538)
val GeminiSurfaceCard = Color(0xFF182033)
val GeminiBorder = Color(0xFF2B3852)

// Vibrant Gemini Accents
val GeminiBlue = Color(0xFF4285F4)
val GeminiPurple = Color(0xFF9B51E0)
val GeminiCyan = Color(0xFF00D2FF)
val GeminiPink = Color(0xFFFF416C)
val GeminiAmber = Color(0xFFFFB300)
val GeminiGreen = Color(0xFF00E676)
val GeminiRed = Color(0xFFFF5252)

// Text Colors
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

// Gradients
val GeminiGradient = Brush.horizontalGradient(
    colors = listOf(GeminiBlue, GeminiPurple, GeminiPink, GeminiCyan)
)

val GeminiOrbGradient = Brush.radialGradient(
    colors = listOf(
        GeminiCyan.copy(alpha = 0.8f),
        GeminiBlue.copy(alpha = 0.7f),
        GeminiPurple.copy(alpha = 0.5f),
        Color.Transparent
    )
)

val CardGlowGradient = Brush.linearGradient(
    colors = listOf(
        GeminiBlue.copy(alpha = 0.15f),
        GeminiPurple.copy(alpha = 0.08f),
        Color.Transparent
    )
)

// Backward Compatibility Aliases
val PaperWhite = Color(0xFFF8FAFC)
val CorkboardBrown = GeminiDarkBg
val InkBlack = Color(0xFF0A0E17)
val ActionRed = GeminiBlue
val OverlayScrim = Color(0xCC070A10)
