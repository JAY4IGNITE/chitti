package com.owlcoders.chitti.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/*
 * Jetpack Compose ports of React Bits pieces (reactbits.dev), kept to the three that carry meaning
 * rather than decoration:
 *  - Modifier.spotlight         <- Components/SpotlightCard  (light follows the finger on a card)
 *  - CountUpText                <- TextAnimations/CountUp    (a metric arriving, not just appearing)
 *  - Modifier.staggeredEntrance <- Components/AnimatedList   (a list assembling in reading order)
 * Every effect honours the system reduced-motion setting (animator scale 0).
 */

// ------------------------------------------------------------------------------------ Surfaces

/**
 * SpotlightCard: a radial light that follows the finger while it is on the surface and fades out
 * after release. Observes pointer events on the Initial pass without consuming them, so clicks and
 * scrolling underneath keep working.
 */
fun Modifier.spotlight(
    color: Color = Color.White.copy(alpha = 0.10f),
    radiusFraction: Float = 0.8f
): Modifier = composed {
    var center by remember { mutableStateOf(Offset.Unspecified) }
    var pressed by remember { mutableStateOf(false) }
    val reduce = rememberReducedMotion()
    val visible by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(if (pressed) 120 else 500),
        label = "spotlight"
    )
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                center = down.position
                pressed = true
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.pressed) center = change.position else break
                }
                pressed = false
            }
        }
        .drawWithContent {
            drawContent()
            if (visible > 0.005f && center.isSpecified && !reduce) {
                val r = maxOf(size.width, size.height) * radiusFraction
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = color.alpha * visible), Color.Transparent),
                        center = center,
                        radius = r
                    ),
                    radius = r,
                    center = center
                )
            }
        }
}

// ------------------------------------------------------------------------------------ Lists

/** AnimatedList: item [index] fades, rises and un-scales into place after index * [stepMs]. */
fun Modifier.staggeredEntrance(index: Int, stepMs: Long = 55, maxDelayMs: Long = 600): Modifier = composed {
    val reduce = rememberReducedMotion()
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        delay(minOf(index * stepMs, maxDelayMs))
        progress.animateTo(1f, ChittiMotion.Settle)
    }
    graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * 24f * density
        scaleX = 0.97f + 0.03f * p
        scaleY = 0.97f + 0.03f * p
    }
}

/** A metric that counts up to [target] on first display and animates on later changes. */
@Composable
fun CountUpText(
    target: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = LocalContentColor.current,
    durationMs: Int = 800,
    fontWeight: FontWeight? = null
) {
    val reduce = rememberReducedMotion()
    var armed by remember { mutableStateOf(reduce) }
    LaunchedEffect(Unit) { armed = true }
    val value by animateIntAsState(
        targetValue = if (armed) target else 0,
        animationSpec = tween(if (reduce) 0 else durationMs, easing = FastOutSlowInEasing),
        label = "countUp"
    )
    Text(text = value.toString(), modifier = modifier, style = style, color = color, fontWeight = fontWeight)
}
