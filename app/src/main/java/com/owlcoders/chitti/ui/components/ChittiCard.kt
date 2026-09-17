package com.owlcoders.chitti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.owlcoders.chitti.ChittiApp
import com.owlcoders.chitti.db.CapturedEvent
import com.owlcoders.chitti.ui.theme.ActionRed
import com.owlcoders.chitti.ui.theme.PaperWhite
import kotlinx.coroutines.launch

@Composable
fun ChittiCard(event: CapturedEvent, onDelete: () -> Unit = {}, modifier: Modifier = Modifier) {
    var generatedReply by remember { mutableStateOf<String?>(null) }
    var isGeneratingReply by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Slight random rotation for the "sticky note" look
    val rotation = (event.id.hashCode() % 6) - 3f
    val context = LocalContext.current

    val containerColor = when {
        event.urgency == "High" -> Color(0xFFFFEBEE)
        event.category == "Work" -> Color(0xFFE3F2FD)
        event.category == "Academic" -> Color(0xFFE8F5E9)
        else -> Color(0xFFFFF9C4) // Classic Yellow Sticky Note
    }


        Card(
            modifier = modifier
                .padding(8.dp)
                .rotate(rotation)
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(2.dp),
                    spotColor = Color.Black.copy(alpha = 0.2f)
                ),
            shape = RoundedCornerShape(2.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            Box {
                // The Push Pin
                Text(
                    text = "📍",
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-10).dp),
                    style = MaterialTheme.typography.headlineMedium
                )
                
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Close,
                        contentDescription = "Delete Task",
                        tint = Color.Gray
                    )
                }
                
                Column(modifier = Modifier.padding(16.dp).padding(top = 8.dp)) {
                    if (event.status == "pending") {
                Text(
                    text = "Thinking...",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Gray
                )
            } else {
                Text(
                    text = event.extractedWhat ?: "Unknown Task",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(text = "When: ${event.extractedWhen ?: "Unknown"}")
                Text(text = "Who: ${event.extractedWho ?: "Unknown"}")
                Text(text = "Source: ${event.sourceApp}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            isGeneratingReply = true
                            scope.launch {
                                val engine = (context.applicationContext as ChittiApp).extractionEngine
                                generatedReply = engine?.generateSmartReply(event) ?: "Sure, I'll take care of it."
                                isGeneratingReply = false
                            }
                        },
                        enabled = !isGeneratingReply
                    ) {
                        Text(if (isGeneratingReply) "Thinking..." else "✨ Smart Reply", color = Color(0xFF673AB7), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_INSERT).apply {
                                data = CalendarContract.Events.CONTENT_URI
                                putExtra(CalendarContract.Events.TITLE, event.extractedWhat)
                                putExtra(CalendarContract.Events.DESCRIPTION, "Source: ${event.sourceApp}\nDetails: ${event.rawText}")
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ActionRed)
                    ) {
                        Text("Calendar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                                    putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, event.extractedWhat)
                                    putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    context.startActivity(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))
                                } catch (e2: Exception) {
                                    // Ignored if device has no alarm app
                                }
                            }
                        }
                    ) {
                        Text("Reminder")
                    }
                }

                if (generatedReply != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(12.dp)) {
                        Column {
                            Text("Drafted Reply:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text(generatedReply!!, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, generatedReply)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Send Smart Reply"))
                                }) {
                                    Text("Send")
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }
}