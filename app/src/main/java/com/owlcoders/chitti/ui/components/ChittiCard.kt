package com.owlcoders.chitti.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.ChittiApp
import com.owlcoders.chitti.db.CapturedEvent
import com.owlcoders.chitti.ui.theme.*
import kotlinx.coroutines.launch

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/**
 * Hands the task to the clock app. ACTION_SET_ALARM is gated by the Clock app's own
 * com.android.alarm.permission.SET_ALARM (declared in our manifest); if the device's clock does
 * not publish it, or no clock resolves the action, we fall back to a timer and then to a message.
 */
private fun launchReminder(context: Context, event: CapturedEvent) {
    val title = event.extractedWhat ?: "Chitti reminder"
    val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
        putExtra(AlarmClock.EXTRA_MESSAGE, title)
        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(alarmIntent)
        return
    } catch (e: ActivityNotFoundException) {
        // fall through to timer
    } catch (e: SecurityException) {
        toast(context, "Reminders need the alarm permission")
        return
    }
    val timerIntent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
        putExtra(AlarmClock.EXTRA_MESSAGE, title)
        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(timerIntent)
    } catch (e: ActivityNotFoundException) {
        toast(context, "No clock app found to set a reminder")
    } catch (e: SecurityException) {
        toast(context, "Reminders need the alarm permission")
    }
}

/** Compact action used inside a card, where the 44dp kit button is too heavy for a row of three. */
@Composable
private fun CardAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = TextHigh,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = modifier
            .height(34.dp)
            .pressScale(interaction, pressed = 0.96f)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface2)
            .border(1.dp, Hairline, RoundedCornerShape(10.dp))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint.copy(alpha = alpha), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = tint.copy(alpha = alpha),
            maxLines = 1
        )
    }
}

/**
 * One captured commitment. Reads top-down: what it is, when it is due and where it came from,
 * then the three things you can do with it.
 */
@Composable
fun ChittiCard(event: CapturedEvent, modifier: Modifier = Modifier, onDelete: () -> Unit = {}) {
    var generatedReply by remember { mutableStateOf<String?>(null) }
    var isGeneratingReply by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val category = event.category ?: "Personal"
    val catColor = categoryColor(category)
    val urgColor = urgencyColor(event.urgency)
    val source = event.sourceApp.substringAfterLast('.').replaceFirstChar { it.uppercase() }

    ChittiSurfaceCard(modifier = modifier, accent = catColor) {
        // Meta line: urgency dot, category, source, dismiss
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Dot(urgColor)
            Spacer(Modifier.width(Space.s))
            Text(
                text = category,
                style = MaterialTheme.typography.labelMedium,
                color = catColor
            )
            Text(
                text = "  ·  $source",
                style = MaterialTheme.typography.labelMedium,
                color = TextLow,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDelete
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = TextLow, modifier = Modifier.size(15.dp))
            }
        }

        Spacer(Modifier.height(Space.m))

        Text(
            text = event.extractedWhat ?: "Captured task",
            style = MaterialTheme.typography.titleLarge,
            color = TextHigh
        )

        Spacer(Modifier.height(Space.s))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Schedule, contentDescription = null, tint = TextLow, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = event.extractedWhen?.takeIf { it.isNotBlank() } ?: "No time set",
                style = MaterialTheme.typography.bodySmall,
                color = TextMid
            )
            val who = event.extractedWho
            if (!who.isNullOrBlank() && !who.equals("self", ignoreCase = true)) {
                Spacer(Modifier.width(Space.m))
                Icon(Icons.Filled.Person, contentDescription = null, tint = TextLow, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = who,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(Space.l))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.s)
        ) {
            CardAction(
                label = if (isGeneratingReply) "Drafting" else "Reply",
                icon = Icons.Filled.AutoAwesome,
                tint = Accent,
                enabled = !isGeneratingReply,
                modifier = Modifier.weight(1f),
                onClick = {
                    isGeneratingReply = true
                    // generateSmartReply is a suspend fun that switches to IO internally.
                    scope.launch {
                        try {
                            val engine = (context.applicationContext as ChittiApp).extractionEngine
                            generatedReply = engine?.generateSmartReply(event) ?: "Got it, I will take care of it."
                        } catch (e: Exception) {
                            toast(context, "Could not draft a reply")
                        } finally {
                            isGeneratingReply = false
                        }
                    }
                }
            )
            CardAction(
                label = "Calendar",
                icon = Icons.Filled.Event,
                modifier = Modifier.weight(1f),
                onClick = {
                    val intent = Intent(Intent.ACTION_INSERT).apply {
                        data = CalendarContract.Events.CONTENT_URI
                        putExtra(CalendarContract.Events.TITLE, event.extractedWhat)
                        putExtra(CalendarContract.Events.DESCRIPTION, "From ${event.sourceApp}\n${event.rawText}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        toast(context, "No calendar app found")
                    }
                }
            )
            CardAction(
                label = "Remind",
                icon = Icons.Filled.NotificationsActive,
                modifier = Modifier.weight(1f),
                onClick = { launchReminder(context, event) }
            )
        }

        AnimatedVisibility(
            visible = isGeneratingReply || generatedReply != null,
            enter = fadeIn(tween(160)) + expandVertically(ChittiMotion.settle()),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(160))
        ) {
            Column {
                Spacer(Modifier.height(Space.m))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface2)
                        .border(1.dp, Hairline, RoundedCornerShape(12.dp))
                        .padding(Space.m)
                ) {
                    if (isGeneratingReply) {
                        LatticeLoader(
                            status = LatticeStatus.WORKING,
                            label = "Drafting a reply",
                            color = Accent,
                            fontSize = 13
                        )
                    } else {
                        Column {
                            Text(
                                text = "Suggested reply",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextLow
                            )
                            Spacer(Modifier.height(Space.xs))
                            Text(
                                text = generatedReply.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextHigh
                            )
                            Spacer(Modifier.height(Space.m))
                            CardAction(
                                label = "Send",
                                icon = Icons.Filled.Event,
                                tint = Accent,
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, generatedReply)
                                        type = "text/plain"
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(sendIntent, "Send reply"))
                                    } catch (e: ActivityNotFoundException) {
                                        toast(context, "No app available to send the reply")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
