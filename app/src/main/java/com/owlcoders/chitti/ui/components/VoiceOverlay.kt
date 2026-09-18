package com.owlcoders.chitti.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owlcoders.chitti.automation.AssistantResponse
import com.owlcoders.chitti.ui.theme.*

enum class VoiceAssistantState {
    IDLE, LISTENING, THINKING, SPEAKING, RESULT
}

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
    AnimatedVisibility(
        visible = state != VoiceAssistantState.IDLE,
        enter = fadeIn(tween(250)) + slideInVertically(
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
            initialOffsetY = { it }
        ),
        exit = fadeOut(tween(200)) + slideOutVertically(
            animationSpec = tween(200, easing = FastOutLinearInEasing),
            targetOffsetY = { it }
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Top scrim dismiss area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )

            // Bottom sheet: consumes clicks so inner interactions do not bubble to onDismiss
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { /* consume */ }
                    )
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(GeminiDarkBg)
                    .border(1.dp, GeminiBorder.copy(alpha = 0.6f), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(TextMuted.copy(alpha = 0.4f))
                )

                Spacer(modifier = Modifier.height(16.dp))

                // State & Assistant Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when (state) {
                                        VoiceAssistantState.LISTENING -> GeminiCyan
                                        VoiceAssistantState.THINKING -> GeminiAmber
                                        VoiceAssistantState.SPEAKING -> GeminiGreen
                                        else -> GeminiBlue
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (state) {
                                VoiceAssistantState.LISTENING -> "Listening..."
                                VoiceAssistantState.THINKING -> "Thinking..."
                                VoiceAssistantState.SPEAKING -> "Chitti is speaking..."
                                VoiceAssistantState.RESULT -> "Assistant Action"
                                else -> "Assistant"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state == VoiceAssistantState.SPEAKING) {
                            IconButton(onClick = onStopSpeech, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.VolumeMute, contentDescription = "Mute", tint = GeminiPink)
                            }
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextMuted)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Speech Transcript / Response Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 70.dp, max = 180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        state == VoiceAssistantState.LISTENING -> {
                            Text(
                                text = if (transcript.isNotBlank()) "\"$transcript\"" else "Say \"Open WhatsApp\", \"Open YouTube\", or \"Find documents\"...",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = if (transcript.length > 35) 18.sp else 22.sp,
                                    lineHeight = 28.sp
                                ),
                                color = if (transcript.isNotBlank()) TextPrimary else TextMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                        state == VoiceAssistantState.THINKING -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GeminiCircularProgressIndicator(
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Executing \"$transcript\"...",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                            }
                        }
                        assistantResponse != null -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (assistantResponse.actionLabel != null) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = GeminiGreen.copy(alpha = 0.15f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, GeminiGreen.copy(alpha = 0.3f)),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = GeminiGreen, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(assistantResponse.actionLabel, color = GeminiGreen, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Text(
                                    text = assistantResponse.message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                // If matching files or documents returned
                                if (assistantResponse.matchingFiles.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        assistantResponse.matchingFiles.take(2).forEach { file ->
                                            Surface(
                                                modifier = Modifier
                                                    .padding(horizontal = 4.dp)
                                                    .clickable { onDocumentClick(file.uriString) },
                                                shape = RoundedCornerShape(12.dp),
                                                color = GeminiSurfaceElevated,
                                                border = androidx.compose.foundation.BorderStroke(1.dp, GeminiBorder)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = GeminiCyan, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(file.name, color = TextPrimary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                        }
                                    }
                                } else if (assistantResponse.matchingDocuments.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        assistantResponse.matchingDocuments.take(2).forEach { doc ->
                                            Surface(
                                                modifier = Modifier
                                                    .padding(horizontal = 4.dp)
                                                    .clickable { onDocumentClick(doc.uriString) },
                                                shape = RoundedCornerShape(12.dp),
                                                color = GeminiSurfaceElevated,
                                                border = androidx.compose.foundation.BorderStroke(1.dp, GeminiBorder)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Filled.Description, contentDescription = null, tint = GeminiCyan, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(doc.title, color = TextPrimary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                        }
                                    }
                                }
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
                    if (state == VoiceAssistantState.LISTENING) {
                        AudioWaveformVisualizer(rmsLevel = rmsLevel)
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                    GeminiPulsingOrb(state = state, onClick = onMicClick)
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Gemini Glowing Iridescent Bottom Lightbar
                GeminiIridescentLightbar()
            }
        }
    }
}

@Composable
fun AudioWaveformVisualizer(rmsLevel: Float) {
    val barCount = 7
    val infiniteTransition = rememberInfiniteTransition()

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(64.dp)
    ) {
        for (i in 0 until barCount) {
            val phaseOffset = (i * 100)
            val animatedFactor by infiniteTransition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 350 + (i * 50), delayMillis = phaseOffset, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )

            val rawHeight = (12.dp + (44 * rmsLevel * animatedFactor).dp).coerceIn(8.dp, 58.dp)
            val barHeight by animateDpAsState(
                targetValue = rawHeight,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            )

            val barColor = when (i % 4) {
                0 -> GeminiBlue
                1 -> GeminiCyan
                2 -> GeminiPurple
                else -> GeminiPink
            }

            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(barHeight)
                    .clip(CircleShape)
                    .background(barColor)
            )
        }
    }
}

@Composable
fun GeminiPulsingOrb(state: VoiceAssistantState, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition()
    
    // Outer ripple ring
    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == VoiceAssistantState.SPEAKING) 1.35f else 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Inner glow ring
    val innerScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer ambient glow ring
        Box(
            modifier = Modifier
                .size(76.dp)
                .scale(rippleScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            GeminiCyan.copy(alpha = 0.4f),
                            GeminiBlue.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Inner harmonic glow
        Box(
            modifier = Modifier
                .size(68.dp)
                .scale(innerScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            GeminiPurple.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Main interactive mic button
        Surface(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            shape = CircleShape,
            color = GeminiSurfaceElevated,
            border = androidx.compose.foundation.BorderStroke(2.dp, GeminiGradient),
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when (state) {
                        VoiceAssistantState.SPEAKING -> Icons.Filled.GraphicEq
                        VoiceAssistantState.THINKING -> Icons.Filled.AutoAwesome
                        else -> Icons.Filled.Mic
                    },
                    contentDescription = "Assistant Mic",
                    tint = GeminiCyan,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun GeminiIridescentLightbar() {
    val infiniteTransition = rememberInfiniteTransition()
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.5.dp)
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        GeminiCyan,
                        GeminiBlue,
                        GeminiPurple,
                        GeminiPink,
                        GeminiCyan
                    ),
                    startX = gradientOffset,
                    endX = gradientOffset + 500f
                )
            )
    )
}
