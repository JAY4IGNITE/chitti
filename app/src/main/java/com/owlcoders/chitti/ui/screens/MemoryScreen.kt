package com.owlcoders.chitti.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.owlcoders.chitti.db.entities.Memory
import com.owlcoders.chitti.ui.components.ChittiMotion
import com.owlcoders.chitti.ui.components.ChittiSurfaceCard
import com.owlcoders.chitti.ui.components.ChittiTextField
import com.owlcoders.chitti.ui.components.EmptyState
import com.owlcoders.chitti.ui.components.PrimaryButton
import com.owlcoders.chitti.ui.components.ScreenHeader
import com.owlcoders.chitti.ui.components.ScreenScaffold
import com.owlcoders.chitti.ui.components.SecondaryButton
import com.owlcoders.chitti.ui.components.Space
import com.owlcoders.chitti.ui.components.StatusPill
import com.owlcoders.chitti.ui.components.pressScale
import com.owlcoders.chitti.ui.components.staggeredEntrance
import com.owlcoders.chitti.ui.theme.Accent
import com.owlcoders.chitti.ui.theme.AccentWash
import com.owlcoders.chitti.ui.theme.Hairline
import com.owlcoders.chitti.ui.theme.Rose
import com.owlcoders.chitti.ui.theme.Surface1
import com.owlcoders.chitti.ui.theme.Surface2
import com.owlcoders.chitti.ui.theme.TextHigh
import com.owlcoders.chitti.ui.theme.TextLow
import com.owlcoders.chitti.ui.theme.TextMid
import com.owlcoders.chitti.ui.theme.categoryColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Bottom inset so the floating bottom bar never covers the last card. */
private val BottomBarInset = 24.dp

