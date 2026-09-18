package com.owlcoders.chitti.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Compose port of React Bits' `<LatticeLoader />`: a 3x3 (or 4x4) lattice of dots lit by a
 * travelling wave while [status] is WORKING, which freezes and dissolves into a check (DONE)
 * or a cross (ERROR), next to a verb and a live stopwatch.
 */
enum class LatticeStatus { WORKING, DONE, ERROR }

/** One wave geometry: per-cell delay in [step] units (null = hole), loop length, speed scale, lit share. */
data class LatticePattern(
    val cells: List<Int?>,
    val loop: Float,
    val scale: Float = 1f,
    val lit: Float = 0.62f
)

object LatticePatterns {
    // 3 x 3
    val Arrow = LatticePattern(listOf(1, 2, 3, 0, 1, 2, 1, 2, 3), loop = 7.2f)
    val Dots = LatticePattern(listOf(0, 1, 2, 0, 1, 2, 0, 1, 2), loop = 3f, scale = 2.4f)
    val Ripple = LatticePattern(listOf(2, 1, 2, 1, 0, 1, 2, 1, 2), loop = 4.8f, scale = 1.5f)
    val Spiral = LatticePattern(listOf(0, 1, 2, 7, 8, 3, 6, 5, 4), loop = 9f, scale = 1.2f, lit = 0.35f)
    val Orbit = LatticePattern(listOf(0, 1, 2, 7, null, 3, 6, 5, 4), loop = 8f, scale = 1.2f)
    val Snake = LatticePattern(listOf(0, 1, 2, 5, 4, 3, 6, 7, 8), loop = 9f, lit = 0.35f)

    // 4 x 4
    val Orbit4 = LatticePattern(listOf(0, 1, 2, 3, 11, null, null, 4, 10, null, null, 5, 9, 8, 7, 6), loop = 6f, scale = 1.2f, lit = 0.45f)
    val Snake4 = LatticePattern(listOf(0, 1, 2, 3, 7, 6, 5, 4, 8, 9, 10, 11, 15, 14, 13, 12), loop = 16f, lit = 0.25f)
    val Sweep4 = LatticePattern(listOf(0, 1, 2, 3, 1, 2, 3, 4, 2, 3, 4, 5, 3, 4, 5, 6), loop = 5f, lit = 0.45f)
    val Spin4 = LatticePattern(listOf(0, 0, 1, 1, 0, 0, 1, 1, 3, 3, 2, 2, 3, 3, 2, 2), loop = 4f, scale = 1.6f, lit = 0.35f)
    val Rain4 = LatticePattern(listOf(0, 2, 1, 3, 1, 3, 2, 4, 2, 4, 3, 5, 3, 5, 4, 6), loop = 4f, scale = 1.2f, lit = 0.35f)
    val Pulse4 = LatticePattern(listOf(2, 1, 1, 2, 1, 0, 0, 1, 1, 0, 0, 1, 2, 1, 1, 2), loop = 2.4f, scale = 2.5f, lit = 0.45f)
}

private val MARKS_3 = mapOf(
    LatticeStatus.DONE to setOf(2, 3, 5, 7),
    LatticeStatus.ERROR to setOf(0, 2, 4, 6, 8)
)
private val MARKS_4 = mapOf(
    LatticeStatus.DONE to setOf(7, 8, 10, 13),
    LatticeStatus.ERROR to setOf(0, 3, 5, 6, 9, 10, 12, 15)
)

/**
 * The CSS keyframes `lattice-on` family, expressed as a function of the cell's phase in [0,1):
 * idle -> peak (held) -> idle, with the "lit" share deciding how long the cell stays bright.
 */
private fun waveOpacity(phase: Float, lit: Float, idle: Float, peak: Float): Float {
    val (up, holdEnd, down) = when {
        lit <= 0.25f -> Triple(0.07f, 0.17f, 0.25f)
        lit <= 0.35f -> Triple(0.10f, 0.24f, 0.35f)
        lit <= 0.45f -> Triple(0.13f, 0.31f, 0.45f)
        else -> Triple(0.18f, 0.42f, 0.62f)
    }
    val p = ((phase % 1f) + 1f) % 1f
    val t = when {
        p < up -> easeInOut(p / up)
        p < holdEnd -> 1f
        p < down -> 1f - easeInOut((p - holdEnd) / (down - holdEnd))
        else -> 0f
    }
    return idle + (peak - idle) * t
}

private fun easeInOut(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return if (c < 0.5f) 4f * c * c * c else 1f - ((-2f * c + 2f).let { it * it * it }) / 2f
}

private fun formatElapsed(tenths: Long): String =
    if (tenths < 600) "%.1fs".format(tenths / 10.0)
    else "${tenths / 600}m %.1fs".format((tenths % 600) / 10.0)

