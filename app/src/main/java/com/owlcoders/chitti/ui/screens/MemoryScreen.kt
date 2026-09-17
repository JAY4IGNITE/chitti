package com.owlcoders.chitti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.db.entities.Memory
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
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
    var selectedCategory by remember { mutableStateOf("all") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<Memory?>(null) }

    val filteredMemories = memories.filter { memory ->
        val matchesCategory = selectedCategory == "all" || memory.category == selectedCategory
        val matchesSearch = searchQuery.isBlank() ||
                memory.key.contains(searchQuery, ignoreCase = true) ||
                memory.value.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesSearch
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFAD1457))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "🧠 Personal Memory",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = "${memories.size} memories stored",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search memories...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        // Category filter
        val allCategories = listOf("all") + categories
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            allCategories.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text(if (category == "all") "All" else category.replaceFirstChar { it.uppercase() }) },
                    leadingIcon = if (selectedCategory == category) {
                        { Icon(Icons.Filled.Done, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Add memory button
        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAD1457))
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Memory")
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredMemories.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No memories yet", color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
                    Text("Add facts, preferences, and knowledge here", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredMemories) { memory ->
                    MemoryCard(
                        memory = memory,
                        dateFormat = dateFormat,
                        onEdit = { editingMemory = memory },
                        onDelete = { onDeleteMemory(memory) }
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
    onDelete: () -> Unit
) {
    val categoryColor = when (memory.category) {
        "college" -> Color(0xFF1565C0)
        "project" -> Color(0xFF2E7D32)
        "person" -> Color(0xFFE65100)
        "branch" -> Color(0xFF6A1B9A)
        else -> Color(0xFF546E7A)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = categoryColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        memory.category.replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = categoryColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = dateFormat.format(Date(memory.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = memory.key,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = memory.value,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF424242)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val allCategories = (defaultCategories + existingCategories).distinct()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialMemory != null) "Edit Memory" else "Add Memory") },
        text = {
            Column {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Key (e.g., 'My branch')") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Value (e.g., 'CSE - AIML')") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Category:", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    allCategories.take(4).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (key.isNotBlank() && value.isNotBlank()) onSave(key, value, category) },
                enabled = key.isNotBlank() && value.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
