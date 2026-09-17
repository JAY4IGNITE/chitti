package com.owlcoders.chitti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import com.owlcoders.chitti.db.CapturedEvent
import com.owlcoders.chitti.ui.theme.ActionRed
import com.owlcoders.chitti.ui.theme.PaperWhite

@Composable
fun ChittiCard(event: CapturedEvent, modifier: Modifier = Modifier) {
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
        Column(modifier = Modifier.padding(16.dp)) {
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
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(event.sourceApp)
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                            }
                        }
                    ) {
                        Text("Reply", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_INSERT).apply {
                                data = CalendarContract.Events.CONTENT_URI
                                putExtra(CalendarContract.Events.TITLE, event.extractedWhat)
                                putExtra(CalendarContract.Events.DESCRIPTION, "Source: ${event.sourceApp}\nDetails: ${event.rawText}")
                                // We'd ideally parse event.extractedWhen to milliseconds here, but for the MVP, Calendar handles natural text poorly without parsing.
                                // We'll just pass the description so the user can set the time.
                            }
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ActionRed)
                    ) {
                        Text("Add to Calendar")
                    }
                }
            }
        }
    }
}
