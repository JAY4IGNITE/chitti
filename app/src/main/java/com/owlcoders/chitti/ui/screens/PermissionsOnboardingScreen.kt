package com.owlcoders.chitti.ui.screens

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.owlcoders.chitti.services.ChittiAccessibilityService
import com.owlcoders.chitti.ui.theme.*

@Composable
fun PermissionsOnboardingScreen(
    onAllPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current

    var hasMic by remember { mutableStateOf(checkMicPermission(context)) }
    var hasNotification by remember { mutableStateOf(checkNotificationPermission(context)) }
    var hasAccessibility by remember { mutableStateOf(checkAccessibilityPermission(context)) }
    // Optional on this screen, but required on Android 13+ for reminders and LinkGuard alerts.
    var hasPostNotifications by remember { mutableStateOf(checkPostNotificationsPermission(context)) }

    fun refresh() {
        hasMic = checkMicPermission(context)
        hasNotification = checkNotificationPermission(context)
        hasAccessibility = checkAccessibilityPermission(context)
        hasPostNotifications = checkPostNotificationsPermission(context)
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasMic = it
    }
    val postNotificationsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPostNotifications = it
    }

    // Notification-listener and accessibility grants happen in system Settings; re-check when we
    // come back so the user does not have to press Refresh.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Ask for POST_NOTIFICATIONS once, right away, on Android 13+.
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !hasPostNotifications) {
            postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
                .verticalScroll(rememberScrollState())
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
            Text(
                "To act as your powerful AI assistant, Chitti needs the following permissions.",
                style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center
            )

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
                onClick = { openSettingsSafely(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
            )

            PermissionItem(
                title = "Accessibility Service",
                description = "To perform typing and clicks on your behalf.",
                icon = Icons.Filled.Accessibility,
                isGranted = hasAccessibility,
                onClick = { openSettingsSafely(context, Settings.ACTION_ACCESSIBILITY_SETTINGS) }
            )

            if (Build.VERSION.SDK_INT >= 33) {
                PermissionItem(
                    title = "Show Notifications",
                    description = "For reminders and suspicious-link alerts (optional).",
                    icon = Icons.Filled.NotificationsActive,
                    isGranted = hasPostNotifications,
                    onClick = { postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    refresh()
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
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun openSettingsSafely(context: Context, action: String) {
    try {
        context.startActivity(Intent(action))
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Exception) {
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
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isGranted) GeminiGreen.copy(alpha = 0.5f) else GeminiBorder)
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
                    .background(if (isGranted) GeminiGreen.copy(alpha = 0.2f) else GeminiCyan.copy(alpha = 0.2f)),
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

fun checkPostNotificationsPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

/**
 * Reads the system setting instead of the service singleton: the singleton is null until the
 * system (re)binds the service, which can lag app start by seconds (and ~11 s after a crash
 * restart), which made onboarding reappear on every cold start.
 */
fun checkAccessibilityPermission(context: Context): Boolean {
    val expected = ComponentName(context, ChittiAccessibilityService::class.java)
    val enabled = try {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    } catch (e: Exception) {
        null
    }.orEmpty()
    val listed = enabled.split(':').any { entry ->
        val parsed = ComponentName.unflattenFromString(entry)
        (parsed != null && parsed == expected) ||
            entry.equals(expected.flattenToString(), ignoreCase = true) ||
            entry.equals(expected.flattenToShortString(), ignoreCase = true)
    }
    return listed || ChittiAccessibilityService.instance != null
}
