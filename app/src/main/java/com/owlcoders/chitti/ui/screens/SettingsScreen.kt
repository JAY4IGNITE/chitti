package com.owlcoders.chitti.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.owlcoders.chitti.ui.components.ChittiSurfaceCard
import com.owlcoders.chitti.ui.components.CountUpText
import com.owlcoders.chitti.ui.components.DangerButton
import com.owlcoders.chitti.ui.components.HairlineDivider
import com.owlcoders.chitti.ui.components.ListRow
import com.owlcoders.chitti.ui.components.ScreenHeader
import com.owlcoders.chitti.ui.components.ScreenScaffold
import com.owlcoders.chitti.ui.components.SecondaryButton
import com.owlcoders.chitti.ui.components.SectionLabel
import com.owlcoders.chitti.ui.components.Space
import com.owlcoders.chitti.ui.components.StatusPill
import com.owlcoders.chitti.ui.components.staggeredEntrance
import com.owlcoders.chitti.ui.theme.Accent
import com.owlcoders.chitti.ui.theme.Amber
import com.owlcoders.chitti.ui.theme.Hairline
import com.owlcoders.chitti.ui.theme.Iris
import com.owlcoders.chitti.ui.theme.Mint
import com.owlcoders.chitti.ui.theme.Rose
import com.owlcoders.chitti.ui.theme.Sky
import com.owlcoders.chitti.ui.theme.Surface1
import com.owlcoders.chitti.ui.theme.TextHigh
import com.owlcoders.chitti.ui.theme.TextLow
import com.owlcoders.chitti.ui.theme.TextMid

