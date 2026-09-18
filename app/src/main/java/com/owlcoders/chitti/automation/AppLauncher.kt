package com.owlcoders.chitti.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log

data class AppLaunchResult(
    val success: Boolean,
    val message: String,
    val appName: String? = null
)

/**
 * Intelligent Mobile App Launcher for Chitti.
 * Resolves spoken commands (e.g. "open WhatsApp", "open YouTube", "open Camera", "open Settings")
 * to specific installed packages or dynamic launcher intent queries with web fallbacks.
 */
class AppLauncher(private val context: Context) {
    private val tag = "ChittiAppLauncher"

    private val commonPackages = mapOf(
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "what's app" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "youtube" to listOf("com.google.android.youtube"),
        "yt" to listOf("com.google.android.youtube"),
        "chrome" to listOf("com.android.chrome"),
        "browser" to listOf("com.android.chrome"),
        "instagram" to listOf("com.instagram.android"),
        "insta" to listOf("com.instagram.android"),
        "spotify" to listOf("com.spotify.music"),
        "telegram" to listOf("org.telegram.messenger"),
        "gmail" to listOf("com.google.android.gm"),
        "maps" to listOf("com.google.android.apps.maps"),
        "google maps" to listOf("com.google.android.apps.maps"),
        "twitter" to listOf("com.twitter.android"),
        "x" to listOf("com.twitter.android"),
        "discord" to listOf("com.discord"),
        "slack" to listOf("com.Slack")
    )

    private val fallbackUrls = mapOf(
        "whatsapp" to "https://web.whatsapp.com",
        "youtube" to "https://www.youtube.com",
        "chrome" to "https://www.google.com",
        "instagram" to "https://www.instagram.com",
        "spotify" to "https://open.spotify.com",
        "maps" to "https://maps.google.com",
        "twitter" to "https://x.com"
    )

    fun launch(query: String): AppLaunchResult {
        val cleanQuery = query.lowercase()
            .replace(Regex("^(open|launch|start|run|go to)\\s+"), "")
            .replace(Regex("\\s+(app|application)$"), "")
            .trim()

        Log.d(tag, "Attempting to launch app for query: '$cleanQuery'")

        // 1. System actions
        when (cleanQuery) {
            "camera", "cam", "photo", "take picture" -> {
                return launchCamera()
            }
            "settings", "setting", "phone settings" -> {
                return launchSettings()
            }
            "phone", "dialer", "call", "contacts" -> {
                return launchDialer()
            }
            "calendar", "calender", "events" -> {
                return launchCalendar()
            }
        }

        // 2. Direct package lookup
        val matchingKey = commonPackages.keys.find { cleanQuery == it || cleanQuery.contains(it) }
        if (matchingKey != null) {
            val candidatePkgs = commonPackages[matchingKey] ?: emptyList()
            for (pkg in candidatePkgs) {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    val friendlyName = matchingKey.replaceFirstChar { it.uppercase() }
                    return AppLaunchResult(true, "Opening $friendlyName...", friendlyName)
                }
            }

            // Fallback to web link if installed package not found (e.g. YouTube in browser)
            val fallbackUrl = fallbackUrls[matchingKey]
            if (fallbackUrl != null) {
                return try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(browserIntent)
                    val friendlyName = matchingKey.replaceFirstChar { it.uppercase() }
                    AppLaunchResult(true, "Opening $friendlyName in browser...", friendlyName)
                } catch (e: Exception) {
                    AppLaunchResult(false, "Could not open $matchingKey")
                }
            }
        }

        // 3. Dynamic lookup across all installed launcher applications on device
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val pkgAppsList = context.packageManager.queryIntentActivities(mainIntent, 0)
            
            // Exact label match first
            for (resolveInfo in pkgAppsList) {
                val label = resolveInfo.loadLabel(context.packageManager).toString()
                if (label.equals(cleanQuery, ignoreCase = true)) {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        return AppLaunchResult(true, "Opening $label...", label)
                    }
                }
            }

            // Partial label match
            for (resolveInfo in pkgAppsList) {
                val label = resolveInfo.loadLabel(context.packageManager).toString()
                if (label.lowercase().contains(cleanQuery) || cleanQuery.contains(label.lowercase())) {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        return AppLaunchResult(true, "Opening $label...", label)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed dynamic app search: ${e.message}")
        }

        return AppLaunchResult(false, "App \"$cleanQuery\" not found on your phone.")
    }

    private fun launchCamera(): AppLaunchResult {
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            AppLaunchResult(true, "Opening Camera...", "Camera")
        } catch (e: Exception) {
            AppLaunchResult(false, "Failed to open camera: ${e.message}")
        }
    }

    private fun launchSettings(): AppLaunchResult {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            AppLaunchResult(true, "Opening Settings...", "Settings")
        } catch (e: Exception) {
            AppLaunchResult(false, "Failed to open settings: ${e.message}")
        }
    }

    private fun launchDialer(): AppLaunchResult {
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            AppLaunchResult(true, "Opening Phone...", "Phone")
        } catch (e: Exception) {
            AppLaunchResult(false, "Failed to open phone: ${e.message}")
        }
    }

    private fun launchCalendar(): AppLaunchResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            AppLaunchResult(true, "Opening Calendar...", "Calendar")
        } catch (e: Exception) {
            AppLaunchResult(false, "Failed to open calendar: ${e.message}")
        }
    }
}
