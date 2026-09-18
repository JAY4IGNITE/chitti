package com.owlcoders.chitti.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.db.CapturedEvent
import com.owlcoders.chitti.ui.components.*
import com.owlcoders.chitti.ui.theme.*
import java.util.Calendar

private data class QuickAction(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val query: String
)

private val QUICK_ACTIONS = listOf(
    QuickAction("Open WhatsApp", Icons.Filled.Forum, Mint, "open whatsapp"),
    QuickAction("Open YouTube", Icons.Filled.PlayCircle, Rose, "open youtube"),
    QuickAction("Remind me", Icons.Filled.Alarm, Accent, "remind me to check my tasks in 10 minutes"),
    QuickAction("Flashlight", Icons.Filled.FlashlightOn, Amber, "toggle flashlight"),
    QuickAction("What's pending", Icons.Filled.TaskAlt, Sky, "what's pending")
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodayScreen(
    events: List<CapturedEvent>,
    onDeleteEvent: (CapturedEvent) -> Unit = {},
    onQuickAction: (String) -> Unit = {}
) {
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val urgent = events.count { it.urgency.equals("High", ignoreCase = true) }
    val subtitle = when {
        events.isEmpty() -> "Nothing needs you right now"
        urgent > 0 -> "${events.size} on your agenda · $urgent urgent"
        else -> "${events.size} on your agenda"
    }

    ScreenScaffold {
        // Hero: one quiet accent wash behind the greeting, no moving decoration.
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(AmbientGlow)
            )
            Column {
                ScreenHeader(title = greeting, subtitle = subtitle, revealTitle = true)

                SectionLabel("Quick actions")
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = Space.gutter),
                    horizontalArrangement = Arrangement.spacedBy(Space.s)
                ) {
                    itemsIndexed(QUICK_ACTIONS, key = { _, a -> a.label }) { index, action ->
                        ChipButton(
                            label = action.label,
                            icon = action.icon,
                            tint = action.tint,
                            onClick = { onQuickAction(action.query) },
                            modifier = Modifier.staggeredEntrance(index, stepMs = 40)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.l))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.gutter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Agenda",
                style = MaterialTheme.typography.titleLarge,
                color = TextHigh,
                modifier = Modifier.weight(1f)
            )
            if (events.isNotEmpty()) {
                StatusPill(text = "${events.size} active", tint = Accent)
            }
        }

        Spacer(Modifier.height(Space.m))

        if (events.isEmpty()) {
            // Centre the empty state in the space that is left, rather than stranding it under
            // the section title with a screen of dead space below.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.CheckCircleOutline,
                    title = "You're all caught up",
                    message = "Chitti reads your notifications and lists anything that needs doing here. Tap the mic to ask for something."
                )
            }
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .fadingEdges(top = Space.l, showTop = listState.canScrollBackward),
                contentPadding = PaddingValues(start = Space.gutter, end = Space.gutter, bottom = Space.xxl),
                verticalArrangement = Arrangement.spacedBy(Space.m)
            ) {
                itemsIndexed(events, key = { _, e -> e.id }) { index, event ->
                    // Swipe either way to dismiss; the X on the card does the same for a tap.
                    val dismiss = SwipeAction(
                        label = "Dismiss",
                        icon = Icons.Filled.CheckCircleOutline,
                        tint = Mint,
                        removes = true,
                        onCommit = { onDeleteEvent(event) }
                    )
                    SwipeActionBox(
                        startAction = dismiss,
                        endAction = dismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredEntrance(index)
                            .animateItemPlacement(ChittiMotion.settle())
                    ) {
                        ChittiCard(
                            event = event,
                            onDelete = { onDeleteEvent(event) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
