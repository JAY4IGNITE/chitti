package com.owlcoders.chitti.ui.components

import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Motion vocabulary translated from Apple's "Designing Fluid Interfaces":
 *  - feedback on press-down, never only on release;
 *  - springs (interruptible, velocity-aware) instead of fixed-duration tweens for anything touched;
 *  - critically damped by default, a little bounce only after a gesture carried momentum;
 *  - respect the user's reduced-motion preference.
 */
object ChittiMotion {
    /** Apple "damping 1.0, response 0.3": smooth settle, no overshoot. */
    val Settle: SpringSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 400f)

    /** Apple "damping 0.8, response 0.3": for sheets and thrown things. */
    val Sheet: SpringSpec<Float> = spring(dampingRatio = 0.8f, stiffness = 300f)

    /** Quick press feedback. */
    val Press: SpringSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 900f)

    /** [Settle] for any animated type (offsets, sizes, colours). */
    fun <T> settle(): SpringSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 400f)

    /** [Sheet] for any animated type. */
    fun <T> sheet(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 300f)
}

/** True when the system animator scale is 0 (the Android equivalent of prefers-reduced-motion). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Scales the element down the instant it is pressed and springs back on release.
 * Pass the same [interactionSource] you give to `clickable`/`Button`.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressed: Float = 0.96f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val reduce = rememberReducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reduce) pressed else 1f,
        animationSpec = ChittiMotion.Press,
        label = "pressScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Apple's momentum projection (from the "Designing Fluid Interfaces" sample code): where a
 * flick at [velocityPxPerSec] would come to rest under scroll-style deceleration.
 */
fun projectMomentum(velocityPxPerSec: Float, decelerationRate: Float = 0.998f): Float =
    (velocityPxPerSec / 1000f) * decelerationRate / (1f - decelerationRate)

/** Progressive resistance past a boundary: the further past it, the less the element follows. */
fun rubberBand(overshoot: Float, dimension: Float, constant: Float = 0.55f): Float =
    (overshoot * dimension * constant) / (dimension + constant * overshoot)

/** [rubberBand] for a signed overshoot. */
fun rubberBandSigned(overshoot: Float, dimension: Float): Float =
    if (overshoot >= 0f) rubberBand(overshoot, dimension) else -rubberBand(-overshoot, dimension)

// ------------------------------------------------------------------------------------ Haptics

/**
 * Haptics for the moments that earn them (Apple's audio-haptic rules: causality, harmony, utility).
 * Fire on the same frame as the visual change that caused it, and only for commits, snaps,
 * threshold crossings and outcomes, never for every tap. Honours the system haptics setting.
 */
class ChittiHaptics internal constructor(private val view: View) {
    /** A detent: a segment or tab passing under the finger. */
    fun tick() = perform(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)

    /** A drag crossed the point where releasing would commit. */
    fun threshold() = perform(
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
        else HapticFeedbackConstants.CONTEXT_CLICK
    )

    /** Something finished successfully. */
    fun confirm() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)

    /** Something failed or was refused. */
    fun reject() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)

    /** A primary control was pressed down (mic, send). */
    fun press() = perform(HapticFeedbackConstants.KEYBOARD_TAP)

    private fun perform(constant: Int) {
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberHaptics(): ChittiHaptics {
    val view = LocalView.current
    return remember(view) { ChittiHaptics(view) }
}

// ------------------------------------------------------------------------------------ Edges

/**
 * Scroll-edge effect: content fades out where it meets floating chrome instead of being cut by a
 * hard divider. Only draws a fade on an edge while content actually continues past it.
 */
fun Modifier.fadingEdges(
    top: Dp = 0.dp,
    bottom: Dp = 0.dp,
    showTop: Boolean = true,
    showBottom: Boolean = true
): Modifier = composed {
    val topAlpha by animateFloatAsState(if (showTop) 1f else 0f, ChittiMotion.Settle, label = "fadeTop")
    val bottomAlpha by animateFloatAsState(if (showBottom) 1f else 0f, ChittiMotion.Settle, label = "fadeBottom")
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val t = top.toPx()
            if (t > 0f && topAlpha > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 1f - topAlpha),
                        1f to Color.Black,
                        startY = 0f,
                        endY = t
                    ),
                    size = Size(size.width, t),
                    blendMode = BlendMode.DstIn
                )
            }
            val b = bottom.toPx()
            if (b > 0f && bottomAlpha > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black,
                        1f to Color.Black.copy(alpha = 1f - bottomAlpha),
                        startY = size.height - b,
                        endY = size.height
                    ),
                    topLeft = Offset(0f, size.height - b),
                    size = Size(size.width, b),
                    blendMode = BlendMode.DstIn
                )
            }
        }
}
