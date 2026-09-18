package com.owlcoders.chitti.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.db.entities.NotificationEntity
import com.owlcoders.chitti.ui.theme.LightScreenBg
import com.owlcoders.chitti.ui.theme.LightScreenInk
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    notifications: List<NotificationEntity>,
    onMarkProcessed: (NotificationEntity) -> Unit = {},
    onDelete: (NotificationEntity) -> Unit = {}
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    var selectedFilter by remember { mutableStateOf("all") } // all, unprocessed, processed

    val filteredNotifications = when (selectedFilter) {
        "unprocessed" -> notifications.filter { !it.processed }
        "processed" -> notifications.filter { it.processed }
        else -> notifications
    }

    // Light screen: provide a dark content colour so Text/Icon without an explicit
    // colour are readable on the white cards (the theme's onSurface is near-white).
    CompositionLocalProvider(LocalContentColor provides LightScreenInk) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightScreenBg)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1565C0))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "📥 Notification Inbox",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = "${notifications.size} captured · ${notifications.count { !it.processed }} unprocessed",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Unselected chip labels default to onSurfaceVariant (light grey) which is
            // faint on the light background; use the light-screen ink instead.
            val chipColors = FilterChipDefaults.filterChipColors(labelColor = LightScreenInk)
            FilterChip(
                selected = selectedFilter == "all",
                onClick = { selectedFilter = "all" },
                colors = chipColors,
                label = { Text("All") }
            )
            FilterChip(
                selected = selectedFilter == "unprocessed",
                onClick = { selectedFilter = "unprocessed" },
                colors = chipColors,
                label = { Text("Unprocessed") }
            )
            FilterChip(
                selected = selectedFilter == "processed",
                onClick = { selectedFilter = "processed" },
                colors = chipColors,
                label = { Text("Processed") }
            )
        }

        if (filteredNotifications.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Inbox,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No notifications yet", color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Stable keys: NotificationCard keeps local `expanded` state, so
                // without keys that state would jump to whichever item slides into
                // the same position after a delete / filter change.
                items(filteredNotifications, key = { it.id }) { notification ->
                    NotificationCard(
                        notification = notification,
                        dateFormat = dateFormat,
                        onMarkProcessed = { onMarkProcessed(notification) },
                        onDelete = { onDelete(notification) }
                    )
                }
            }
        }
    }
    }
}

@Composable
fun NotificationCard(
    notification: NotificationEntity,
    dateFormat: SimpleDateFormat,
    onMarkProcessed: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val bgColor by animateColorAsState(
        if (notification.processed) Color(0xFFE8F5E9) else Color.White,
        label = "cardBg"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // App icon placeholder
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1565C0).copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = null,
                            tint = Color(0xFF1565C0),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = notification.rawTitle.ifEmpty { notification.packageName },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = dateFormat.format(Date(notification.postTime)),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                if (notification.processed) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Processed",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = notification.rawText,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (!notification.processed) {
                        TextButton(onClick = onMarkProcessed) {
                            Icon(Icons.Filled.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Process")
                        }
                    }
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Red)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", color = Color.Red)
                    }
                }
            }
        }
    }
}
