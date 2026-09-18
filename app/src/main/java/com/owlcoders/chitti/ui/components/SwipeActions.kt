package com.owlcoders.chitti.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * What a horizontal swipe does. [removes] means the item leaves the list on commit, so the card
 * keeps flying off-screen; otherwise it springs home after the action runs.
 */
class SwipeAction(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val removes: Boolean,
    val onCommit: () -> Unit
)

/**
 * Swipe-to-act, built on the "Designing Fluid Interfaces" rules:
 *  - the card tracks the finger 1:1 (no animation while the finger is down);
 *  - a direction without an action rubber-bands instead of stopping hard;
 *  - crossing the commit point is felt (haptic) and seen (the label snaps to full strength);
 *  - on release the velocity is projected forward to decide commit vs. return, and the same
 *    velocity is handed to the spring so there is no seam;
 *  - grabbing a card that is still settling picks it up where it is (interruptible).
 * The actions are also exposed as accessibility custom actions, since a swipe is invisible to TalkBack.
 *
 * [startAction] is revealed by dragging right, [endAction] by dragging left.
 */
@Composable
fun SwipeActionBox(
    modifier: Modifier = Modifier,
    startAction: SwipeAction? = null,
    endAction: SwipeAction? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable () -> Unit
) {
    val offset = remember { Animatable(0f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    var armed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val start by rememberUpdatedState(startAction)
    val end by rememberUpdatedState(endAction)

    // Read through a function so the long-lived gesture coroutine always sees the current width.
    fun threshold() = widthPx * 0.32f

    val a11y = listOfNotNull(startAction, endAction).map { action ->
        CustomAccessibilityAction(action.label) { action.onCommit(); true }
    }

    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .semantics { if (a11y.isNotEmpty()) customActions = a11y }
            // Gestures live on the stationary container so positions (and therefore velocity)
            // are measured in a frame that does not move with the card.
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                var raw = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        tracker.resetTracking()
                        raw = offset.value
                        scope.launch { offset.stop() }
                    },
                    onHorizontalDrag = { change, delta ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        raw += delta
                        val allowed = (raw > 0f && start != null) || (raw < 0f && end != null)
                        val visual = if (allowed) raw else rubberBandSigned(raw, widthPx)
                        scope.launch { offset.snapTo(visual) }
                        val nowArmed = allowed && abs(visual) >= threshold()
                        if (nowArmed != armed) {
                            armed = nowArmed
                            haptics.threshold()
                        }
                    },
                    onDragEnd = {
                        val v = tracker.calculateVelocity().x
                        val current = offset.value
                        val projected = current + projectMomentum(v)
                        val action = if (projected > 0f) start else end
                        // Decide on direction of travel, not on where the finger happened to stop:
                        // a card flicked back toward home returns even if it is past the threshold.
                        val reversing = v != 0f && sign(v) != sign(current) && abs(v) > 300f
                        val commit = action != null && abs(projected) >= threshold() && !reversing
                        armed = false
                        scope.launch {
                            if (commit && action != null) {
                                haptics.confirm()
                                if (action.removes) {
                                    offset.animateTo(sign(projected) * widthPx * 1.15f, ChittiMotion.Settle, initialVelocity = v)
                                    action.onCommit()
                                    // If the row is not actually removed, do not leave it off-screen.
                                    delay(700)
                                    offset.snapTo(0f)
                                } else {
                                    action.onCommit()
                                    offset.animateTo(0f, ChittiMotion.Sheet, initialVelocity = v)
                                }
                            } else {
                                offset.animateTo(0f, ChittiMotion.Sheet, initialVelocity = v)
                            }
                        }
                    },
                    onDragCancel = {
                        armed = false
                        scope.launch { offset.animateTo(0f, ChittiMotion.Sheet) }
                    }
                )
            }
    ) {
        val x = offset.value
        val revealed = when {
            x > 0.5f -> startAction
            x < -0.5f -> endAction
            else -> null
        }
        if (revealed != null) {
            val progress = (abs(x) / threshold()).coerceIn(0f, 1f)
            val pop by animateFloatAsState(if (armed) 1f else 0f, ChittiMotion.Press, label = "swipePop")
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(revealed.tint.copy(alpha = 0.08f + 0.14f * progress)),
                contentAlignment = if (x > 0f) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = Space.xl)
                        .graphicsLayer {
                            alpha = progress
                            val s = 0.85f + 0.15f * progress + 0.08f * pop
                            scaleX = s
                            scaleY = s
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(revealed.icon, contentDescription = null, tint = revealed.tint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.s))
                    Text(
                        revealed.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = revealed.tint,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Box(modifier = Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }) {
            content()
        }
    }
}
