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
import com.owlcoders.chitti.db.CapturedEvent
import com.owlcoders.chitti.ui.theme.ActionRed
import com.owlcoders.chitti.ui.theme.PaperWhite

@Composable
fun ChittiCard(event: CapturedEvent, modifier: Modifier = Modifier) {
    // Slight random rotation for the "sticky note" look
    val rotation = (event.id.hashCode() % 6) - 3f

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
        colors = CardDefaults.cardColors(containerColor = PaperWhite)
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
                    TextButton(onClick = { /* Dismiss */ }) {
                        Text("Dismiss", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { /* Add to Calendar */ },
                        colors = ButtonDefaults.buttonColors(containerColor = ActionRed)
                    ) {
                        Text("Add to Calendar")
                    }
                }
            }
        }
    }
}
