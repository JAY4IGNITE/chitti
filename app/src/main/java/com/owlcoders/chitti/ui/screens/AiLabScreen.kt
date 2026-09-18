package com.owlcoders.chitti.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.ChittiApp
import com.owlcoders.chitti.db.CapturedEvent
import com.owlcoders.chitti.db.entities.Task
import com.owlcoders.chitti.services.ExtractedData
import com.owlcoders.chitti.services.ImportanceScorer
import com.owlcoders.chitti.services.NotificationFilter
import com.owlcoders.chitti.ui.components.ChittiSurfaceCard
import com.owlcoders.chitti.ui.components.ChittiTextField
import com.owlcoders.chitti.ui.components.CountUpText
import com.owlcoders.chitti.ui.components.HairlineDivider
import com.owlcoders.chitti.ui.components.LatticeLoader
import com.owlcoders.chitti.ui.components.LatticeStatus
import com.owlcoders.chitti.ui.components.ListRow
import com.owlcoders.chitti.ui.components.PrimaryButton
import com.owlcoders.chitti.ui.components.ScreenHeader
import com.owlcoders.chitti.ui.components.ScreenScaffold
import com.owlcoders.chitti.ui.components.SecondaryButton
import com.owlcoders.chitti.ui.components.SectionLabel
import com.owlcoders.chitti.ui.components.Space
import com.owlcoders.chitti.ui.components.StatusPill
import com.owlcoders.chitti.ui.components.staggeredEntrance
import com.owlcoders.chitti.ui.theme.Accent
import com.owlcoders.chitti.ui.theme.Amber
import com.owlcoders.chitti.ui.theme.Iris
import com.owlcoders.chitti.ui.theme.Mint
import com.owlcoders.chitti.ui.theme.Rose
import com.owlcoders.chitti.ui.theme.Sky
import com.owlcoders.chitti.ui.theme.TextHigh
import com.owlcoders.chitti.ui.theme.TextLow
import com.owlcoders.chitti.ui.theme.TextMid
import com.owlcoders.chitti.ui.theme.categoryColor
import com.owlcoders.chitti.ui.theme.priorityColor
import com.owlcoders.chitti.ui.theme.urgencyColor
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

