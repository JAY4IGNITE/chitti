package com.owlcoders.chitti.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.db.entities.NotificationEntity
import com.owlcoders.chitti.ui.components.ChittiMotion
import com.owlcoders.chitti.ui.components.ChittiSurfaceCard
import com.owlcoders.chitti.ui.components.EmptyState
import com.owlcoders.chitti.ui.components.ScreenHeader
import com.owlcoders.chitti.ui.components.ScreenScaffold
import com.owlcoders.chitti.ui.components.SecondaryButton
import com.owlcoders.chitti.ui.components.SegmentedTabs
import com.owlcoders.chitti.ui.components.Space
import com.owlcoders.chitti.ui.components.StatusPill
import com.owlcoders.chitti.ui.components.SwipeAction
import com.owlcoders.chitti.ui.components.SwipeActionBox
import com.owlcoders.chitti.ui.components.fadingEdges
import com.owlcoders.chitti.ui.components.pressScale
import com.owlcoders.chitti.ui.components.staggeredEntrance
import com.owlcoders.chitti.ui.theme.Mint
import com.owlcoders.chitti.ui.theme.Rose
import com.owlcoders.chitti.ui.theme.Sky
import com.owlcoders.chitti.ui.theme.TextHigh
import com.owlcoders.chitti.ui.theme.TextLow
import com.owlcoders.chitti.ui.theme.TextMid
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Bottom inset so the floating bottom bar never covers the last card. */
private val BottomBarInset = 24.dp

private val InboxTabs = listOf("All", "Unprocessed", "Processed")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InboxScreen(
    notifications: List<NotificationEntity>,
    onMarkProcessed: (NotificationEntity) -> Unit = {},
    onDelete: (NotificationEntity) -> Unit = {}
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val filteredNotifications = when (selectedTab) {
        1 -> notifications.filter { !it.processed }
        2 -> notifications.filter { it.processed }
        else -> notifications
    }
    val unprocessed = notifications.count { !it.processed }

    ScreenScaffold {
        ScreenHeader(
            title = "Inbox",
            subtitle = when {
                notifications.isEmpty() -> "Nothing captured yet"
                unprocessed == 0 -> "${notifications.size} captured, all processed"
                else -> "${notifications.size} captured, $unprocessed unprocessed"
            }
        )

        SegmentedTabs(
            options = InboxTabs,
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.padding(horizontal = Space.gutter)
        )

        Spacer(Modifier.height(Space.l))

        if (filteredNotifications.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Inbox,
                title = if (notifications.isEmpty()) "No notifications yet" else "Nothing here",
                message = when (selectedTab) {
                    1 -> "Every captured notification has been processed."
                    2 -> "Notifications you process will collect here."
                    else -> "Chitti collects notifications from your apps once access is granted."
                }
            )
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fadingEdges(top = Space.l, showTop = listState.canScrollBackward),
                contentPadding = PaddingValues(
                    start = Space.gutter,
                    end = Space.gutter,
                    top = Space.xs,
                    bottom = BottomBarInset
                ),
                verticalArrangement = Arrangement.spacedBy(Space.m)
            ) {
                // Stable keys: the card keeps local `expanded` state, so without keys that
                // state would jump to whichever item slides into the same position after a
                // delete or a filter change.
                itemsIndexed(filteredNotifications, key = { _, n -> n.id }) { index, notification ->
                    // Right: mark processed (it leaves the list only on the Unprocessed tab).
                    // Left: delete. The card's own buttons do the same for a tap.
                    SwipeActionBox(
                        startAction = if (notification.processed) null else SwipeAction(
                            label = "Processed",
                            icon = Icons.Filled.Done,
                            tint = Mint,
                            removes = selectedTab == 1,
                            onCommit = { onMarkProcessed(notification) }
                        ),
                        endAction = SwipeAction(
                            label = "Delete",
                            icon = Icons.Filled.Delete,
                            tint = Rose,
                            removes = true,
                            onCommit = { onDelete(notification) }
                        ),
                        modifier = Modifier
                            .animateItemPlacement(ChittiMotion.settle())
                            .staggeredEntrance(index)
                    ) {
                        NotificationCard(
                            notification = notification,
                            dateFormat = dateFormat,
                            onMarkProcessed = { onMarkProcessed(notification) },
                            onDelete = { onDelete(notification) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationCard(
    notification: NotificationEntity,
    dateFormat: SimpleDateFormat,
    onMarkProcessed: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ChittiSurfaceCard(
        modifier = modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
        accent = if (notification.processed) Mint else Sky
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            StatusPill(text = sourceLabel(notification.packageName), tint = Sky)
            Spacer(Modifier.width(Space.s))
            Spacer(Modifier.weight(1f))
            Text(
                text = relativeTime(notification.postTime, dateFormat),
                style = MaterialTheme.typography.labelSmall,
                color = TextLow,
                maxLines = 1
            )
        }

        Spacer(Modifier.height(Space.m))

        Text(
            text = notification.rawTitle.ifBlank { "Untitled notification" },
            style = MaterialTheme.typography.titleMedium,
            color = TextHigh,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis
        )

        if (notification.rawText.isNotBlank()) {
            Spacer(Modifier.height(Space.xs))
            Text(
                text = notification.rawText,
                style = MaterialTheme.typography.bodySmall,
                color = TextMid,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(Space.l))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            // The button becomes the pill in place, so the result appears where the action was.
            AnimatedContent(
                targetState = notification.processed,
                transitionSpec = {
                    (fadeIn(tween(160)) + scaleIn(ChittiMotion.settle(), initialScale = 0.85f)) togetherWith
                        fadeOut(tween(100))
                },
                label = "processed"
            ) { processed ->
                if (processed) {
                    StatusPill(text = "Processed", tint = Mint, icon = Icons.Filled.Check)
                } else {
                    SecondaryButton(
                        text = "Mark processed",
                        onClick = onMarkProcessed,
                        icon = Icons.Filled.Done
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            IconAction(
                icon = Icons.Filled.Delete,
                tint = Rose,
                contentDescription = "Delete notification",
                onClick = onDelete
            )
        }
    }
}

/** Quiet square icon affordance: tinted wash, tinted glyph, press feedback. */
@Composable
private fun IconAction(
    icon: ImageVector,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(36.dp)
            .pressScale(interaction, pressed = 0.94f)
            .clip(RoundedCornerShape(11.dp))
            .background(tint.copy(alpha = 0.12f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(17.dp))
    }
}

/** "com.whatsapp" reads best as "Whatsapp": the package name is all a notification carries. */
private fun sourceLabel(packageName: String): String {
    val segment = packageName.substringAfterLast('.').ifBlank { packageName }
    return segment.replaceFirstChar { it.uppercase() }
}

/** Relative time for anything within the last week, an absolute stamp beyond it. */
private fun relativeTime(postTime: Long, dateFormat: SimpleDateFormat): String {
    val delta = System.currentTimeMillis() - postTime
    val minutes = delta / 60_000L
    val hours = delta / 3_600_000L
    val days = delta / 86_400_000L
    return when {
        delta < 0L -> dateFormat.format(Date(postTime))
        minutes < 1L -> "just now"
        minutes < 60L -> "${minutes}m ago"
        hours < 24L -> "${hours}h ago"
        days < 7L -> "${days}d ago"
        else -> dateFormat.format(Date(postTime))
    }
}
