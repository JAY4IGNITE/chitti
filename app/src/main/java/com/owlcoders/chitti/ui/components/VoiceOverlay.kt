package com.owlcoders.chitti.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owlcoders.chitti.automation.AssistantResponse
import com.owlcoders.chitti.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class VoiceAssistantState {
    IDLE, LISTENING, THINKING, SPEAKING, RESULT
}

/**
 * The assistant sheet. Motion follows Apple's fluid-interface rules:
 *  - the sheet tracks the finger 1:1 while dragging (respecting where it was grabbed);
 *  - pulling up past the top rubber-bands instead of stopping hard;
 *  - on release the finger's velocity is projected forward to decide between settle and
 *    dismiss, and handed to the spring so there is no seam between drag and animation;
 *  - the scrim dims in proportion to how far the sheet has been pulled;
 *  - everything is interruptible (the sheet can be grabbed mid-settle).
 */
@Composable
fun GeminiVoiceOverlay(
    state: VoiceAssistantState,
    transcript: String,
    rmsLevel: Float, // 0.0f to 1.0f from mic
    assistantResponse: AssistantResponse? = null,
    onMicClick: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onStopSpeech: () -> Unit = {},
    onDocumentClick: (String) -> Unit = {}
) {
    val visible = state != VoiceAssistantState.IDLE
    // Back must close the overlay (and stop the mic/TTS via onDismiss) instead of navigating
    // underneath it while it keeps listening.
    BackHandler(enabled = visible, onBack = onDismiss)

    val reduceMotion = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) } // px; > 0 pulled down, < 0 pulled up (rubber-banded)
    var sheetHeightPx by remember { mutableFloatStateOf(1f) }
    var dismissing by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            dismissing = false
            dragOffset.snapTo(0f)
        }
    }

    val enter = if (reduceMotion) fadeIn(tween(160)) else
        fadeIn(tween(180)) + slideInVertically(ChittiMotion.settle()) { it / 2 } + scaleIn(ChittiMotion.Settle, initialScale = 0.96f)
    val exit = if (reduceMotion) fadeOut(tween(140)) else
        fadeOut(tween(160)) + slideOutVertically(tween(180, easing = FastOutLinearInEasing)) { it }

    AnimatedVisibility(visible = visible, enter = enter, exit = exit) {
        val pulled = (dragOffset.value / sheetHeightPx).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Scrim.copy(alpha = 0.80f * (1f - pulled))),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Top scrim dismiss area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )

            val dragState = rememberDraggableState { delta ->
                if (dismissing) return@rememberDraggableState
                val next = dragOffset.value + delta
                val bounded = if (next >= 0f) next else -rubberBand(-next, sheetHeightPx)
                scope.launch { dragOffset.snapTo(bounded) }
            }

            // Bottom sheet: consumes clicks so inner interactions do not bubble to onDismiss
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { sheetHeightPx = it.height.toFloat().coerceAtLeast(1f) }
                    .offset { IntOffset(0, dragOffset.value.roundToInt()) }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = dragState,
                        onDragStopped = { velocity ->
                            if (dismissing) return@draggable
                            // Momentum projection: where would the sheet come to rest?
                            val projected = dragOffset.value + projectMomentum(velocity)
                            if (projected > sheetHeightPx * 0.42f && velocity > -300f) {
                                dismissing = true
                                scope.launch {
                                    dragOffset.animateTo(
                                        sheetHeightPx,
                                        ChittiMotion.Settle,
                                        initialVelocity = velocity.coerceAtLeast(0f)
                                    )
                                    onDismiss()
                                }
                            } else {
                                // Hand the release velocity to the spring: no seam between drag and settle.
                                scope.launch { dragOffset.animateTo(0f, ChittiMotion.Sheet, initialVelocity = velocity) }
                            }
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* consume */ }
                    )
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Surface1)
                    .border(1.dp, Hairline, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            ) {
                // A single accent wash at the top edge; stronger while the mic is open.
                val washAlpha by animateFloatAsState(
                    if (state == VoiceAssistantState.LISTENING) 0.55f else 0.25f,
                    label = "sheetWash"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .graphicsLayer { alpha = washAlpha }
                        .background(AmbientGlow)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                // Drag handle (the whole sheet is draggable; the handle is the affordance)
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(TextLow.copy(alpha = 0.5f + 0.4f * pulled))
                )

                Spacer(modifier = Modifier.height(16.dp))

                // State & Assistant Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val dotColor by animateColorAsState(
                            when (state) {
                                VoiceAssistantState.LISTENING -> Accent
                                VoiceAssistantState.THINKING -> Amber
                                VoiceAssistantState.SPEAKING -> Mint
                                else -> TextLow
                            },
                            label = "stateDot"
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        AnimatedContent(
                            targetState = state,
                            transitionSpec = { (fadeIn(tween(150)) togetherWith fadeOut(tween(100))) },
                            label = "stateLabel"
                        ) { s ->
                            Text(
                                text = when (s) {
                                    VoiceAssistantState.LISTENING -> "Listening"
                                    VoiceAssistantState.THINKING -> "Working"
                                    VoiceAssistantState.SPEAKING -> "Speaking"
                                    VoiceAssistantState.RESULT -> "Result"
                                    else -> "Chitti"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMid
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state == VoiceAssistantState.SPEAKING) {
                            IconButton(onClick = onStopSpeech, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.VolumeMute, contentDescription = "Stop speaking", tint = Rose, modifier = Modifier.size(19.dp))
                            }
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextLow, modifier = Modifier.size(19.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Speech Transcript / Response Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 70.dp, max = 200.dp)
                        .animateContentSize(ChittiMotion.settle()),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        state == VoiceAssistantState.LISTENING -> {
                            Text(
                                text = if (transcript.isNotBlank()) "\"$transcript\"" else "Say \"Open WhatsApp\", \"Remind me to call mom in 30 minutes\", or \"What's pending?\"",
                                style = if (transcript.length > 35) MaterialTheme.typography.titleLarge
                                    else MaterialTheme.typography.headlineMedium,
                                color = if (transcript.isNotBlank()) TextHigh else TextLow,
                                textAlign = TextAlign.Center
                            )
                        }
                        state == VoiceAssistantState.THINKING -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                LatticeLoader(
                                    status = LatticeStatus.WORKING,
                                    label = "Working on it",
                                    pattern = LatticePatterns.Orbit,
                                    color = Accent,
                                    cellSize = 8.dp,
                                    gap = 3.dp,
                                    fontSize = 15
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "\"$transcript\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMid,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        assistantResponse != null -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (assistantResponse.actionLabel != null) {
                                    val ok = assistantResponse.actionSuccess
                                    StatusPill(
                                        text = assistantResponse.actionLabel,
                                        tint = if (ok) Mint else Amber,
                                        icon = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Info,
                                        modifier = Modifier.padding(bottom = 10.dp)
                                    )
                                }

                                Text(
                                    text = assistantResponse.message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextHigh,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Dynamic Animated Waveform & Glowing Mic Orb
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedVisibility(
                        visible = state == VoiceAssistantState.LISTENING,
                        enter = fadeIn(tween(150)) + expandVertically(ChittiMotion.settle()),
                        exit = fadeOut(tween(120)) + shrinkVertically(tween(160))
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AudioWaveformVisualizer(rmsLevel = rmsLevel)
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }
                    GeminiPulsingOrb(state = state, onClick = onMicClick)
                }

                Spacer(modifier = Modifier.height(6.dp))

                }
            }
        }
    }
}