@Composable
fun SettingsScreen(
    hasNotificationAccess: Boolean,
    onWipeData: () -> Unit,
    // Privacy dashboard stats
    eventCount: Int = 0,
    taskCount: Int = 0,
    notificationCount: Int = 0,
    memoryCount: Int = 0,
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

    // Permission state is re-evaluated every time the screen resumes (i.e. when the
    // user comes back from system Settings), instead of once at first composition.
    var permissionRefresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRefresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The caller's hasNotificationAccess is computed once at launch; check the
    // listener status ourselves on every resume so the row stays accurate.
    val notificationListenerEnabled = remember(permissionRefresh, hasNotificationAccess) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    val hasMicrophone = remember(permissionRefresh) {
        context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val hasCalendar = remember(permissionRefresh) {
        context.checkSelfPermission(android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    fun openAppInfo() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }
        try {
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(context, "Could not open app settings", Toast.LENGTH_SHORT).show()
        }
    }

    // Request runtime permissions directly. If the system will no longer show the
    // dialog ("don't ask again"), fall back to App Info so the user can still grant it.
    var requestedPermission by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRefresh++
        if (!granted) {
            val activity = context.findActivity()
            val permission = requestedPermission
            val permanentlyDenied = activity != null && permission != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            if (permanentlyDenied) {
                Toast.makeText(context, "Enable the permission under App permissions", Toast.LENGTH_SHORT).show()
                openAppInfo()
            } else {
                Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun requestPermission(permission: String) {
        requestedPermission = permission
        permissionLauncher.launch(permission)
    }

    val totalItems = eventCount + taskCount + notificationCount + memoryCount +
        chatMessageCount + automationHistoryCount

    ScreenScaffold {
        ScreenHeader(title = "Settings")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // ---------------------------------------------------------- Permissions
            SectionLabel("Permissions")
            ChittiSurfaceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter)
                    .staggeredEntrance(0),
                contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.xs)
            ) {
                PermissionRow(
                    title = "Notification access",
                    explanation = "Reads incoming notifications so Chitti can capture events for you",
                    icon = Icons.Filled.Notifications,
                    iconTint = Accent,
                    granted = notificationListenerEnabled,
                    onGrant = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        } catch (e: android.content.ActivityNotFoundException) {
                            Toast.makeText(context, "Notification access settings not available", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                HairlineDivider()
                PermissionRow(
                    title = "Microphone",
                    explanation = "Lets you talk to Chitti and dictate notes on device",
                    icon = Icons.Filled.Mic,
                    iconTint = Iris,
                    granted = hasMicrophone,
                    onGrant = { requestPermission(android.Manifest.permission.RECORD_AUDIO) }
                )
                HairlineDivider()
                PermissionRow(
                    title = "Calendar",
                    explanation = "Writes detected events straight into your calendar",
                    icon = Icons.Filled.DateRange,
                    iconTint = Amber,
                    granted = hasCalendar,
                    onGrant = { requestPermission(android.Manifest.permission.WRITE_CALENDAR) }
                )
            }

            // ---------------------------------------------------------- Models
            SectionLabel("On-device models")
            ChittiSurfaceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter)
                    .staggeredEntrance(1),
                contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.xs)
            ) {
                ModelRow(
                    name = "MediaPipe Gemma",
                    role = "Language model",
                    icon = Icons.Filled.Psychology
                )
                HairlineDivider()
                ModelRow(
                    name = "Android SpeechRecognizer",
                    role = "Speech to text",
                    icon = Icons.Filled.RecordVoiceOver
                )
                HairlineDivider()
                ModelRow(
                    name = "Android TextToSpeech",
                    role = "Text to speech",
                    icon = Icons.Filled.VolumeUp
                )
            }

            // ---------------------------------------------------------- Storage
            SectionLabel("Storage")
            ChittiSurfaceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter)
                    .staggeredEntrance(2)
            ) {
                StorageRow("Captured events", eventCount)
                StorageRow("Tasks", taskCount)
                StorageRow("Notifications", notificationCount)
                StorageRow("Memories", memoryCount)
                StorageRow("Chat messages", chatMessageCount)
                StorageRow("Automation logs", automationHistoryCount)
                Spacer(Modifier.height(Space.m))
                HairlineDivider()
                Spacer(Modifier.height(Space.m))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total items",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextHigh,
                        modifier = Modifier.weight(1f)
                    )
                    CountUpText(
                        target = totalItems,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextHigh
                    )
                }
            }

            // ---------------------------------------------------------- Data
            SectionLabel("Data")
            ChittiSurfaceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter)
                    .staggeredEntrance(3)
            ) {
                Text(
                    text = "Clear one kind of data at a time, or erase everything Chitti has stored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid
                )
                Spacer(Modifier.height(Space.m))
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    SecondaryButton(
                        text = "Clear notifications ($notificationCount)",
                        onClick = onClearNotifications,
                        enabled = notificationCount > 0,
                        fill = true
                    )
                    SecondaryButton(
                        text = "Clear chat history ($chatMessageCount)",
                        onClick = onClearChatHistory,
                        enabled = chatMessageCount > 0,
                        fill = true
                    )
                    SecondaryButton(
                        text = "Clear automation logs ($automationHistoryCount)",
                        onClick = onClearAutomationHistory,
                        enabled = automationHistoryCount > 0,
                        fill = true
                    )
                    SecondaryButton(
                        text = "Clear memories ($memoryCount)",
                        onClick = onClearMemories,
                        enabled = memoryCount > 0,
                        fill = true
                    )
                }
                Spacer(Modifier.height(Space.l))
                HairlineDivider()
                Spacer(Modifier.height(Space.l))
                DangerButton(
                    text = "Erase all data",
                    onClick = { showWipeConfirmation = true },
                    icon = Icons.Filled.DeleteForever,
                    fill = true
                )
                Spacer(Modifier.height(Space.s))
                Text(
                    text = "Deletes every event, task, memory, notification and message. This cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLow
                )
            }

            // ---------------------------------------------------------- About
            SectionLabel("About")
            ChittiSurfaceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter)
                    .staggeredEntrance(4)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chitti",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextHigh,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "v1.0",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextLow
                    )
                }
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = "Every model runs on this device. Nothing you capture, say or store leaves it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMid
                )
            }
        }
    }

    // Wipe confirmation dialog
    if (showWipeConfirmation) {
        AlertDialog(
            onDismissRequest = { showWipeConfirmation = false },
            modifier = Modifier.border(1.dp, Hairline, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            containerColor = Surface1,
            iconContentColor = Rose,
            titleContentColor = TextHigh,
            textContentColor = TextMid,
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = Rose) },
            title = { Text("Erase all data?", style = MaterialTheme.typography.titleLarge, color = TextHigh) },
            text = {
                Text(
                    text = "This permanently deletes everything Chitti has stored on this device. It cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMid
                )
            },
            confirmButton = {
                DangerButton(
                    text = "Erase everything",
                    onClick = {
                        onWipeData()
                        showWipeConfirmation = false
                    }
                )
            },
            dismissButton = {
                SecondaryButton(
                    text = "Cancel",
                    onClick = { showWipeConfirmation = false }
                )
            }
        )
    }
}

/** Walks ContextWrappers (Compose gives a ContextThemeWrapper) up to the hosting Activity. */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** One permission: what it is, why Chitti wants it, and its current state. */
@Composable
private fun PermissionRow(
    title: String,
    explanation: String,
    icon: ImageVector,
    iconTint: Color,
    granted: Boolean,
    onGrant: () -> Unit
) {
    ListRow(
        title = title,
        subtitle = explanation,
        icon = icon,
        iconTint = if (granted) iconTint else TextLow
    ) {
        Spacer(Modifier.width(Space.m))
        if (granted) {
            StatusPill(text = "Granted", tint = Mint, icon = Icons.Filled.Check)
        } else {
            SecondaryButton(text = "Grant", onClick = onGrant)
        }
    }
}

/** One on-device model: friendly name, what it does, and where it runs. */
@Composable
private fun ModelRow(
    name: String,
    role: String,
    icon: ImageVector
) {
    ListRow(
        title = name,
        subtitle = role,
        icon = icon,
        iconTint = Sky
    ) {
        Spacer(Modifier.width(Space.m))
        StatusPill(text = "On-device", tint = Sky)
    }
}

/** A stored data type and how many rows of it exist. */
@Composable
private fun StorageRow(label: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMid,
            modifier = Modifier.weight(1f)
        )
        CountUpText(
            target = count,
            style = MaterialTheme.typography.labelLarge,
            color = TextHigh
        )
    }
}
