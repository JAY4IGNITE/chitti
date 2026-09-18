package com.owlcoders.chitti.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/*
 * Chitti design tokens.
 *
 * One theme, one accent. The neutral ramp is very slightly blue so the surfaces read as a material
 * instead of flat grey paint. The accent marks interactive and selected state only; the semantic
 * colours carry meaning (success / warning / danger / info) and nothing else. Category colours are
 * muted on purpose: an agenda list full of neon reads as noise, not as information.
 */

// ---------------------------------------------------------------- Neutral ramp
val Ink = Color(0xFF07080B)            // app background
val Surface1 = Color(0xFF0E1116)       // cards, bars
val Surface2 = Color(0xFF141A23)       // raised: inputs, chips, elevated cards
val Surface3 = Color(0xFF1C2330)       // pressed / selected surface
val Hairline = Color(0xFF212A38)       // 1dp separators and card borders
val HairlineStrong = Color(0xFF2C3647) // focused borders

// ---------------------------------------------------------------- Text
val TextHigh = Color(0xFFECEFF5)       // titles, values
val TextMid = Color(0xFF98A1B2)        // body, subtitles
val TextLow = Color(0xFF6B7484)        // overlines, metadata, disabled

// ---------------------------------------------------------------- Accent
val Accent = Color(0xFF5B7CFA)
val AccentBright = Color(0xFF8AA3FF)
val AccentDeep = Color(0xFF3A57C9)
val AccentWash = Color(0x1F5B7CFA)     // 12% accent, for selected pills and icon wells

// ---------------------------------------------------------------- Semantic
val Mint = Color(0xFF35D6A4)           // success, done, low urgency
val Amber = Color(0xFFF2B33D)          // warning, medium urgency
val Rose = Color(0xFFF2666E)           // danger, destructive, high urgency
val Sky = Color(0xFF48B7F0)            // information
val Iris = Color(0xFFA78BFA)           // secondary category

// ---------------------------------------------------------------- Scrims & gradients
val Scrim = Color(0xCC05060A)

/** A quiet two-stop accent sweep for thin rules and rings. Never used as a fill behind text. */
val AccentGradient = Brush.horizontalGradient(listOf(AccentDeep, Accent, AccentBright))

/** Ambient wash behind a hero area: one hue, very low alpha. */
val AmbientGlow = Brush.radialGradient(
    colors = listOf(Accent.copy(alpha = 0.22f), Accent.copy(alpha = 0.06f), Color.Transparent)
)

/** Semantic helpers so every screen maps data to colour the same way. */
fun categoryColor(category: String?): Color = when (category?.lowercase()) {
    "work", "office", "project" -> Accent
    "academic", "college", "branch", "study" -> Iris
    "personal", "person", "family" -> Mint
    "finance", "payment", "bill" -> Amber
    else -> Sky
}

fun urgencyColor(urgency: String?): Color = when (urgency?.lowercase()) {
    "high" -> Rose
    "medium" -> Amber
    else -> Mint
}

fun priorityColor(priority: Int): Color = when (priority) {
    2 -> Rose
    1 -> Amber
    else -> Mint
}

// ---------------------------------------------------------------- Compatibility aliases
// Older screens refer to the previous palette by name. They now resolve to the tokens above, so
// nothing is left on the old colours while the screens are migrated.
val GeminiDarkBg = Ink
val GeminiSurface = Surface1
val GeminiSurfaceElevated = Surface2
val GeminiSurfaceCard = Surface1
val GeminiBorder = Hairline
val GeminiBlue = Accent
val GeminiCyan = Accent
val GeminiPurple = Iris
val GeminiPink = Rose
val GeminiAmber = Amber
val GeminiGreen = Mint
val GeminiRed = Rose
val TextPrimary = TextHigh
val TextSecondary = TextMid
val TextMuted = TextLow
val LightScreenInk = TextHigh
val LightScreenBg = Ink
val GeminiGradient = AccentGradient
val GeminiOrbGradient = AmbientGlow
val CardGlowGradient = AmbientGlow
val PaperWhite = TextHigh
val CorkboardBrown = Ink
val InkBlack = Ink
val ActionRed = Rose
val OverlayScrim = Scrim
