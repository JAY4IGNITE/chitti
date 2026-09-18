package com.owlcoders.chitti.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.db.CapturedEvent
import com.owlcoders.chitti.db.entities.Task
import com.owlcoders.chitti.ui.components.ChittiSurfaceCard
import com.owlcoders.chitti.ui.components.Dot
import com.owlcoders.chitti.ui.components.EmptyState
import com.owlcoders.chitti.ui.components.HairlineDivider
import com.owlcoders.chitti.ui.components.ListRow
import com.owlcoders.chitti.ui.components.MeterRow
import com.owlcoders.chitti.ui.components.MetricTile
import com.owlcoders.chitti.ui.components.ScreenHeader
import com.owlcoders.chitti.ui.components.ScreenScaffold
import com.owlcoders.chitti.ui.components.SectionLabel
import com.owlcoders.chitti.ui.components.Space
import com.owlcoders.chitti.ui.components.StatusPill
import com.owlcoders.chitti.ui.components.staggeredEntrance
import com.owlcoders.chitti.ui.theme.*

/**
 * Stats: the assistant's numbers in one column. Five metric tiles, two proportion cards, and
 * whatever is still urgent. Nothing here is decorative; every value comes from the data the
 * screen is handed.
 */
@Composable
fun DashboardScreen(
    events: List<CapturedEvent>,
    tasks: List<Task> = emptyList(),
    notificationCount: Int = 0,
    memoryCount: Int = 0,
    automationCount: Int = 0
) {
    val total = events.size
    val work = events.count { it.category == "Work" }
    val personal = events.count { it.category == "Personal" }
    val academic = events.count { it.category == "Academic" }
    val urgentEvents = events.filter { it.urgency == "High" }

    val pendingTasks = tasks.count { it.status == "pending" }
    val completedTasks = tasks.count { it.status == "done" }
    val highPriority = tasks.count { it.priority == 2 }

    ScreenScaffold {
        ScreenHeader(
            title = "Stats",
            subtitle = "Your assistant at a glance"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // ------------------------------------------------------------- Metric grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter)
                    .staggeredEntrance(0),
                horizontalArrangement = Arrangement.spacedBy(Space.m)
            ) {
                MetricTile(
                    label = "Events",
                    value = total,
                    icon = Icons.AutoMirrored.Filled.EventNote,
                    tint = Accent,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Tasks",
                    value = tasks.size,
                    icon = Icons.Filled.Checklist,
                    tint = Mint,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(Space.m))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter)
                    .staggeredEntrance(1),
                horizontalArrangement = Arrangement.spacedBy(Space.m)
            ) {
                MetricTile(
                    label = "Notifications",
                    value = notificationCount,
                    icon = Icons.Filled.Notifications,
                    tint = Amber,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Memories",
                    value = memoryCount,
                    icon = Icons.Filled.Psychology,
                    tint = Iris,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(Space.m))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter)
                    .staggeredEntrance(2),
                horizontalArrangement = Arrangement.spacedBy(Space.m)
            ) {
                MetricTile(
                    label = "Actions",
                    value = automationCount,
                    icon = Icons.Filled.AutoAwesome,
                    tint = Sky,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.weight(1f))
            }

            // ------------------------------------------------------------- Task status
            SectionLabel("Task status")
            ChittiSurfaceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter)
                    .staggeredEntrance(3)
            ) {
                MeterRow(label = "Pending", value = pendingTasks, total = tasks.size, tint = Amber)
                MeterRow(label = "Done", value = completedTasks, total = tasks.size, tint = Mint)
                MeterRow(
                    label = "High priority",
                    value = highPriority,
                    total = tasks.size,
                    tint = priorityColor(2)
                )
            }

            // ------------------------------------------------------------- Categories
            SectionLabel("Categories")
            ChittiSurfaceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter)
                    .staggeredEntrance(4)
            ) {
                MeterRow(label = "Work", value = work, total = total, tint = categoryColor("Work"))
                MeterRow(label = "Personal", value = personal, total = total, tint = categoryColor("Personal"))
                MeterRow(label = "Academic", value = academic, total = total, tint = categoryColor("Academic"))
            }

            // ------------------------------------------------------------- Needs attention
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                SectionLabel("Needs attention", modifier = Modifier.weight(1f))
                if (urgentEvents.isNotEmpty()) {
                    StatusPill(
                        text = "${urgentEvents.size} high",
                        tint = urgencyColor("High"),
                        modifier = Modifier.padding(end = Space.gutter, bottom = Space.s)
                    )
                }
            }

            if (urgentEvents.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.TaskAlt,
                    title = "Nothing urgent",
                    message = "No high-urgency events are waiting on you right now."
                )
            } else {
                ChittiSurfaceCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.gutter)
                        .staggeredEntrance(5),
                    accent = Rose,
                    contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.xs)
                ) {
                    urgentEvents.forEachIndexed { index, event ->
                        if (index > 0) HairlineDivider()
                        ListRow(
                            title = statsHeadline(event),
                            subtitle = statsMeta(event),
                            trailing = { Dot(urgencyColor(event.urgency)) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Best available one-line name for an event, trimmed so a long capture cannot blow up a row. */
private fun statsHeadline(event: CapturedEvent): String {
    val what = event.extractedWhat?.trim()
    if (!what.isNullOrEmpty()) return what
    val raw = event.rawText.trim().replace('\n', ' ')
    if (raw.isEmpty()) return "Untitled event"
    return if (raw.length > 60) raw.take(60).trimEnd() + "…" else raw
}

/** Secondary line: when it happens and where it came from, whichever of the two we have. */
private fun statsMeta(event: CapturedEvent): String? = listOfNotNull(
    event.extractedWhen?.trim()?.takeIf { it.isNotEmpty() },
    event.sourceApp.trim().takeIf { it.isNotEmpty() }
).joinToString(" · ").takeIf { it.isNotEmpty() }
