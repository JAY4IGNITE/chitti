package com.owlcoders.chitti.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.owlcoders.chitti.R

val CaveatFontFamily = FontFamily(
    Font(R.font.caveat, FontWeight.Normal)
)

val ChittiTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = CaveatFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.5.sp,
        color = InkBlack
    ),
    titleLarge = TextStyle(
        fontFamily = CaveatFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
        color = InkBlack
    )
)
