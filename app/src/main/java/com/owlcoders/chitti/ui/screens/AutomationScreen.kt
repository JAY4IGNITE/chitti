package com.owlcoders.chitti.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.db.entities.AutomationHistory
import com.owlcoders.chitti.ui.components.ChittiSurfaceCard
import com.owlcoders.chitti.ui.components.EmptyState
import com.owlcoders.chitti.ui.components.ListRow
import com.owlcoders.chitti.ui.components.ScreenHeader
import com.owlcoders.chitti.ui.components.ScreenScaffold
import com.owlcoders.chitti.ui.components.Space
import com.owlcoders.chitti.ui.components.StatusPill
import com.owlcoders.chitti.ui.components.staggeredEntrance
import com.owlcoders.chitti.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Actions: the log of everything Chitti executed. One card per action, newest first as the DAO
 * supplies it, with the outcome carried by a single pill rather than by the card colour.
 */
@Composable
fun AutomationScreen(
    history: List<AutomationHistory>
) {
    // Anchor for relative timestamps; recomputed whenever the log changes.
    val now = remember(history) { System.currentTimeMillis() }

    val successCount = history.count { it.result.startsWith("success") }
    val failCount = history.count { it.result.startsWith("failed") }
    val confirmedCount = history.count { it.userConfirmed }

    ScreenScaffold {
        ScreenHeader(
            title = "Actions",
            subtitle = "Everything Chitti did on your behalf"
        )

        if (history.isEmpty()) {
            Spacer(Modifier.height(Space.xxl))
            EmptyState(
                icon = Icons.Filled.AutoAwesome,
                title = "No actions yet",
                message = "When Chitti opens an app, sets a reminder or sends something for you, it lands here."
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = Space.gutter,
                    end = Space.gutter,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(Space.s)
            ) {
                item(key = "action-summary") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Space.xs),
                        horizontalArrangement = Arrangement.spacedBy(Space.s)
                    ) {
                        StatusPill(
                            text = "$successCount succeeded",
                            tint = Mint,
                            icon = Icons.Filled.CheckCircle
                        )
                        StatusPill(
                            text = "$failCount failed",
                            tint = Rose,
                            icon = Icons.Filled.ErrorOutline
                        )
                        StatusPill(
                            text = "$confirmedCount confirmed",
                            tint = Sky,
                            icon = Icons.Filled.Verified
                        )
                    }
                }

                itemsIndexed(history, key = { _, item -> item.id }) { index, item ->
                    ActionHistoryRow(item = item, index = index, now = now)
                }
            }
        }
    }
}

/** One executed action: humanised name, when it ran, how it ended, and the detail underneath. */
@Composable
private fun ActionHistoryRow(item: AutomationHistory, index: Int, now: Long) {
    val outcome = actionOutcome(item.result)
    val detail = actionDetail(item)

    ChittiSurfaceCard(
        modifier = Modifier
            .fillMaxWidth()
            .staggeredEntrance(index),
        accent = outcome.tint,
        contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.xs)
    ) {
        ListRow(
            title = humaniseActionId(item.actionType),
            subtitle = actionMeta(item, now),
            icon = outcome.icon,
            iconTint = outcome.tint,
            trailing = { StatusPill(text = outcome.label, tint = outcome.tint) }
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = TextLow,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = Space.m)
            )
        }
    }
}

// ---------------------------------------------------------------------------- formatting

private class ActionOutcome(val label: String, val tint: Color, val icon: ImageVector)

/** Maps the stored `result` string onto one of the four outcomes the log can hold. */
private fun actionOutcome(result: String): ActionOutcome {
    val normalised = result.trim().lowercase(Locale.getDefault())
    return when {
        normalised.startsWith("success") -> ActionOutcome("Success", Mint, Icons.Filled.CheckCircle)
        normalised.startsWith("failed") || normalised.startsWith("error") ->
            ActionOutcome("Failed", Rose, Icons.Filled.ErrorOutline)
        normalised.startsWith("cancel") -> ActionOutcome("Cancelled", Amber, Icons.Filled.Block)
        normalised.isEmpty() -> ActionOutcome("Pending", TextLow, Icons.Filled.Schedule)
        else -> ActionOutcome(
            normalised.replaceFirstChar { it.uppercase() },
            Sky,
            Icons.Filled.Info
        )
    }
}

private val ACRONYMS = setOf("sms", "url", "otp", "api", "id", "ui", "gps", "qr", "pdf")

/** OPEN_APP -> "Open app", SEND_SMS -> "Send SMS". */
private fun humaniseActionId(raw: String): String {
    val words = raw.trim().split('_', '-', ' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return "Action"
    val spelled = words.joinToString(" ") { word ->
        val lower = word.lowercase(Locale.getDefault())
        if (lower in ACRONYMS) lower.uppercase(Locale.getDefault()) else lower
    }
    return spelled.replaceFirstChar { it.uppercase() }
}

/** Relative time, plus the confirmation flag when the user approved the action. */
private fun actionMeta(item: AutomationHistory, now: Long): String {
    val time = actionRelativeTime(item.executedAt, now)
    return if (item.userConfirmed) time + " · Confirmed by you" else time
}

private fun actionRelativeTime(timestamp: Long, now: Long): String {
    val diff = now - timestamp
    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000L} min ago"
        diff < 86_400_000L -> "${diff / 3_600_000L} hr ago"
        diff < 604_800_000L -> "${diff / 86_400_000L} d ago"
        else -> SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}

/** The quiet second line: whatever the result said beyond its status word, then the parameters. */
private fun actionDetail(item: AutomationHistory): String? {
    val parts = listOfNotNull(resultDetail(item.result), readableParameters(item.parameters))
    return parts.joinToString(" · ").takeIf { it.isNotEmpty() }
}

private fun resultDetail(result: String): String? {
    val separator = result.indexOf(':')
    if (separator < 0) return null
    return result.substring(separator + 1).trim().takeIf { it.isNotEmpty() }
}

/** Flattens the stored JSON blob into something a person can skim. */
private fun readableParameters(raw: String): String? {
    var text = raw.trim()
    if (text.isEmpty() || text == "{}" || text == "[]" || text.equals("null", ignoreCase = true)) {
        return null
    }
    if (text.startsWith("{") && text.endsWith("}")) {
        text = text.substring(1, text.length - 1)
    }
    text = text
        .replace("\"", "")
        .replace(",", " · ")
        .replace(":", ": ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return text.takeIf { it.isNotEmpty() }
}