/** Sample notifications: the short button label paired with the text it loads. */
private val AI_LAB_SAMPLES = listOf(
    "Submission" to "Record submission tomorrow 10 AM, Lab 2.",
    "Fee (code-mixed)" to "25th lopu fee kattali, marchipovaddu.",
    "Deck deadline" to "Submit the hackathon deck by 10am tomorrow",
    "Standup" to "Team standup meeting today at 4 PM on Google Meet",
    "Casual chat" to "Hey bro, are you free this weekend?"
)

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

    val engine = app.extractionEngine
    val simResult = result
    // Read the engine readouts through `result` first so the rows refresh after a run: the
    // engine's own fields are plain vars and never trigger recomposition on their own.
    val backend = simResult?.mode
        ?: engine?.lastInferenceMode
        ?: if (engine?.isLlmLoaded() == true) "Gemma 2B" else "Rule-based regex"
    val latencyMs = simResult?.latencyMs ?: engine?.lastInferenceLatencyMs ?: 0L

    ScreenScaffold {
        ScreenHeader(
            title = "AI Lab",
            subtitle = "Test the on-device pipeline"
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // ------------------------------------------------------------------- Engine
            SectionLabel("Engine")

            ChittiSurfaceCard(
                modifier = Modifier
                    .padding(horizontal = Space.gutter)
                    .staggeredEntrance(0),
                contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.xs)
            ) {
                ListRow(
                    title = "Model backend",
                    icon = Icons.Filled.Memory,
                    iconTint = Accent,
                    trailing = {
                        Text(
                            text = backend,
                            style = MaterialTheme.typography.labelLarge,
                            color = TextMid
                        )
                    }
                )
                HairlineDivider(inset = 44.dp)
                ListRow(
                    title = "Execution mode",
                    icon = Icons.Filled.DeveloperBoard,
                    iconTint = Iris,
                    trailing = { StatusPill(text = "On-device", tint = Sky) }
                )
                HairlineDivider(inset = 44.dp)
                ListRow(
                    title = "Last latency",
                    icon = Icons.Filled.Speed,
                    iconTint = Amber,
                    trailing = {
                        if (latencyMs > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CountUpText(
                                    target = latencyMs.toInt(),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextHigh
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = "ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextLow
                                )
                            }
                        } else {
                            Text(
                                text = "Not run yet",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextLow
                            )
                        }
                    }
                )
                HairlineDivider(inset = 44.dp)
                ListRow(
                    title = "Network",
                    subtitle = "Inference runs without a connection",
                    icon = Icons.Filled.CloudOff,
                    iconTint = Sky,
                    trailing = { StatusPill(text = "Offline", tint = Sky) }
                )
            }

            // ---------------------------------------------------------------- Simulator
            SectionLabel("Simulator")

            ChittiSurfaceCard(
                modifier = Modifier
                    .padding(horizontal = Space.gutter)
                    .staggeredEntrance(1)
            ) {
                ChittiTextField(
                    value = testInput,
                    onValueChange = {
                        testInput = it.take(AI_LAB_MAX_INPUT_CHARS)
                        isSaved = false
                        smartReply = null
                    },
                    placeholder = "Paste or type a notification",
                    label = "Notification text",
                    singleLine = false,
                    supportingText = "${testInput.length} / $AI_LAB_MAX_INPUT_CHARS characters"
                )

                Spacer(Modifier.height(Space.m))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.s)
                ) {
                    AI_LAB_SAMPLES.forEach { sample ->
                        SecondaryButton(
                            text = sample.first,
                            onClick = {
                                testInput = sample.second.take(AI_LAB_MAX_INPUT_CHARS)
                                isSaved = false
                                smartReply = null
                            }
                        )
                    }
                }

                Spacer(Modifier.height(Space.l))

                if (isRunning) {
                    LatticeLoader(
                        status = LatticeStatus.WORKING,
                        label = "Running extraction",
                        color = Accent,
                        modifier = Modifier.padding(vertical = Space.s)
                    )
                } else {
                    PrimaryButton(
                        text = "Run extraction",
                        icon = Icons.Filled.PlayArrow,
                        enabled = testInput.isNotBlank(),
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

                                    val activeEngine = app.extractionEngine
                                    val extracted = if (passesFilter) {
                                        activeEngine?.extract(input)
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
                                    val mode = activeEngine?.lastInferenceMode ?: "Rule-based regex"

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
                                    Toast.makeText(
                                        context,
                                        "Simulation failed: ${e.message ?: "unknown error"}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } finally {
                                    isRunning = false
                                }
                            }
                        }
                    )
                }
            }

            // ------------------------------------------------------------------- Result
            if (simResult != null) {
                val ext = simResult.extracted
                val hasExtraction = ext != null && ext.what.isNotBlank()

                SectionLabel("Result")

                ChittiSurfaceCard(
                    modifier = Modifier
                        .padding(horizontal = Space.gutter)
                        .staggeredEntrance(2)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Space.s),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            text = if (simResult.passedFilter) "PASSED" else "REJECTED",
                            tint = if (simResult.passedFilter) Mint else Rose,
                            icon = if (simResult.passedFilter) Icons.Filled.CheckCircle else Icons.Filled.Block
                        )
                        if (ext != null && ext.category.isNotBlank()) {
                            StatusPill(text = ext.category, tint = categoryColor(ext.category))
                        }
                        if (ext != null && ext.urgency.isNotBlank()) {
                            StatusPill(text = ext.urgency + " urgency", tint = urgencyColor(ext.urgency))
                        }
                    }

                    if (ext != null && hasExtraction) {
                        Spacer(Modifier.height(Space.s))
                        HairlineDivider()

                        ListRow(
                            title = "Task",
                            subtitle = ext.what,
                            icon = Icons.Filled.TaskAlt,
                            iconTint = categoryColor(ext.category)
                        )
                        HairlineDivider(inset = 44.dp)
                        ListRow(
                            title = "Time",
                            subtitle = ext.whenTime.ifBlank { "Not specified" },
                            icon = Icons.Filled.Schedule,
                            iconTint = Sky
                        )
                        HairlineDivider(inset = 44.dp)
                        ListRow(
                            title = "Who",
                            subtitle = ext.who.ifBlank { "Not specified" },
                            icon = Icons.Filled.Person,
                            iconTint = Iris
                        )
                        HairlineDivider(inset = 44.dp)
                        ListRow(
                            title = "Confidence",
                            icon = Icons.Filled.Speed,
                            iconTint = Accent,
                            trailing = {
                                Text(
                                    text = "${(ext.confidence * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextMid
                                )
                            }
                        )
                        HairlineDivider(inset = 44.dp)
                        ListRow(
                            title = "Importance",
                            icon = Icons.Filled.Memory,
                            iconTint = priorityColor(simResult.priority),
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CountUpText(
                                        target = simResult.importanceScore,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextHigh
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        text = "/ 100",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextLow
                                    )
                                }
                            }
                        )
                        HairlineDivider(inset = 44.dp)
                        ListRow(
                            title = "Priority",
                            icon = Icons.Filled.Event,
                            iconTint = priorityColor(simResult.priority),
                            trailing = {
                                StatusPill(
                                    text = when (simResult.priority) {
                                        2 -> "High"
                                        1 -> "Medium"
                                        else -> "Low"
                                    },
                                    tint = priorityColor(simResult.priority)
                                )
                            }
                        )

                        Spacer(Modifier.height(Space.l))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Space.s)
                        ) {
                            PrimaryButton(
                                text = if (isSaved) "Added" else "Add to my agenda",
                                icon = Icons.Filled.Add,
                                enabled = !isSaved,
                                modifier = Modifier.weight(1f),
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
                                }
                            )

                            SecondaryButton(
                                text = "Calendar",
                                icon = Icons.Filled.Event,
                                modifier = Modifier.weight(1f),
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
                                }
                            )
                        }
                    } else {
                        Spacer(Modifier.height(Space.m))
                        Text(
                            text = "Filtered as non-actionable chat noise. No task or commitment was detected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMid
                        )
                    }
                }

                // -------------------------------------------------------------- Smart reply
                if (ext != null && hasExtraction) {
                    SectionLabel("Smart reply")

                    ChittiSurfaceCard(
                        modifier = Modifier
                            .padding(horizontal = Space.gutter)
                            .staggeredEntrance(3)
                    ) {
                        Text(
                            text = "Draft a short reply for this notification, on-device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMid
                        )
                        Spacer(Modifier.height(Space.m))

                        if (isGeneratingReply) {
                            LatticeLoader(
                                status = LatticeStatus.WORKING,
                                label = "Writing reply",
                                color = Accent,
                                modifier = Modifier.padding(vertical = Space.xs)
                            )
                        } else {
                            SecondaryButton(
                                text = "Generate reply",
                                icon = Icons.Filled.AutoAwesome,
                                fill = true,
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
                            )
                        }

                        val reply = smartReply
                        if (reply != null) {
                            Spacer(Modifier.height(Space.m))
                            HairlineDivider()
                            Spacer(Modifier.height(Space.m))
                            Text(
                                text = reply,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextHigh
                            )
                        }
                    }
                }
            }
        }
    }
}
