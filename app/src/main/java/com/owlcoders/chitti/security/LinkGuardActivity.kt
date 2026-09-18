package com.owlcoders.chitti.security

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owlcoders.chitti.ui.theme.ChittiTheme

/**
 * LinkGuard interstitial.
 *
 * Two entry paths:
 *  1. Internal: any Chitti action that opens a URL routes here first
 *     (see [open] and ActionExecutor.OPEN_DEEP_LINK).
 *  2. External: the activity is registered as a browsable VIEW handler, so the user can
 *     pick "Chitti LinkGuard" when tapping links in WhatsApp/SMS. We scan, then hand the
 *     link to their real browser.
 *
 * SAFE links are forwarded immediately with no UI flash.
 * CAUTION/DANGER links show an explainable warning; DANGER requires an explicit override.
 */
class LinkGuardActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "com.owlcoders.chitti.EXTRA_SCAN_URL"

        /** Launch LinkGuard for a URL from anywhere inside the app. */
        fun open(context: Context, url: String) {
            context.startActivity(
                Intent(context, LinkGuardActivity::class.java)
                    .putExtra(EXTRA_URL, url)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL)
            ?: intent.dataString
            ?: run { finish(); return }

        val verdict = LinkScanner.scan(url)

        // Fast path: verified-safe links open with zero friction.
        if (verdict.level == LinkScanner.RiskLevel.SAFE) {
            forwardToBrowser(url)
            finish()
            return
        }

        setContent {
            ChittiTheme {
                WarningScreen(
                    verdict = verdict,
                    onGoBack = { finish() },
                    onProceed = {
                        forwardToBrowser(url)
                        finish()
                    }
                )
            }
        }
    }

    /** Opens the URL in an external app, never looping back into LinkGuard itself. */
    private fun forwardToBrowser(url: String) {
        val uri = Uri.parse(url)
        val view = Intent(Intent.ACTION_VIEW, uri)

        // Resolve all candidates and drop ourselves to avoid an intent loop.
        val candidates = packageManager.queryIntentActivities(view, PackageManager.MATCH_ALL)
            .map { it.activityInfo }
            .filter { it.packageName != packageName }

        try {
            when {
                candidates.isEmpty() -> {
                    Toast.makeText(this, "No app available to open this link", Toast.LENGTH_SHORT).show()
                }
                candidates.size == 1 -> {
                    startActivity(
                        Intent(view).setComponent(ComponentName(candidates[0].packageName, candidates[0].name))
                    )
                }
                else -> {
                    val chooser = Intent.createChooser(view, "Open with").apply {
                        putExtra(
                            Intent.EXTRA_EXCLUDE_COMPONENTS,
                            arrayOf(ComponentName(this@LinkGuardActivity, LinkGuardActivity::class.java))
                        )
                    }
                    startActivity(chooser)
                }
            }
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No app available to open this link", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun WarningScreen(
    verdict: LinkScanner.LinkVerdict,
    onGoBack: () -> Unit,
    onProceed: () -> Unit
) {
    val danger = verdict.level == LinkScanner.RiskLevel.DANGER
    val accent = if (danger) Color(0xFFD32F2F) else Color(0xFFF59E0B)
    val icon = if (danger) Icons.Filled.GppBad else Icons.Filled.GppMaybe
    val headline = if (danger) "Dangerous link blocked" else "Open this link carefully"
    val sub = if (danger)
        "Chitti's on-device scan flagged this link as a likely scam or phishing attempt."
    else
        "This link has some warning signs. Check the details before continuing."

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(accent.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(52.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                sub,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("LINK", style = MaterialTheme.typography.labelSmall, color = accent)
                    Text(
                        verdict.url,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        maxLines = 4
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("WHY CHITTI FLAGGED IT", style = MaterialTheme.typography.labelSmall, color = accent)
                    Spacer(Modifier.height(4.dp))
                    verdict.reasons.forEach { reason ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text("•  ", color = accent)
                            Text(reason, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Scanned fully on-device · risk score ${verdict.score}/100",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onGoBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Go back — keep me safe", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onProceed, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (danger) "I understand the risk, open anyway" else "Continue to site",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
