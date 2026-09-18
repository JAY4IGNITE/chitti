package com.owlcoders.chitti.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.Alignment
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
 * Jetpack Compose ports of React Bits pieces (reactbits.dev), kept to the ones that carry meaning
 * rather than decoration:
 *  - Modifier.spotlight         <- Components/SpotlightCard     (light follows the finger on a card)
 *  - CountUpText                <- TextAnimations/CountUp       (a metric arriving, not just appearing)
 *  - Modifier.staggeredEntrance <- Components/AnimatedList      (a list assembling in reading order)
 *  - BlurText                   <- TextAnimations/BlurText      (the greeting arriving, once)
 *  - RotatingText               <- TextAnimations/RotatingText  (example voice commands, one at a time)
 *  - WordRevealText             <- TextAnimations/SplitText     (a fresh reply reading in like a stream)
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

/**
 * AnimatedList: item [index] fades, rises and un-scales into place after index * [stepMs].
 * Plays once: the "played" flag is saveable, so a lazy item scrolled back into view, or a tab
 * revisited, is simply there. Only the first screenful is staggered; an item first reached by
 * scrolling animates immediately instead of leaving a blank gap under the finger.
 */
fun Modifier.staggeredEntrance(index: Int, stepMs: Long = 55, maxDelayMs: Long = 600): Modifier = composed {
    val reduce = rememberReducedMotion()
    var played by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(reduce) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (played) return@LaunchedEffect
        if (index < 8) delay(minOf(index * stepMs, maxDelayMs))
        progress.animateTo(1f, ChittiMotion.Settle)
        played = true
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

// ------------------------------------------------------------------------------------ Text

/**
 * BlurText (React Bits TextAnimations/BlurText): words rise out of a soft blur one after another,
 * in reading order. Plays once per [text]; revisiting the screen shows it settled, so it reads as
 * an arrival rather than a loop. Blur needs API 31+; below that the words only fade and rise.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlurText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = LocalContentColor.current,
    stepMs: Long = 90,
    maxBlur: Dp = 10.dp
) {
    val reduce = rememberReducedMotion()
    var played by androidx.compose.runtime.saveable.rememberSaveable(text) { mutableStateOf(reduce) }
    val words = remember(text) { text.split(' ').filter { it.isNotEmpty() } }
    FlowRow(modifier = modifier) {
        words.forEachIndexed { index, word ->
            val progress = remember(text) { Animatable(if (played) 1f else 0f) }
            LaunchedEffect(text) {
                if (progress.value >= 1f) return@LaunchedEffect
                delay(index * stepMs)
                progress.animateTo(1f, ChittiMotion.settle())
                if (index == words.lastIndex) played = true
            }
            val p = progress.value
            Text(
                text = if (index == words.lastIndex) word else "$word ",
                style = style,
                color = color,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = p
                        translationY = (1f - p) * 10f * density
                    }
                    .then(if (p < 1f) Modifier.blur(maxBlur * (1f - p)) else Modifier)
            )
        }
    }
}

/**
 * RotatingText (React Bits TextAnimations/RotatingText): cycles through [items], each leaving
 * upward as the next rises from below, so the direction of travel says "next". Stops rotating
 * (shows the first item) under reduced motion.
 */
@Composable
fun RotatingText(
    items: List<String>,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = LocalContentColor.current,
    intervalMs: Long = 2800,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null
) {
    val reduce = rememberReducedMotion()
    var index by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(items, reduce) {
        if (reduce || items.size < 2) return@LaunchedEffect
        while (isActive) {
            delay(intervalMs)
            index = (index + 1) % items.size
        }
    }
    androidx.compose.animation.AnimatedContent(
        targetState = items.getOrElse(index) { "" },
        modifier = modifier,
        transitionSpec = {
            (androidx.compose.animation.slideInVertically(ChittiMotion.settle()) { it / 2 } +
                androidx.compose.animation.fadeIn(tween(220))) togetherWith
                (androidx.compose.animation.slideOutVertically(ChittiMotion.settle()) { -it / 2 } +
                    androidx.compose.animation.fadeOut(tween(160)))
        },
        contentAlignment = Alignment.Center,
        label = "rotatingText"
    ) { value ->
        Text(value, style = style, color = color, textAlign = textAlign)
    }
}

/**
 * Word-by-word reveal for text that just arrived (an assistant reply), the way a streamed answer
 * reads. A single Text with per-word alpha, so wrapping and selection behave like plain text.
 * With [animate] false, or under reduced motion, the text is shown at once.
 */
@Composable
fun WordRevealText(
    text: String,
    animate: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = LocalContentColor.current,
    perWordMs: Int = 28,
    maxMs: Int = 900,
    onRevealed: () -> Unit = {}
) {
    val reduce = rememberReducedMotion()
    val words = remember(text) { text.split(' ') }
    // Fully revealed is words + 2: each word takes two steps to reach full strength.
    val revealed = words.size + 2f
    val progress = remember(text) { Animatable(if (animate && !reduce) 0f else revealed) }
    LaunchedEffect(text, animate) {
        if (!animate || reduce || progress.value >= revealed) {
            if (animate) onRevealed()
            return@LaunchedEffect
        }
        val duration = (words.size * perWordMs).coerceAtMost(maxMs)
        // Each word takes ~2 steps to fade in, so the leading edge reads soft, not typed.
        progress.animateTo(revealed, tween(duration, easing = LinearEasing))
        onRevealed()
    }
    val p = progress.value
    val annotated = if (p >= revealed) {
        androidx.compose.ui.text.AnnotatedString(text)
    } else {
        androidx.compose.ui.text.buildAnnotatedString {
            words.forEachIndexed { i, word ->
                val a = ((p - i) / 2f).coerceIn(0f, 1f)
                pushStyle(androidx.compose.ui.text.SpanStyle(color = color.copy(alpha = color.alpha * a)))
                append(word)
                if (i < words.lastIndex) append(' ')
                pop()
            }
        }
    }
    Text(text = annotated, modifier = modifier, style = style, color = color)
}
