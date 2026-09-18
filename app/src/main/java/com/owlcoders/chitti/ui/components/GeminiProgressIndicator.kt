package com.owlcoders.chitti.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.ui.theme.GeminiBlue
import com.owlcoders.chitti.ui.theme.GeminiBorder
import com.owlcoders.chitti.ui.theme.GeminiCyan
import com.owlcoders.chitti.ui.theme.GeminiPink
import com.owlcoders.chitti.ui.theme.GeminiPurple

/**
 * Gemini cosmic iridescent rotating spinner.
 * Bulletproof against any Compose BOM / KeyframesSpec bytecode mismatch,
 * while delivering a high-end Gemini visual aesthetic.
 */
@Composable
fun GeminiCircularProgressIndicator(
    modifier: Modifier = Modifier.size(24.dp),
    strokeWidth: Dp = 2.5.dp,
    color: Color? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "GeminiSpinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SpinnerRotation"
    )

    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 45f,
        targetValue = 280f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SpinnerSweep"
    )

    val gradientBrush = if (color != null) {
        Brush.sweepGradient(listOf(color, color))
    } else {
        Brush.sweepGradient(
            listOf(GeminiCyan, GeminiBlue, GeminiPurple, GeminiPink, GeminiCyan)
        )
    }

    Canvas(modifier = modifier) {
        drawArc(
            brush = gradientBrush,
            startAngle = rotation,
            sweepAngle = sweepAngle,
            useCenter = false,
            style = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
}

/**
 * Gemini iridescent shimmering linear progress indicator.
 */
@Composable
fun GeminiLinearProgressIndicator(
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(4.dp)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "GeminiLinear")
    val offsetFraction by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "LinearOffset"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(GeminiBorder)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val startX = (offsetFraction - 0.35f) * width
            val endX = (offsetFraction + 0.35f) * width

            val brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    GeminiCyan,
                    GeminiBlue,
                    GeminiPurple,
                    Color.Transparent
                ),
                start = Offset(startX, 0f),
                end = Offset(endX, 0f)
            )

            drawRect(brush = brush)
        }
    }
}
