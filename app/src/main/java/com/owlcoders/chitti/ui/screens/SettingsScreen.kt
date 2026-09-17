package com.owlcoders.chitti.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    hasNotificationAccess: Boolean,
    onWipeData: () -> Unit,
    // Privacy dashboard stats
    eventCount: Int = 0,
    taskCount: Int = 0,
    notificationCount: Int = 0,
    memoryCount: Int = 0,
    documentCount: Int = 0,
    chatMessageCount: Int = 0,
    automationHistoryCount: Int = 0,
    // Selective deletion callbacks
    onClearNotifications: () -> Unit = {},
    onClearChatHistory: () -> Unit = {},
    onClearAutomationHistory: () -> Unit = {},
    onClearMemories: () -> Unit = {}
) {
    val context = LocalContext.current
    var showWipeConfirmation by remember { mutableStateOf(false) }

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
                .background(Color(0xFF37474F))
                .padding(16.dp)
        ) {
            Text(
                text = "⚙️ Settings & Privacy",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // PERMISSIONS SECTION
        SectionHeader("Permissions")

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                PermissionRow(
                    name = "Notification Listener",
                    isGranted = hasNotificationAccess,
                    onRequest = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        context.startActivity(intent)
                    }
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                PermissionRow(
                    name = "Camera (OCR)",
                    isGranted = context.checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED,
                    onRequest = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                PermissionRow(
                    name = "Microphone (Voice)",
                    isGranted = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED,
                    onRequest = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                )
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                PermissionRow(
                    name = "Calendar",
                    isGranted = context.checkSelfPermission(android.Manifest.permission.WRITE_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED,
                    onRequest = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // PRIVACY DASHBOARD
        SectionHeader("Privacy Dashboard")

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Data Stored on Device", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(12.dp))

                DataStatRow("Captured Events", eventCount)
                DataStatRow("Tasks", taskCount)
                DataStatRow("Raw Notifications", notificationCount)
                DataStatRow("Memories", memoryCount)
                DataStatRow("Documents", documentCount)
                DataStatRow("Chat Messages", chatMessageCount)
                DataStatRow("Automation Logs", automationHistoryCount)

                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                val totalItems = eventCount + taskCount + notificationCount + memoryCount + documentCount + chatMessageCount + automationHistoryCount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Items", fontWeight = FontWeight.Bold)
                    Text("$totalItems", fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SELECTIVE DATA MANAGEMENT
        SectionHeader("Data Management")

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                DataDeleteRow("Clear Notifications", notificationCount, onClearNotifications)
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                DataDeleteRow("Clear Chat History", chatMessageCount, onClearChatHistory)
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                DataDeleteRow("Clear Automation Logs", automationHistoryCount, onClearAutomationHistory)
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                DataDeleteRow("Clear Memories", memoryCount, onClearMemories)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // NUCLEAR OPTION
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.Red)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Wipe All Data", fontWeight = FontWeight.Bold, color = Color.Red)
                }
                Text(
                    "This will delete ALL captured events, tasks, memories, notifications, and chat history. This cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Button(
                    onClick = { showWipeConfirmation = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear Everything")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // MODEL INFO
        SectionHeader("AI Models")

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ModelRow("LLM", "MediaPipe Gemma", "On-device")
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                ModelRow("OCR", "ML Kit Text Recognition", "On-device")
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                ModelRow("VAD", "Silero VAD (ONNX)", "On-device")
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                ModelRow("TTS", "Android TextToSpeech", "On-device")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Version
        Text(
            "Chitti v1.0 · Offline AI Assistant",
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Wipe confirmation dialog
    if (showWipeConfirmation) {
        AlertDialog(
            onDismissRequest = { showWipeConfirmation = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.Red) },
            title = { Text("Are you sure?") },
            text = { Text("This will permanently delete all data from Chitti. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onWipeData()
                        showWipeConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = Color.Gray,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun PermissionRow(name: String, isGranted: Boolean, onRequest: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (isGranted) Color(0xFF4CAF50) else Color(0xFFFFA000),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        if (!isGranted) {
            TextButton(onClick = onRequest) {
                Text("Grant", color = Color(0xFF1565C0))
            }
        } else {
            Text("Granted", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50))
        }
    }
}

@Composable
fun DataStatRow(label: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text("$count", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF424242))
    }
}

@Composable
fun DataDeleteRow(label: String, count: Int, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium)
            Text("$count items", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        TextButton(
            onClick = onDelete,
            enabled = count > 0
        ) {
            Text("Clear", color = if (count > 0) Color.Red else Color.Gray)
        }
    }
}

@Composable
fun ModelRow(type: String, name: String, location: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(type, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(name, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF4CAF50).copy(alpha = 0.1f)
        ) {
            Text(
                location,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF4CAF50)
            )
        }
    }
}