@Composable
fun LatticeLoader(
    status: LatticeStatus = LatticeStatus.WORKING,
    label: String = "Thinking",
    doneLabel: String = "Done in",
    errorLabel: String = "Failed after",
    pattern: LatticePattern = LatticePatterns.Orbit,
    grid: Int = 3,
    round: Boolean = true,
    color: Color = Color.Unspecified,
    doneColor: Color = Color(0xFF22C55E),
    errorColor: Color = Color(0xFFEF4444),
    cellSize: Dp = 6.dp,
    gap: Dp = 2.dp,
    fontSize: Int = 14,
    stepMs: Int = 90,
    idleOpacity: Float = 0.15f,
    glow: Boolean = false,
    showTimer: Boolean = true,
    modifier: Modifier = Modifier
) {
    val n = if (grid == 4) 4 else 3
    val ink = if (color == Color.Unspecified) androidx.compose.material3.LocalContentColor.current else color
    val marks = if (n == 4) MARKS_4 else MARKS_3
    val cells = List(n * n) { i -> pattern.cells.getOrNull(i) }
    val d = stepMs * pattern.scale
    val cycleMs = (pattern.loop * d).toInt().coerceAtLeast(200)
    val reduceMotion = rememberReducedMotion()

    // One shared clock for the whole lattice; each cell derives its phase from its delay.
    val transition = rememberInfiniteTransition(label = "lattice")
    val clock by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (reduceMotion) 1400 else cycleMs, easing = LinearEasing)),
        label = "clock"
    )

    // Run layer fades out and the mark layer materialises (scale 0.9 -> 1) on done/error.
    val working = status == LatticeStatus.WORKING
    val runAlpha by animateFloatAsState(if (working) 1f else 0f, tween(200), label = "run")
    val markAlpha by animateFloatAsState(if (working) 0f else 1f, tween(200), label = "mark")
    val markScale by animateFloatAsState(
        if (working && !reduceMotion) 0.9f else 1f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "markScale"
    )
    val markColor = if (status == LatticeStatus.ERROR) errorColor else doneColor
    val lastMark = remember { mutableStateOf(LatticeStatus.DONE) }
    if (!working) lastMark.value = status
    val markCells = marks[lastMark.value] ?: emptySet()

    // Stopwatch: ticks every 100 ms while working, freezes on done/error.
    var tenths by remember { mutableLongStateOf(0L) }
    LaunchedEffect(status) {
        if (working) {
            val startedAt = System.nanoTime()
            tenths = 0
            while (isActive) {
                delay(100)
                tenths = (System.nanoTime() - startedAt) / 100_000_000L
            }
        }
    }

    val latticeSide = cellSize * n + gap * (n - 1)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(latticeSide)) {
            val cellPx = cellSize.toPx()
            val gapPx = gap.toPx()
            val radius = if (round) CornerRadius(cellPx / 2f) else CornerRadius(maxOf(1f, cellPx * 0.25f))
            val peak = if (reduceMotion) 0.7f else 1f
            for (i in 0 until n * n) {
                val col = i % n
                val row = i / n
                val topLeft = Offset(col * (cellPx + gapPx), row * (cellPx + gapPx))
                val size = Size(cellPx, cellPx)

                // Wave layer
                val unit = cells[i]
                val runOpacity = if (unit == null) {
                    idleOpacity * 0.47f
                } else {
                    val delayFrac = if (reduceMotion) 0f else (unit * d) / cycleMs
                    waveOpacity(clock - delayFrac, pattern.lit, idleOpacity, peak)
                }
                if (runAlpha > 0.001f) {
                    if (glow && unit != null && runOpacity > idleOpacity + 0.05f) {
                        drawRoundRect(
                            color = ink.copy(alpha = 0.35f * runAlpha * runOpacity),
                            topLeft = topLeft - Offset(cellPx * 0.25f, cellPx * 0.25f),
                            size = Size(cellPx * 1.5f, cellPx * 1.5f),
                            cornerRadius = CornerRadius(cellPx)
                        )
                    }
                    drawRoundRect(color = ink.copy(alpha = runOpacity * runAlpha), topLeft = topLeft, size = size, cornerRadius = radius)
                }

                // Mark layer
                if (markAlpha > 0.001f) {
                    val on = i in markCells
                    val c = if (on) markColor else ink
                    val a = (if (on) 1f else idleOpacity) * markAlpha
                    val center = topLeft + Offset(cellPx / 2f, cellPx / 2f)
                    val s = cellPx * markScale
                    val tl = center - Offset(s / 2f, s / 2f)
                    if (glow && on) {
                        drawRoundRect(color = c.copy(alpha = 0.35f * a), topLeft = tl - Offset(s * 0.25f, s * 0.25f), size = Size(s * 1.5f, s * 1.5f), cornerRadius = CornerRadius(s))
                    }
                    drawRoundRect(color = c.copy(alpha = a), topLeft = tl, size = Size(s, s), cornerRadius = if (round) CornerRadius(s / 2f) else CornerRadius(maxOf(1f, s * 0.25f)))
                }
            }
        }

        Spacer(Modifier.width((fontSize * 0.625f).dp))

        val verb = when (status) {
            LatticeStatus.WORKING -> label
            LatticeStatus.DONE -> doneLabel
            LatticeStatus.ERROR -> errorLabel
        }
        Text(
            text = verb,
            color = ink,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium
        )
        if (showTimer) {
            Spacer(Modifier.width((fontSize * 0.625f).dp))
            Text(
                text = formatElapsed(tenths),
                color = ink.copy(alpha = 0.6f),
                fontSize = (fontSize * 0.875f).sp,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
