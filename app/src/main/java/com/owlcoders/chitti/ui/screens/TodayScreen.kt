package com.owlcoders.chitti.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owlcoders.chitti.db.CapturedEvent
import com.owlcoders.chitti.ui.components.ChittiCard
import com.owlcoders.chitti.ui.components.ChittiMotion
import com.owlcoders.chitti.ui.components.pressScale
import com.owlcoders.chitti.ui.theme.*
import java.util.Calendar

private const val AURA_BREATH_CYCLES = 3

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TodayScreen(
    events: List<CapturedEvent>,
    onDeleteEvent: (CapturedEvent) -> Unit = {},
    onQuickAction: (String) -> Unit = {}
) {
    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        currentHour < 12 -> "Good morning"
        currentHour < 17 -> "Good afternoon"
        else -> "Good evening"
    }

    // Decorative "breathing" aura. Previously an infinite transition that kept the
    // frame clock busy for as long as the Home tab was visible; now it plays a few
    // cycles on entry and settles, so the UI can reach an idle state.
    val ambientAuraScale = remember { Animatable(1.0f) }
    LaunchedEffect(Unit) {
        repeat(AURA_BREATH_CYCLES) {
            ambientAuraScale.animateTo(1.08f, tween(1500, easing = FastOutSlowInEasing))
            ambientAuraScale.animateTo(1.0f, tween(1500, easing = FastOutSlowInEasing))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GeminiDarkBg)
    ) {
        // Assistant Hero Banner with Animated Ambient Glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            GeminiBlue.copy(alpha = 0.18f),
                            GeminiPurple.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Ambient breathing glow behind avatar
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .scale(ambientAuraScale.value)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(GeminiCyan.copy(alpha = 0.4f), Color.Transparent)
                                    )
                                )
                        )

                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(GeminiCyan, GeminiBlue, GeminiPurple)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "$greeting ✨",
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp),
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (events.isNotEmpty()) "${events.size} items on your agenda" else "You're all caught up for today",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Assistant Quick Suggestion Chips
                Text(
                    text = "Quick Assistant Actions",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChipItem(
                        icon = Icons.Filled.Forum,
                        label = "Open WhatsApp",
                        tint = GeminiGreen,
                        onClick = { onQuickAction("open whatsapp") }
                    )
                    SuggestionChipItem(
                        icon = Icons.Filled.PlayCircle,
                        label = "Open YouTube",
                        tint = GeminiPink,
                        onClick = { onQuickAction("open youtube") }
                    )
                    SuggestionChipItem(
                        icon = Icons.Filled.Alarm,
                        label = "Remind me in 10 min",
                        tint = GeminiCyan,
                        onClick = { onQuickAction("remind me to check my tasks in 10 minutes") }
                    )
                    SuggestionChipItem(
                        icon = Icons.Filled.FlashlightOn,
                        label = "Toggle Flashlight",
                        tint = GeminiAmber,
                        onClick = { onQuickAction("toggle flashlight") }
                    )
                    SuggestionChipItem(
                        icon = Icons.Filled.TaskAlt,
                        label = "What's Pending?",
                        tint = GeminiBlue,
                        onClick = { onQuickAction("what's pending") }
                    )
                }
            }
        }

        // Commitments / Agenda List
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Today's Agenda",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GeminiSurfaceElevated
                ) {
                    Text(
                        text = "${events.size} Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = GeminiCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (events.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            color = GeminiSurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, GeminiBorder)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.DoneAll,
                                    contentDescription = null,
                                    tint = GeminiCyan,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No pending tasks or deadlines",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap the glowing mic anytime to speak or say \"Open WhatsApp\", \"Remind me to call mom in 30 minutes\", or \"What's pending?\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(events, key = { it.id }) { event ->
                        // Cards glide into their new slot when one is deleted (spatial consistency).
                        ChittiCard(
                            event = event,
                            onDelete = { onDeleteEvent(event) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItemPlacement(ChittiMotion.settle())
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestionChipItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .pressScale(interaction, pressed = 0.94f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .border(1.dp, GeminiBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = GeminiSurfaceElevated,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}
