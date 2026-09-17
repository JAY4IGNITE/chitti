package com.owlcoders.chitti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.ChittiApp
import com.owlcoders.chitti.db.CapturedEvent
import com.owlcoders.chitti.db.entities.ChatHistoryEntity
import com.owlcoders.chitti.db.entities.Memory
import kotlinx.coroutines.launch

data class ChatMessage(val text: String, val isUser: Boolean)

@Composable
fun ChatBotScreen(
    events: List<CapturedEvent>,
    chatHistory: List<ChatHistoryEntity> = emptyList(),
    memories: List<Memory> = emptyList(),
    onSaveMessage: (ChatHistoryEntity) -> Unit = {}
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Initialize messages from persisted chat history
    var messages by remember {
        mutableStateOf(
            if (chatHistory.isNotEmpty()) {
                chatHistory.map { ChatMessage(it.message, it.role == "user") }
            } else {
                listOf(ChatMessage("Hi! I'm Chitti 🤖 I have memorized ${events.size} tasks and ${memories.size} memories. Ask me anything!", false))
            }
        )
    }
    var inputText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
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
                .background(Color(0xFF673AB7))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "✨ Chitti AI Assistant",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = "Memory-augmented · ${events.size} tasks · ${memories.size} memories",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Chat History
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(messages) { msg ->
                ChatBubble(msg)
            }
            if (isThinking) {
                item {
                    ChatBubble(ChatMessage("🤔 Thinking...", false))
                }
            }
        }

        // Input Area
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp,
            color = Color.White
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask about your tasks...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF673AB7),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isThinking) {
                            val query = inputText
                            inputText = ""
                            messages = messages + ChatMessage(query, true)
                            isThinking = true

                            // Persist user message
                            onSaveMessage(ChatHistoryEntity(role = "user", message = query))

                            scope.launch {
                                val engine = (context.applicationContext as ChittiApp).extractionEngine

                                // Build context from memories + events
                                val memoryContext = if (memories.isNotEmpty()) {
                                    "\nUser Memories:\n" + memories.joinToString("\n") { "- ${it.key}: ${it.value}" }
                                } else ""

                                val response = engine?.generateRagResponse(query + memoryContext, events)
                                    ?: "Sorry, the AI engine is not available right now."

                                messages = messages + ChatMessage(response, false)
                                isThinking = false

                                // Persist assistant response
                                onSaveMessage(ChatHistoryEntity(role = "assistant", message = response))
                            }
                        }
                    },
                    containerColor = Color(0xFF673AB7),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun ChatBubble(msg: ChatMessage) {
    val alignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bgColor = if (msg.isUser) Color(0xFF673AB7) else Color.White
    val textColor = if (msg.isUser) Color.White else Color.Black

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (msg.isUser) 16.dp else 4.dp,
                bottomEnd = if (msg.isUser) 4.dp else 16.dp
            ),
            color = bgColor,
            shadowElevation = 2.dp,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = msg.text,
                modifier = Modifier.padding(12.dp),
                color = textColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
