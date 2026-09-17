package com.owlcoders.chitti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.db.CapturedEvent

@Composable
fun DashboardScreen(events: List<CapturedEvent>) {
    val total = events.size
    val work = events.count { it.category == "Work" }
    val personal = events.count { it.category == "Personal" }
    val academic = events.count { it.category == "Academic" }
    
    val highUrgency = events.count { it.urgency == "High" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp)
    ) {
        Text(
            text = "Chitti Dashboard",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp, top = 24.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Total Memory Captured", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                Text(text = "$total events", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Category Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                CategoryStat("Work", work, total, Color(0xFF2196F3))
                CategoryStat("Personal", personal, total, Color(0xFF4CAF50))
                CategoryStat("Academic", academic, total, Color(0xFFFFC107))
            }
        }
        
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "High Urgency Tasks", style = MaterialTheme.typography.titleMedium, color = Color.Red, fontWeight = FontWeight.Bold)
                Text(text = "$highUrgency", style = MaterialTheme.typography.headlineMedium, color = Color.Red, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CategoryStat(name: String, count: Int, total: Int, color: Color) {
    val percentage = if (total > 0) count.toFloat() / total else 0f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Text(text = name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        LinearProgressIndicator(
            progress = percentage,
            modifier = Modifier.weight(2f).height(8.dp),
            color = color,
            trackColor = Color(0xFFE0E0E0),
        )
        Text(text = "$count", modifier = Modifier.weight(0.5f).padding(start = 8.dp), fontWeight = FontWeight.Bold)
    }
}
