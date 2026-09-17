package com.owlcoders.chitti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.db.entities.AutomationHistory
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AutomationScreen(
    history: List<AutomationHistory>
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF00695C))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "⚡ Automation Log",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = "${history.size} actions executed",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Stats bar
        val successCount = history.count { it.result.startsWith("success") }
        val failCount = history.count { it.result.startsWith("failed") }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatChip(
                label = "Success",
                count = successCount,
                color = Color(0xFF4CAF50),
                modifier = Modifier.weight(1f)
            )
            StatChip(
                label = "Failed",
                count = failCount,
                color = Color(0xFFF44336),
                modifier = Modifier.weight(1f)
            )
            StatChip(
                label = "Confirmed",
                count = history.count { it.userConfirmed },
                color = Color(0xFF2196F3),
                modifier = Modifier.weight(1f)
            )
        }

        if (history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No actions executed yet", color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
                    Text("Chitti will log all automated actions here", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history) { item ->
                    AutomationHistoryCard(item, dateFormat)
                }
            }
        }
    }
}

@Composable
fun StatChip(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
        }
    }
}

@Composable
fun AutomationHistoryCard(item: AutomationHistory, dateFormat: SimpleDateFormat) {
    val isSuccess = item.result.startsWith("success")
    val iconTint = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFF44336)
    val icon = if (isSuccess) Icons.Filled.CheckCircle else Icons.Filled.Error

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.actionType.replace("_", " "),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.result,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    text = dateFormat.format(Date(item.executedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            if (item.userConfirmed) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF2196F3).copy(alpha = 0.1f)
                ) {
                    Text(
                        "Confirmed",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2196F3)
                    )
                }
            }
        }
    }
}