private const val AllCategories = "all"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MemoryScreen(
    memories: List<Memory>,
    categories: List<String>,
    onAddMemory: (String, String, String) -> Unit = { _, _, _ -> },
    onDeleteMemory: (Memory) -> Unit = {},
    onUpdateMemory: (Memory) -> Unit = {}
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(AllCategories) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<Memory?>(null) }

    val filteredMemories = memories.filter { memory ->
        val matchesCategory = selectedCategory == AllCategories || memory.category == selectedCategory
        val matchesSearch = searchQuery.isBlank() ||
                memory.key.contains(searchQuery, ignoreCase = true) ||
                memory.value.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesSearch
    }

    // Every known category stays reachable: the row scrolls instead of truncating.
    val categoryOptions = (listOf(AllCategories) + categories).distinct()

    ScreenScaffold {
        ScreenHeader(
            title = "Memory",
            subtitle = "Facts Chitti remembers about you",
            trailing = {
                MemoryIconAction(
                    icon = Icons.Filled.Add,
                    tint = Accent,
                    contentDescription = "Add memory",
                    onClick = { showAddDialog = true }
                )
            }
        )

        ChittiTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = "Search memories",
            leadingIcon = Icons.Filled.Search,
            modifier = Modifier.padding(horizontal = Space.gutter)
        )

        Spacer(Modifier.height(Space.m))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Space.gutter),
            horizontalArrangement = Arrangement.spacedBy(Space.s)
        ) {
            items(categoryOptions, key = { it }) { category ->
                CategoryPill(
                    label = category.titleCase(),
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category }
                )
            }
        }

        Spacer(Modifier.height(Space.l))

        if (filteredMemories.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Psychology,
                title = if (memories.isEmpty()) "No memories yet" else "Nothing matches",
                message = if (memories.isEmpty()) {
                    "Add the facts, preferences and people Chitti should remember about you."
                } else {
                    "Try a different search term or category."
                }
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = Space.gutter,
                    end = Space.gutter,
                    top = Space.xs,
                    bottom = BottomBarInset
                ),
                verticalArrangement = Arrangement.spacedBy(Space.m)
            ) {
                itemsIndexed(filteredMemories, key = { _, m -> m.id }) { index, memory ->
                    MemoryCard(
                        memory = memory,
                        dateFormat = dateFormat,
                        onEdit = { editingMemory = memory },
                        onDelete = { onDeleteMemory(memory) },
                        modifier = Modifier
                            .animateItemPlacement(ChittiMotion.settle())
                            .staggeredEntrance(index)
                    )
                }
            }
        }
    }

    // Add / Edit Memory Dialog
    if (showAddDialog || editingMemory != null) {
        AddMemoryDialog(
            existingCategories = categories,
            initialMemory = editingMemory,
            onDismiss = {
                showAddDialog = false
                editingMemory = null
            },
            onSave = { key, value, category ->
                val current = editingMemory
                if (current != null) {
                    onUpdateMemory(
                        current.copy(
                            key = key,
                            value = value,
                            category = category,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    onAddMemory(key, value, category)
                }
                showAddDialog = false
                editingMemory = null
            }
        )
    }
}

@Composable
fun MemoryCard(
    memory: Memory,
    dateFormat: SimpleDateFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = categoryColor(memory.category)

    ChittiSurfaceCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onEdit,
        accent = tint
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            StatusPill(text = memory.category.titleCase(), tint = tint)
            Spacer(Modifier.width(Space.s))
            Spacer(Modifier.weight(1f))
            Text(
                text = dateFormat.format(Date(memory.updatedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = TextLow,
                maxLines = 1
            )
            Spacer(Modifier.width(Space.s))
            MemoryIconAction(
                icon = Icons.Filled.Delete,
                tint = Rose,
                contentDescription = "Delete memory",
                onClick = onDelete
            )
        }

        Spacer(Modifier.height(Space.m))

        Text(
            text = memory.key,
            style = MaterialTheme.typography.titleMedium,
            color = TextHigh,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(Space.xs))

        Text(
            text = memory.value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMid
        )
    }
}

@Composable
fun AddMemoryDialog(
    existingCategories: List<String>,
    initialMemory: Memory? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var key by remember { mutableStateOf(initialMemory?.key ?: "") }
    var value by remember { mutableStateOf(initialMemory?.value ?: "") }
    var category by remember { mutableStateOf(initialMemory?.category ?: "note") }

    val defaultCategories = listOf("note", "college", "branch", "project", "person")
    val categoryOptions = (defaultCategories + existingCategories).distinct()
    val canSave = key.isNotBlank() && value.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(1.dp, Hairline, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        containerColor = Surface1,
        titleContentColor = TextHigh,
        textContentColor = TextMid,
        title = {
            Text(
                text = if (initialMemory != null) "Edit memory" else "Add memory",
                style = MaterialTheme.typography.titleLarge,
                color = TextHigh
            )
        },
        text = {
            Column {
                ChittiTextField(
                    value = key,
                    onValueChange = { key = it },
                    placeholder = "My branch",
                    label = "Key"
                )
                Spacer(Modifier.height(Space.m))
                ChittiTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = "CSE - AIML",
                    label = "Value",
                    singleLine = false
                )
                Spacer(Modifier.height(Space.l))
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextLow
                )
                Spacer(Modifier.height(Space.s))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    categoryOptions.forEach { option ->
                        CategoryPill(
                            label = option.titleCase(),
                            selected = category == option,
                            onClick = { category = option }
                        )
                    }
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                text = "Save",
                onClick = { if (canSave) onSave(key, value, category) },
                enabled = canSave,
                fill = false
            )
        },
        dismissButton = {
            SecondaryButton(text = "Cancel", onClick = onDismiss)
        }
    )
}

/** Small filter pill: accent wash when selected, quiet raised surface when not. */
@Composable
private fun CategoryPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val background by animateColorAsState(
        targetValue = if (selected) AccentWash else Surface2,
        animationSpec = ChittiMotion.settle(),
        label = "pillBackground"
    )
    val content by animateColorAsState(
        targetValue = if (selected) Accent else TextMid,
        animationSpec = ChittiMotion.settle(),
        label = "pillLabel"
    )
    val stroke by animateColorAsState(
        targetValue = if (selected) Accent.copy(alpha = 0.35f) else Hairline,
        animationSpec = ChittiMotion.settle(),
        label = "pillStroke"
    )
    Box(
        modifier = modifier
            .pressScale(interaction, pressed = 0.96f)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .border(1.dp, stroke, RoundedCornerShape(10.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = Space.m, vertical = Space.s)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1
        )
    }
}

/** Quiet square icon affordance: tinted wash, tinted glyph, press feedback. */
@Composable
private fun MemoryIconAction(
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

private fun String.titleCase(): String = replaceFirstChar { it.uppercase() }
