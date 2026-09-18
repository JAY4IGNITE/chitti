package com.owlcoders.chitti.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owlcoders.chitti.ChittiApp
import com.owlcoders.chitti.db.CapturedEvent
import com.owlcoders.chitti.ui.theme.*
import kotlinx.coroutines.launch

/**
 * The Clock app's ACTION_SET_ALARM / ACTION_SET_TIMER handler is protected by the
 * normal permission com.android.alarm.permission.SET_ALARM. It is granted at install
 * only if the manifest declares it; without it startActivity throws SecurityException.
 */
private const val SET_ALARM_PERMISSION = "com.android.alarm.permission.SET_ALARM"

private fun hasSetAlarmPermission(context: Context): Boolean =
    context.checkSelfPermission(SET_ALARM_PERMISSION) == PackageManager.PERMISSION_GRANTED

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/** Try the alarm app first, fall back to a timer, and surface any failure to the user. */
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
        toast(context, "Reminders need the alarm permission (SET_ALARM)")
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
        toast(context, "Reminders need the alarm permission (SET_ALARM)")
    }
}

@Composable
fun ChittiCard(event: CapturedEvent, modifier: Modifier = Modifier, onDelete: () -> Unit = {}) {
    var generatedReply by remember { mutableStateOf<String?>(null) }
    var isGeneratingReply by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val categoryColor = when (event.category) {
        "Work" -> GeminiCyan
        "Academic" -> GeminiPurple
        else -> GeminiBlue
    }

    val urgencyColor = when (event.urgency) {
        "High" -> GeminiRed
        "Medium" -> GeminiAmber
        else -> GeminiGreen
    }

    Card(
        modifier = modifier
            .padding(vertical = 6.dp)
            .border(1.dp, GeminiBorder.copy(alpha = 0.8f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GeminiSurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header: Category & Urgency Badges + Delete Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Category pill
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = categoryColor.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, categoryColor.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = event.category ?: "Personal",
                            style = MaterialTheme.typography.labelSmall,
                            color = categoryColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Urgency indicator
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = urgencyColor.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, urgencyColor.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "${event.urgency ?: "Normal"} Urgency",
                            style = MaterialTheme.typography.labelSmall,
                            color = urgencyColor,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Task Title
            Text(
                text = event.extractedWhat ?: "Captured Task",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp),
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Details: When, Who, Source
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = GeminiCyan, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = event.extractedWhen ?: "Pending",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                if (!event.extractedWho.isNullOrBlank() && event.extractedWho != "Self") {
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(Icons.Filled.Person, contentDescription = null, tint = GeminiPurple, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = event.extractedWho,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Smart Reply
                OutlinedButton(
                    onClick = {
                        if (isGeneratingReply) return@OutlinedButton
                        isGeneratingReply = true
                        // generateSmartReply is a suspend fun that switches to IO internally;
                        // keep this on the composable scope, never runBlocking.
                        scope.launch {
                            try {
                                val engine = (context.applicationContext as ChittiApp).extractionEngine
                                generatedReply = engine?.generateSmartReply(event) ?: "Got it! I will take care of it."
                            } catch (e: Exception) {
                                toast(context, "Could not draft a reply")
                            } finally {
                                isGeneratingReply = false
                            }
                        }
                    },
                    enabled = !isGeneratingReply,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GeminiCyan),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GeminiCyan.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isGeneratingReply) "Thinking..." else "Reply", style = MaterialTheme.typography.labelSmall)
                }

                // Calendar
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_INSERT).apply {
                            data = CalendarContract.Events.CONTENT_URI
                            putExtra(CalendarContract.Events.TITLE, event.extractedWhat)
                            putExtra(CalendarContract.Events.DESCRIPTION, "Source: ${event.sourceApp}\nDetails: ${event.rawText}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            toast(context, "No calendar app found")
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Filled.Event, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Calendar", style = MaterialTheme.typography.labelSmall)
                }

                // Reminder / Alarm
                FilledTonalButton(
                    onClick = { launchReminder(context, event) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = GeminiSurfaceElevated,
                        contentColor = TextPrimary,
                        disabledContainerColor = GeminiSurfaceElevated.copy(alpha = 0.5f),
                        disabledContentColor = TextMuted
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remind", style = MaterialTheme.typography.labelSmall)
                }
            }


            // Smart Reply Output Card
            if (generatedReply != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = GeminiSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GeminiBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = GeminiCyan, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Drafted Smart Reply:", style = MaterialTheme.typography.labelSmall, color = GeminiCyan)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = generatedReply!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, generatedReply)
                                        type = "text/plain"
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(sendIntent, "Send Smart Reply"))
                                    } catch (e: ActivityNotFoundException) {
                                        toast(context, "No app available to send the reply")
                                    }
                                }
                            ) {
                                Text("Send via App", color = GeminiCyan, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}