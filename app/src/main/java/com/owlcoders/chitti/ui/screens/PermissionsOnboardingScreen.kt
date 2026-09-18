package com.owlcoders.chitti.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.owlcoders.chitti.services.ChittiAccessibilityService
import com.owlcoders.chitti.ui.theme.*

@Composable
fun PermissionsOnboardingScreen(
    onAllPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    
    var hasMic by remember { mutableStateOf(checkMicPermission(context)) }
    var hasNotification by remember { mutableStateOf(checkNotificationPermission(context)) }
    var hasAccessibility by remember { mutableStateOf(checkAccessibilityPermission()) }
    // Autofill is optional but good to prompt, let's treat it as required for the full experience in this screen, 
    // or just strongly recommended. For simplicity, we can make it a checklist.

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasMic = it
        if (hasMic && hasNotification && hasAccessibility) {
            onAllPermissionsGranted()
        }
    }

    LaunchedEffect(hasMic, hasNotification, hasAccessibility) {
        if (hasMic && hasNotification && hasAccessibility) {
            onAllPermissionsGranted()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = GeminiDarkBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(GeminiCyan, GeminiBlue))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Welcome to Chitti", style = MaterialTheme.typography.headlineMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("To act as your powerful AI assistant, Chitti needs the following permissions.", 
                style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            PermissionItem(
                title = "Microphone",
                description = "To hear your voice commands.",
                icon = Icons.Filled.Mic,
                isGranted = hasMic,
                onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )
            
            PermissionItem(
                title = "Notification Access",
                description = "To organize your tasks from incoming messages.",
                icon = Icons.Filled.Notifications,
                isGranted = hasNotification,
                onClick = { 
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            )
            
            PermissionItem(
                title = "Accessibility Service",
                description = "To perform typing and clicks on your behalf.",
                icon = Icons.Filled.Accessibility,
                isGranted = hasAccessibility,
                onClick = { 
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )

            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = { 
                    // Re-check all permissions
                    hasMic = checkMicPermission(context)
                    hasNotification = checkNotificationPermission(context)
                    hasAccessibility = checkAccessibilityPermission()
                    
                    if (hasMic && hasNotification && hasAccessibility) {
                        onAllPermissionsGranted()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GeminiBlue),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Refresh & Continue", fontSize = MaterialTheme.typography.titleMedium.fontSize)
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            // Allow bypassing for testing purposes
            TextButton(onClick = onAllPermissionsGranted) {
                Text("Skip (Not Recommended)", color = TextMuted)
            }
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = GeminiSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isGranted) GeminiGreen.copy(alpha=0.5f) else GeminiBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) GeminiGreen.copy(alpha=0.2f) else GeminiCyan.copy(alpha=0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (isGranted) GeminiGreen else GeminiCyan)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            if (isGranted) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Granted", tint = GeminiGreen, modifier = Modifier.size(28.dp))
            } else {
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = GeminiCyan),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Grant", fontSize = MaterialTheme.typography.labelSmall.fontSize)
                }
            }
        }
    }
}

fun checkMicPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}

fun checkNotificationPermission(context: Context): Boolean {
    val packageNames = NotificationManagerCompat.getEnabledListenerPackages(context)
    return packageNames.contains(context.packageName)
}

fun checkAccessibilityPermission(): Boolean {
    // A reliable way to check if our specific service is enabled is simply to check the singleton.
    // If it's connected, it's enabled.
    return ChittiAccessibilityService.instance != null
}
