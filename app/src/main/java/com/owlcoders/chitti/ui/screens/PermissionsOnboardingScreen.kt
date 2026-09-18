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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.owlcoders.chitti.services.ChittiAccessibilityService
import com.owlcoders.chitti.ui.components.BlurText
import com.owlcoders.chitti.ui.components.ChittiMotion
import com.owlcoders.chitti.ui.components.ChittiSurfaceCard
import com.owlcoders.chitti.ui.components.MeterRow
import com.owlcoders.chitti.ui.components.rememberHaptics
import com.owlcoders.chitti.ui.components.HairlineDivider
import com.owlcoders.chitti.ui.components.ListRow
import com.owlcoders.chitti.ui.components.PrimaryButton
import com.owlcoders.chitti.ui.components.ScreenScaffold
import com.owlcoders.chitti.ui.components.SecondaryButton
import com.owlcoders.chitti.ui.components.Space
import com.owlcoders.chitti.ui.components.StatusPill
import com.owlcoders.chitti.ui.components.pressScale
import com.owlcoders.chitti.ui.components.staggeredEntrance
import com.owlcoders.chitti.ui.theme.Accent
import com.owlcoders.chitti.ui.theme.AccentBright
import com.owlcoders.chitti.ui.theme.AmbientGlow
import com.owlcoders.chitti.ui.theme.HairlineStrong
import com.owlcoders.chitti.ui.theme.Hairline
import com.owlcoders.chitti.ui.theme.Mint
import com.owlcoders.chitti.ui.theme.Surface2
import com.owlcoders.chitti.ui.theme.TextHigh
import com.owlcoders.chitti.ui.theme.TextLow
import com.owlcoders.chitti.ui.theme.TextMid

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

    val skipInteraction = remember { MutableInteractionSource() }

    ScreenScaffold {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(Space.xxxl))

            // Hero: app mark sitting in one quiet accent glow, the wordmark arriving out of a blur,
            // one line of copy.
            Box(contentAlignment = Alignment.Center, modifier = Modifier.staggeredEntrance(0)) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .background(AmbientGlow)
                )
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Surface2)
                        .border(1.dp, HairlineStrong, RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = AccentBright,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(Modifier.height(Space.s))

            BlurText(
                text = "Chitti",
                style = MaterialTheme.typography.displayLarge,
                color = TextHigh
            )

            Spacer(Modifier.height(Space.s))

            Text(
                text = "An assistant that runs on your phone. Grant these so it can listen, read what arrives and act for you.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMid,
                textAlign = TextAlign.Center,
                modifier = Modifier.staggeredEntrance(2)
            )

            Spacer(Modifier.height(Space.xxl))

            // Progress through the three required grants, so the goal and the distance to it are visible.
            val granted = listOf(hasMic, hasNotification, hasAccessibility).count { it }
            MeterRow(
                label = if (granted == 3) "All set" else "Required access",
                value = granted,
                total = 3,
                tint = if (granted == 3) Mint else Accent,
                modifier = Modifier
                    .padding(horizontal = Space.xs)
                    .staggeredEntrance(3)
            )

            Spacer(Modifier.height(Space.m))

            ChittiSurfaceCard(
                modifier = Modifier.staggeredEntrance(4),
                contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.xs)
            ) {
                PermissionItem(
                    title = "Microphone",
                    description = "Hear your voice commands.",
                    icon = Icons.Filled.Mic,
                    isGranted = hasMic,
                    onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                )

                HairlineDivider(inset = 44.dp)

                PermissionItem(
                    title = "Notification access",
                    description = "Turn incoming messages into tasks.",
                    icon = Icons.Filled.Notifications,
                    isGranted = hasNotification,
                    onClick = { openSettingsSafely(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
                )

                HairlineDivider(inset = 44.dp)

                PermissionItem(
                    title = "Accessibility service",
                    description = "Tap and type on your behalf.",
                    icon = Icons.Filled.Accessibility,
                    isGranted = hasAccessibility,
                    onClick = { openSettingsSafely(context, Settings.ACTION_ACCESSIBILITY_SETTINGS) }
                )

                if (Build.VERSION.SDK_INT >= 33) {
                    HairlineDivider(inset = 44.dp)

                    PermissionItem(
                        title = "Show notifications",
                        description = "Reminders and suspicious-link alerts. Optional.",
                        icon = Icons.Filled.NotificationsActive,
                        isGranted = hasPostNotifications,
                        onClick = { postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    )
                }
            }

            Spacer(Modifier.height(Space.xxl))

            PrimaryButton(
                text = "Continue",
                modifier = Modifier.staggeredEntrance(5),
                onClick = {
                    refresh()
                    if (hasMic && hasNotification && hasAccessibility) {
                        onAllPermissionsGranted()
                    }
                }
            )

            Spacer(Modifier.height(Space.m))

            // Bypass, kept for testing and for users who want to look around first.
            Text(
                text = "Skip for now",
                style = MaterialTheme.typography.labelLarge,
                color = TextLow,
                modifier = Modifier
                    .pressScale(skipInteraction, pressed = 0.97f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = skipInteraction,
                        indication = null,
                        onClick = onAllPermissionsGranted
                    )
                    .padding(horizontal = Space.l, vertical = Space.m)
            )
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

/** One permission: icon well, name, one-line reason, and either a Granted pill or a Grant button. */
@Composable
fun PermissionItem(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    // A grant that lands while the screen is open is confirmed where it happened: the button
    // turns into the pill in place, with a confirm haptic on the same frame.
    val haptics = rememberHaptics()
    var wasGranted by remember { mutableStateOf(isGranted) }
    LaunchedEffect(isGranted) {
        if (isGranted && !wasGranted) haptics.confirm()
        wasGranted = isGranted
    }
    ListRow(
        title = title,
        subtitle = description,
        icon = icon,
        iconTint = if (isGranted) Mint else Accent,
        trailing = {
            AnimatedContent(
                targetState = isGranted,
                transitionSpec = {
                    (fadeIn(tween(160)) + scaleIn(ChittiMotion.settle(), initialScale = 0.8f)) togetherWith
                        fadeOut(tween(100))
                },
                label = "grant"
            ) { granted ->
                if (granted) {
                    StatusPill(text = "Granted", tint = Mint, icon = Icons.Filled.Check)
                } else {
                    SecondaryButton(text = "Grant", onClick = onClick)
                }
            }
        }
    )
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