@Composable
fun AudioWaveformVisualizer(rmsLevel: Float) {
    val barCount = 7
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(56.dp)
    ) {
        for (i in 0 until barCount) {
            val phaseOffset = (i * 100)
            val animatedFactor by infiniteTransition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 350 + (i * 50), delayMillis = phaseOffset, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar$i"
            )

            val rawHeight = (16.dp + (56 * rmsLevel * animatedFactor).dp).coerceIn(8.dp, 64.dp)
            // Springs: the bars follow the mic level continuously and can reverse mid-motion.
            val barHeight by animateDpAsState(
                targetValue = rawHeight,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                label = "barHeight$i"
            )

            // Centre bars read brightest, so the shape of the level is legible at a glance.
            val emphasis = 1f - (kotlin.math.abs(i - (barCount - 1) / 2f) / barCount)
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(barHeight)
                    .clip(CircleShape)
                    .background(Accent.copy(alpha = 0.45f + 0.55f * emphasis))
            )
        }
    }
}

@Composable
fun GeminiPulsingOrb(state: VoiceAssistantState, onClick: () -> Unit) {
    val reduceMotion = rememberReducedMotion()
    val infiniteTransition = rememberInfiniteTransition(label = "orb")

    // Outer ripple ring: breathes while listening/speaking, rests otherwise.
    val ripplePeak = when {
        reduceMotion -> 1f
        state == VoiceAssistantState.SPEAKING -> 1.35f
        state == VoiceAssistantState.LISTENING -> 1.15f
        else -> 1.06f
    }
    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = ripplePeak,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ripple"
    )

    // Inner glow ring
    val innerScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (reduceMotion) 1f else 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "inner"
    )

    val interaction = remember { MutableInteractionSource() }

    Box(contentAlignment = Alignment.Center) {
        // A single accent halo that breathes only while the mic or speech is live.
        Box(
            modifier = Modifier
                .size(78.dp)
                .scale(rippleScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Accent.copy(alpha = 0.30f), Accent.copy(alpha = 0.08f), Color.Transparent)
                    )
                )
        )

        // Main interactive mic button: feedback on press-down, springs back on release.
        Surface(
            modifier = Modifier
                .size(62.dp)
                .pressScale(interaction, pressed = 0.9f)
                .clip(CircleShape)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            shape = CircleShape,
            color = if (state == VoiceAssistantState.LISTENING) Accent else Surface2,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (state == VoiceAssistantState.LISTENING) Accent else Hairline)
        ) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = { (fadeIn(tween(150)) + scaleIn(initialScale = 0.8f)) togetherWith fadeOut(tween(100)) },
                    label = "micIcon"
                ) { s ->
                    Icon(
                        imageVector = when (s) {
                            VoiceAssistantState.SPEAKING -> Icons.Filled.GraphicEq
                            VoiceAssistantState.THINKING -> Icons.Filled.AutoAwesome
                            else -> Icons.Filled.Mic
                        },
                        contentDescription = "Microphone",
                        tint = if (state == VoiceAssistantState.LISTENING) Color.White else TextHigh,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }
        }
    }
}
