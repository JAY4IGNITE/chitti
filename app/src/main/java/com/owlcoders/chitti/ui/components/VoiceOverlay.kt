package com.owlcoders.chitti.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.ui.theme.ActionRed
import com.owlcoders.chitti.ui.theme.OverlayScrim

enum class VoiceState {
    IDLE, LISTENING, THINKING, SPEAKING
}

@Composable
fun VoiceOverlay(
    state: VoiceState,
    transcript: String,
    modifier: Modifier = Modifier
) {
    if (state == VoiceState.IDLE) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OverlayScrim),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Animated indicator based on state
            VoiceIndicator(state = state)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = transcript,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun VoiceIndicator(state: VoiceState) {
    val infiniteTransition = rememberInfiniteTransition()
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == VoiceState.LISTENING) 1.5f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .size(80.dp)
            .scale(scale)
            .background(
                color = when (state) {
                    VoiceState.LISTENING -> ActionRed
                    VoiceState.THINKING -> Color.Gray
                    VoiceState.SPEAKING -> Color.Green
                    else -> Color.Transparent
                },
                shape = CircleShape
            )
    )
}
