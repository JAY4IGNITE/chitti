package com.owlcoders.chitti.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owlcoders.chitti.automation.AssistantIntentDispatcher
import com.owlcoders.chitti.db.CapturedEvent
import com.owlcoders.chitti.db.entities.ChatHistoryEntity
import com.owlcoders.chitti.db.entities.Memory
import com.owlcoders.chitti.services.TtsEngine
import com.owlcoders.chitti.ui.components.ChittiMotion
import com.owlcoders.chitti.ui.components.LatticeLoader
import com.owlcoders.chitti.ui.components.pressScale
import com.owlcoders.chitti.ui.components.rememberReducedMotion
import com.owlcoders.chitti.ui.components.LatticePatterns
import com.owlcoders.chitti.ui.components.LatticeStatus
import com.owlcoders.chitti.ui.theme.*
import kotlinx.coroutines.launch

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val actionLabel: String? = null
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBotScreen(
    events: List<CapturedEvent>,
    chatHistory: List<ChatHistoryEntity> = emptyList(),
    memories: List<Memory> = emptyList(),
    dispatcher: AssistantIntentDispatcher? = null,
    ttsEngine: TtsEngine? = null,
    onSaveMessage: (ChatHistoryEntity) -> Unit = {},
    onStartVoice: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val greeting = remember {
        ChatMessage(
            "Hey! I'm Chitti, your mobile AI assistant ✨ I can open apps (like WhatsApp or YouTube), set reminders, manage tasks, or answer anything. Try asking me!",
            false
        )
    }

    var messages by remember {
        mutableStateOf(
            if (chatHistory.isNotEmpty()) {
                chatHistory.map { ChatMessage(it.message, it.role == "user") }
            } else {
                listOf(greeting)
            }
        )
    }

    // `chatHistory` comes from collectAsState(initial = emptyList()), so the first
    // composition may see an empty list and fall back to the greeting. Hydrate once
    // when the persisted history actually arrives, but never after the user has
    // started a conversation locally (that would drop/duplicate in-flight messages).
    var hydratedFromHistory by remember { mutableStateOf(chatHistory.isNotEmpty()) }
    LaunchedEffect(chatHistory) {
        if (!hydratedFromHistory && chatHistory.isNotEmpty() && messages.none { it.isUser }) {
            messages = chatHistory.map { ChatMessage(it.message, it.role == "user") }
            hydratedFromHistory = true
        }
    }

    var inputText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun sendMessage(queryText: String) {
        if (queryText.isBlank() || isThinking) return
        val query = queryText.trim()
        inputText = ""
        hydratedFromHistory = true // local conversation is now the source of truth
        messages = messages + ChatMessage(query, true)
        isThinking = true
        onSaveMessage(ChatHistoryEntity(role = "user", message = query))

        scope.launch {
            try {
                if (dispatcher != null) {
                    val response = dispatcher.processQuery(query, events, shouldSpeak = true)
                    messages = messages + ChatMessage(response.message, false, response.actionLabel)
                    onSaveMessage(ChatHistoryEntity(role = "assistant", message = response.message))
                } else {
                    val reply = "Assistant is currently initializing."
                    messages = messages + ChatMessage(reply, false)
                }
            } catch (e: Exception) {
                messages = messages + ChatMessage("Sorry, something went wrong: ${e.message ?: "unknown error"}", false)
            } finally {
                isThinking = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GeminiDarkBg)
    ) {
        // Assistant Top Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Ink)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.SmartToy, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Chitti", style = MaterialTheme.typography.titleLarge, color = TextHigh)
                        Text("On-device assistant", style = MaterialTheme.typography.labelSmall, color = TextMid)
                    }
                }

                if (ttsEngine?.isSpeaking == true) {
                    IconButton(onClick = { ttsEngine.stop() }) {
                        Icon(Icons.Filled.VolumeMute, contentDescription = "Stop speaking", tint = Rose, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Hairline))

        // Chat Message History
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // The list only ever appends, so the index is a stable key; animateItemPlacement keeps
            // existing bubbles gliding up when the thinking row appears/disappears.
            itemsIndexed(messages, key = { index, _ -> index }) { _, msg ->
                Box(modifier = Modifier.animateItemPlacement(ChittiMotion.settle())) {
                    GeminiChatBubble(
                        msg = msg,
                        onSpeak = {
                            ttsEngine?.speak(msg.text)
                        }
                    )
                }
            }

            if (isThinking) {
                item(key = "lattice-thinking") {
                    LatticeLoader(
                        status = LatticeStatus.WORKING,
                        label = "Thinking",
                        pattern = LatticePatterns.Orbit,
                        color = Accent,
                        glow = false,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // Input Bar Area: a floating material with a light-catching top edge instead of a hard divider
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Surface1
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mic Button
                val micInteraction = remember { MutableInteractionSource() }
                IconButton(
                    onClick = onStartVoice,
                    interactionSource = micInteraction,
                    modifier = Modifier
                        .size(42.dp)
                        .pressScale(micInteraction, pressed = 0.9f)
                        .clip(CircleShape)
                        .background(Surface2)
                        .border(1.dp, Hairline, CircleShape)
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = "Voice input", tint = TextHigh, modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Text Input Field
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message Chitti", style = MaterialTheme.typography.bodyMedium, color = TextLow) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Hairline,
                        focusedContainerColor = Surface2,
                        unfocusedContainerColor = Surface2,
                        focusedTextColor = TextHigh,
                        unfocusedTextColor = TextHigh,
                        cursorColor = Accent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Send Button
                val sendInteraction = remember { MutableInteractionSource() }
                FilledIconButton(
                    onClick = { sendMessage(inputText) },
                    interactionSource = sendInteraction,
                    modifier = Modifier
                        .size(42.dp)
                        .pressScale(sendInteraction, pressed = 0.9f),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accent, disabledContainerColor = Surface2),
                    enabled = inputText.isNotBlank() && !isThinking
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun GeminiChatBubble(
    msg: ChatMessage,
    onSpeak: () -> Unit = {}
) {
    val alignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bgColor = if (msg.isUser) Accent else Surface2
    val textColor = if (msg.isUser) Color.White else TextHigh

    // Materialise from where it belongs: a short rise + fade + scale on a settle spring.
    val reduceMotion = rememberReducedMotion()
    val shown = remember { MutableTransitionState(reduceMotion).apply { targetState = true } }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        AnimatedVisibility(
            visibleState = shown,
            enter = fadeIn(tween(160)) +
                slideInVertically(ChittiMotion.settle()) { it / 4 } +
                scaleIn(ChittiMotion.Settle, initialScale = 0.96f)
        ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (msg.isUser) 16.dp else 5.dp,
                bottomEnd = if (msg.isUser) 5.dp else 16.dp
            ),
            color = bgColor,
            border = if (!msg.isUser) androidx.compose.foundation.BorderStroke(1.dp, Hairline) else null,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!msg.isUser && msg.actionLabel != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Mint, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(msg.actionLabel, style = MaterialTheme.typography.labelSmall, color = Mint, fontWeight = FontWeight.SemiBold)
                    }
                }

                Text(
                    text = msg.text,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp)
                )

                if (!msg.isUser) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = onSpeak, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Read aloud", tint = TextLow, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
        }
    }
}
