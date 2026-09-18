package com.owlcoders.chitti.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
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
import com.owlcoders.chitti.db.entities.Task
import com.owlcoders.chitti.services.ExtractedData
import com.owlcoders.chitti.services.ImportanceScorer
import com.owlcoders.chitti.services.NotificationFilter
import com.owlcoders.chitti.ui.theme.LightScreenBg
import com.owlcoders.chitti.ui.theme.LightScreenInk
import kotlinx.coroutines.launch

/**
 * Hard cap on simulator input. The few-shot prompt already uses a few hundred
 * tokens and MediaPipe aborts the process natively (not catchable) when the prompt
 * exceeds max tokens, so unbounded pasted text must never reach extract().
 */
const val AI_LAB_MAX_INPUT_CHARS = 400

data class SimulationResult(
    val rawText: String,
    val passedFilter: Boolean,
    val extracted: ExtractedData?,
    val importanceScore: Int,
    val priority: Int,
    val latencyMs: Long,
    val mode: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiLabScreen(
    onAddEvent: (CapturedEvent, Task) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val app = context.applicationContext as ChittiApp
    val scope = rememberCoroutineScope()

    var testInput by remember { mutableStateOf("Record submission tomorrow 10 AM, Lab 2.") }
    var isRunning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SimulationResult?>(null) }
    var smartReply by remember { mutableStateOf<String?>(null) }
    var isGeneratingReply by remember { mutableStateOf(false) }
    var isSaved by remember { mutableStateOf(false) }

    val presets = listOf(
        "Record submission tomorrow 10 AM, Lab 2.",
        "25th lopu fee kattali, marchipovaddu.",
        "Submit the hackathon deck by 10am tomorrow",
        "Team standup meeting today at 4 PM on Google Meet",
        "Hey bro, are you free this weekend?"
    )

    // Light screen: dark content colour so uncoloured Text/Icon read on white cards.
    CompositionLocalProvider(LocalContentColor provides LightScreenInk) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightScreenBg)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF00695C))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "🧪 AI Performance Lab & Simulator",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = "Benchmark on-device inference, latency, and simulate notifications per plan.md §14",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Hardware & Engine Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Memory, contentDescription = "Engine", tint = Color(0xFF00695C))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hardware & Inference Profile",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val isLlmLoaded = app.extractionEngine?.isLlmLoaded() == true
                val mode = app.extractionEngine?.lastInferenceMode ?: if (isLlmLoaded) "Gemma 2B (On-Device)" else "Rule-based Regex"
                val latency = app.extractionEngine?.lastInferenceLatencyMs ?: 0L

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Model Backend:", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Text(mode, fontWeight = FontWeight.SemiBold, color = Color(0xFF00695C))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Execution Mode:", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Text("On-Device (CPU / GPU / NPU-Ready)", fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Privacy & Offline:", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Text("100% Local (Airplane Mode Safe)", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Last Latency:", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Text(if (latency > 0) "${latency}ms" else "Ready", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                }
            }
        }

        // Preset Test Scenarios
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Sample Test Scenarios (plan.md §14)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                presets.forEach { preset ->
                    OutlinedButton(
                        onClick = {
                            testInput = preset
                            isSaved = false
                            smartReply = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = preset,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Select", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Custom Input & Simulator Action
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Notification Simulator",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = testInput,
                    onValueChange = {
                        testInput = it.take(AI_LAB_MAX_INPUT_CHARS)
                        isSaved = false
                        smartReply = null
                    },
                    label = { Text("Notification Text") },
                    supportingText = {
                        Text("${testInput.length} / $AI_LAB_MAX_INPUT_CHARS characters")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    // This field sits inside a white Card; the dark scheme's onSurface
                    // (near-white) would make typed text invisible.
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LightScreenInk,
                        unfocusedTextColor = LightScreenInk,
                        cursorColor = LightScreenInk,
                        focusedLabelColor = Color(0xFF00695C),
                        unfocusedLabelColor = Color.Gray,
                        focusedSupportingTextColor = Color.Gray,
                        unfocusedSupportingTextColor = Color.Gray,
                        focusedBorderColor = Color(0xFF00695C),
                        unfocusedBorderColor = Color(0xFFBDBDBD)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        isRunning = true
                        isSaved = false
                        smartReply = null
                        // Clamp again at the call site so nothing longer than the cap
                        // can reach the LLM prompt even if state was set another way.
                        val input = testInput.take(AI_LAB_MAX_INPUT_CHARS)
                        // extract() is a suspend fun that switches to IO internally;
                        // it must stay inside the composable scope (no runBlocking).
                        scope.launch {
                            try {
                                val startTime = System.currentTimeMillis()
                                val passesFilter = NotificationFilter.shouldProcess(input)

                                val engine = app.extractionEngine
                                val extracted = if (passesFilter) {
                                    engine?.extract(input)
                                } else null

                                val latency = System.currentTimeMillis() - startTime

                                val scoreFloat = if (extracted != null && extracted.what.isNotBlank()) {
                                    ImportanceScorer.score(
                                        extractedWhat = extracted.what,
                                        extractedWhen = extracted.whenTime,
                                        urgency = extracted.urgency,
                                        category = extracted.category,
                                        rawText = input
                                    )
                                } else 0f

                                val priority = ImportanceScorer.toPriority(scoreFloat)
                                val scoreInt = (scoreFloat * 100).toInt()
                                val mode = engine?.lastInferenceMode ?: "Rule-based Regex"

                                result = SimulationResult(
                                    rawText = input,
                                    passedFilter = passesFilter,
                                    extracted = extracted,
                                    importanceScore = scoreInt,
                                    priority = priority,
                                    latencyMs = latency,
                                    mode = mode
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, "Simulation failed: ${e.message ?: "unknown error"}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isRunning = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRunning && testInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C))
                ) {
                    if (isRunning) {
                        com.owlcoders.chitti.ui.components.GeminiCircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Running AI Pipeline...")
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Run")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simulate & Analyze Notification")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Results Card
        val simResult = result
        if (simResult != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Pipeline Analysis Results",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00695C)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Stage 1: Filter
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Stage 1 (Pre-Filter): ", fontWeight = FontWeight.SemiBold)
                        if (simResult.passedFilter) {
                            Text("PASSED", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                        } else {
                            Text("REJECTED (Chat/Noise)", color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Latency: ", fontWeight = FontWeight.SemiBold)
                        Text("${simResult.latencyMs} ms", color = Color(0xFFE65100), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Engine: ", fontWeight = FontWeight.SemiBold)
                        Text(simResult.mode, color = Color(0xFF1565C0))
                    }

                    val ext = simResult.extracted
                    if (ext != null && ext.what.isNotBlank()) {
                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        Text("Stage 2 (Entity Extraction):", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("• What: ${ext.what}", fontWeight = FontWeight.SemiBold)
                        Text("• When: ${ext.whenTime}")
                        Text("• Who: ${ext.who}")
                        Text("• Category: ${ext.category}")
                        Text("• Urgency: ${ext.urgency}")
                        Text("• Confidence: ${(ext.confidence * 100).toInt()}%")

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        Text("Stage 3 (Scoring & Priority):", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("• Importance Score: ${simResult.importanceScore}/100")
                        Text(
                            "• Priority Level: ${when (simResult.priority) { 2 -> "High"; 1 -> "Medium"; else -> "Low" }}",
                            color = when (simResult.priority) { 2 -> Color(0xFFC62828); 1 -> Color(0xFFEF6C00); else -> Color(0xFF2E7D32) },
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Actions
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val event = CapturedEvent(
                                        sourceApp = "com.owlcoders.chitti.sim",
                                        rawText = simResult.rawText,
                                        extractedWhat = ext.what,
                                        extractedWhen = ext.whenTime,
                                        extractedWho = ext.who,
                                        category = ext.category,
                                        urgency = ext.urgency,
                                        status = "extracted",
                                        timestamp = System.currentTimeMillis()
                                    )
                                    val task = Task(
                                        title = ext.what,
                                        description = "Details: ${simResult.rawText}\nWhen: ${ext.whenTime}\nCategory: ${ext.category}",
                                        status = "pending",
                                        priority = simResult.priority,
                                        sourceType = "simulator",
                                        sourceId = 0
                                    )
                                    onAddEvent(event, task)
                                    isSaved = true
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isSaved,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Add")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isSaved) "Saved!" else "Add to Tasks")
                            }

                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_INSERT).apply {
                                        data = CalendarContract.Events.CONTENT_URI
                                        putExtra(CalendarContract.Events.TITLE, ext.what)
                                        putExtra(CalendarContract.Events.DESCRIPTION, simResult.rawText)
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: ActivityNotFoundException) {
                                        Toast.makeText(context, "No calendar app found", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Event, contentDescription = "Calendar")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Calendar")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            enabled = !isGeneratingReply,
                            onClick = {
                                isGeneratingReply = true
                                // generateSmartReply is a suspend fun (IO internally); keep it
                                // on the composable scope with a visible loading state.
                                scope.launch {
                                    try {
                                        val dummyEvent = CapturedEvent(
                                            sourceApp = "simulator",
                                            rawText = simResult.rawText,
                                            extractedWhat = ext.what,
                                            extractedWhen = ext.whenTime,
                                            extractedWho = ext.who,
                                            category = ext.category,
                                            urgency = ext.urgency,
                                            status = "extracted",
                                            timestamp = System.currentTimeMillis()
                                        )
                                        smartReply = app.extractionEngine?.generateSmartReply(dummyEvent)
                                            ?: "Engine not ready yet"
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Could not generate reply", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isGeneratingReply = false
                                    }
                                }
                            }
                        ) {
                            if (isGeneratingReply) {
                                com.owlcoders.chitti.ui.components.GeminiCircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF673AB7))
                            } else {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = "Reply", tint = Color(0xFF673AB7))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isGeneratingReply) "Generating..." else "Generate Smart Reply", color = Color(0xFF673AB7))
                        }

                        val reply = smartReply
                        if (reply != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFEDE7F6),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Text(
                                    text = "Smart Reply: $reply",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF4A148C)
                                )
                            }
                        }
                    } else if (!simResult.passedFilter || (ext != null && ext.what.isBlank())) {
                        Divider(modifier = Modifier.padding(vertical = 12.dp))
                        Text(
                            "Result: Content filtered out as non-actionable chat noise. No tasks or commitments detected.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
    }
}
