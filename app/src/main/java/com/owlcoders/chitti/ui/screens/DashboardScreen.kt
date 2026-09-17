package com.owlcoders.chitti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.db.CapturedEvent
import com.owlcoders.chitti.db.entities.Task

@Composable
fun DashboardScreen(
    events: List<CapturedEvent>,
    tasks: List<Task> = emptyList(),
    notificationCount: Int = 0,
    memoryCount: Int = 0,
    documentCount: Int = 0,
    automationCount: Int = 0
) {
    val total = events.size
    val work = events.count { it.category == "Work" }
    val personal = events.count { it.category == "Personal" }
    val academic = events.count { it.category == "Academic" }
    val highUrgency = events.count { it.urgency == "High" }

    val pendingTasks = tasks.count { it.status == "pending" }
    val completedTasks = tasks.count { it.status == "done" }
    val highPriority = tasks.count { it.priority == 2 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF283593))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "📊 Chitti Dashboard",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = "Your AI memory at a glance",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Quick Stats Grid
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DashStatCard(
                title = "Events",
                value = "$total",
                icon = Icons.AutoMirrored.Filled.EventNote,
                color = Color(0xFF1565C0),
                modifier = Modifier.weight(1f)
            )
            DashStatCard(
                title = "Tasks",
                value = "${tasks.size}",
                icon = Icons.Filled.Task,
                color = Color(0xFF2E7D32),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DashStatCard(
                title = "Notifications",
                value = "$notificationCount",
                icon = Icons.Filled.Notifications,
                color = Color(0xFFE65100),
                modifier = Modifier.weight(1f)
            )
            DashStatCard(
                title = "Memories",
                value = "$memoryCount",
                icon = Icons.Filled.Psychology,
                color = Color(0xFF6A1B9A),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DashStatCard(
                title = "Documents",
                value = "$documentCount",
                icon = Icons.Filled.Description,
                color = Color(0xFF00838F),
                modifier = Modifier.weight(1f)
            )
            DashStatCard(
                title = "Actions",
                value = "$automationCount",
                icon = Icons.Filled.AutoAwesome,
                color = Color(0xFFC2185B),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Category Breakdown
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Category Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                CategoryStat("Work", work, total, Color(0xFF2196F3))
                CategoryStat("Personal", personal, total, Color(0xFF4CAF50))
                CategoryStat("Academic", academic, total, Color(0xFFFFC107))
            }
        }

        // Task Status
        if (tasks.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Task Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TaskStatusPill("Pending", pendingTasks, Color(0xFFFFA000))
                        TaskStatusPill("Done", completedTasks, Color(0xFF4CAF50))
                        TaskStatusPill("High ⚡", highPriority, Color(0xFFF44336))
                    }
                }
            }
        }

        // Urgency Alert
        if (highUrgency > 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "High Urgency Items",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Red,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$highUrgency items need your attention",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Daily Digest placeholder
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Today,
                        contentDescription = null,
                        tint = Color(0xFF1565C0),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Daily Digest",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1565C0)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "You have $total events tracked, $pendingTasks pending tasks, and $memoryCount memories stored. " +
                            if (highUrgency > 0) "$highUrgency high-urgency items need attention!" else "All looking good!",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun DashStatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = color.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
                Text(title, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

@Composable
fun TaskStatusPill(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = color.copy(alpha = 0.15f)
        ) {
            Text(
                "$count",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                fontWeight = FontWeight.Bold,
                color = color,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

@Composable
fun CategoryStat(name: String, count: Int, total: Int, color: Color) {
    val percentage = if (total > 0) count.toFloat() / total else 0f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(text = name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        LinearProgressIndicator(
            progress = percentage,
            modifier = Modifier
                .weight(2f)
                .height(8.dp),
            color = color,
            trackColor = Color(0xFFE0E0E0),
        )
        Text(
            text = "$count",
            modifier = Modifier
                .weight(0.5f)
                .padding(start = 8.dp),
            fontWeight = FontWeight.Bold
        )
    }
}
