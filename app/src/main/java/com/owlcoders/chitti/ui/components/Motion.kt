package com.owlcoders.chitti.ui.components

import android.provider.Settings
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

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
