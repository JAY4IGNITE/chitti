package com.owlcoders.chitti

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.owlcoders.chitti.automation.*
import com.owlcoders.chitti.db.entities.ChatHistoryEntity
import com.owlcoders.chitti.db.entities.Memory
import com.owlcoders.chitti.services.SpeechToTextManager
import com.owlcoders.chitti.services.TtsEngine
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.owlcoders.chitti.ui.components.GeminiVoiceOverlay
import com.owlcoders.chitti.ui.components.pressScale
import com.owlcoders.chitti.ui.components.rememberReducedMotion
import com.owlcoders.chitti.ui.components.VoiceAssistantState
import com.owlcoders.chitti.ui.screens.*
import com.owlcoders.chitti.ui.theme.*
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val icon: ImageVector, val label: String) {
    object Home : Screen("home", Icons.Filled.Home, "Today")
    object Chat : Screen("chat", Icons.Filled.SmartToy, "Chat")
    object Inbox : Screen("inbox", Icons.Filled.Inbox, "Inbox")
    object Dashboard : Screen("dashboard", Icons.Filled.Dashboard, "Stats")
    object Automation : Screen("automation", Icons.Filled.AutoAwesome, "Actions")
    object Memory : Screen("memory", Icons.Filled.Psychology, "Memory")
    object AiLab : Screen("ailab", Icons.Filled.Science, "AI Lab")
    object Settings : Screen("settings", Icons.Filled.Settings, "Settings")
    object Profile : Screen("profile", Icons.Filled.Person, "Profile")
}

class MainActivity : ComponentActivity() {
    private var ttsEngine: TtsEngine? = null
    private var sttManager: SpeechToTextManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) requestBatteryOptimizationExemption()

        val tts = TtsEngine(this)
        ttsEngine = tts

        setContent {
            ChittiTheme {
                val navController = rememberNavController()
                val scope = rememberCoroutineScope()
                val context = LocalContext.current
                val app = application as ChittiApp

                // Automation & Assistant helpers
                val appLauncher = remember { AppLauncher(context) }
                val actionExecutor = remember { ActionExecutor(context, app.database) }
                val dispatcher = remember { AssistantIntentDispatcher(context, appLauncher, tts, actionExecutor) }

                // Assistant Voice State
                val latestEvents = remember { mutableStateOf<List<com.owlcoders.chitti.db.CapturedEvent>>(emptyList()) }
                // Incremented whenever the user starts a new voice session or dismisses the overlay;
                // a processQuery() that finishes for an older generation is discarded.
                var queryGen by remember { mutableIntStateOf(0) }
                var voiceState by remember { mutableStateOf(VoiceAssistantState.IDLE) }
                var transcript by remember { mutableStateOf("") }
                var rmsLevel by remember { mutableFloatStateOf(0.1f) }
                var currentAssistantResponse by remember { mutableStateOf<AssistantResponse?>(null) }

                // Initialize STT Manager
                val stt = remember {
                    SpeechToTextManager(
                        context = context,
                        onPartialResult = { partial ->
                            transcript = partial
                        },
                        onFinalResult = { finalQuery ->
                            transcript = finalQuery
                            voiceState = VoiceAssistantState.THINKING
                            val gen = ++queryGen
                            scope.launch {
                                val response = try {
                                    dispatcher.processQuery(finalQuery, latestEvents.value, shouldSpeak = true)
                                } catch (t: Throwable) {
                                    Log.e("ChittiMain", "processQuery failed: ${t.message}", t)
                                    AssistantResponse(
                                        message = "Something went wrong while doing that. Please try again.",
                                        actionSuccess = false,
                                        actionLabel = "Error"
                                    )
                                }
                                if (gen != queryGen) {
                                    // User dismissed or restarted while we were thinking: drop it.
                                    tts.stop()
                                    return@launch
                                }
                                currentAssistantResponse = response
                                // The dispatcher already started TTS; show SPEAKING only while it
                                // actually speaks. onSpeechFinished below moves us to RESULT.
                                voiceState = if (tts.isSpeaking) VoiceAssistantState.SPEAKING else VoiceAssistantState.RESULT
                            }
                        },
                        onRmsLevel = { rms ->
                            rmsLevel = rms
                        },
                        onErrorMessage = { errMsg ->
                            Log.w("ChittiMain", "STT Error: $errMsg")
                            // Errors can arrive after end-of-speech (e.g. NO_MATCH), when we are
                            // already showing THINKING; a real result never follows those.
                            if (voiceState == VoiceAssistantState.LISTENING || voiceState == VoiceAssistantState.THINKING) {
                                voiceState = VoiceAssistantState.RESULT
                                currentAssistantResponse = AssistantResponse(
                                    message = errMsg,
                                    spokenText = errMsg,
                                    actionSuccess = false
                                )
                            }
                        },
                        onStateChange = { state ->
                            when (state) {
                                SpeechToTextManager.SpeechState.READY,
                                SpeechToTextManager.SpeechState.LISTENING -> {
                                    voiceState = VoiceAssistantState.LISTENING
                                }
                                SpeechToTextManager.SpeechState.PROCESSING -> {
                                    if (voiceState == VoiceAssistantState.LISTENING) {
                                        voiceState = VoiceAssistantState.THINKING
                                    }
                                }
                                else -> {}
                            }
                        }
                    )
                }
                // Side effects belong outside composition (F7); also hook TTS completion once.
                SideEffect { sttManager = stt }
                DisposableEffect(tts) {
                    tts.onSpeechFinished = {
                        if (voiceState == VoiceAssistantState.SPEAKING) voiceState = VoiceAssistantState.RESULT
                    }
                    onDispose { tts.onSpeechFinished = null }
                }

                // Audio permission launcher
                val audioPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        transcript = ""
                        currentAssistantResponse = null
                        voiceState = VoiceAssistantState.LISTENING
                        stt.startListening()
                    } else {
                        voiceState = VoiceAssistantState.RESULT
                        currentAssistantResponse = AssistantResponse(
                            message = "Microphone permission is required to talk to Chitti.",
                            spokenText = "Microphone permission is required to talk to Chitti.",
                            actionSuccess = false
                        )
                    }
                }

                fun startVoiceInput() {
                    queryGen++
                    tts.stop()
                    val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm) {
                        transcript = ""
                        currentAssistantResponse = null
                        voiceState = VoiceAssistantState.LISTENING
                        stt.startListening()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                // ----- Collect Data from Room -----
                val events by app.database.eventDao().getAllEvents().collectAsState(initial = emptyList())
                SideEffect { latestEvents.value = events }
                val tasks by app.database.taskDao().getAllTasks().collectAsState(initial = emptyList())
                val notifications by app.database.notificationDao().getAllNotifications().collectAsState(initial = emptyList())
                val memories by app.database.memoryDao().getAllMemories().collectAsState(initial = emptyList())
                val memoryCategories by app.database.memoryDao().getCategories().collectAsState(initial = emptyList())
                val automationHistory by app.database.automationHistoryDao().getRecentHistory(100).collectAsState(initial = emptyList())
                val chatHistory by app.database.chatHistoryDao().getAllMessages().collectAsState(initial = emptyList())

                var notificationCount by remember { mutableIntStateOf(0) }
                var memoryCount by remember { mutableIntStateOf(0) }
                var chatMessageCount by remember { mutableIntStateOf(0) }
                var automationHistoryCount by remember { mutableIntStateOf(0) }

                LaunchedEffect(notifications) { notificationCount = notifications.size }
                LaunchedEffect(memories) { memoryCount = memories.size }
                LaunchedEffect(chatHistory) { chatMessageCount = chatHistory.size }
                LaunchedEffect(automationHistory) { automationHistoryCount = automationHistory.size }

                var hasNotificationAccess by remember { mutableStateOf(isNotificationServiceEnabled()) }

                // Onboarding state
                var isOnboardingComplete by remember {
                    mutableStateOf(
                        com.owlcoders.chitti.ui.screens.checkMicPermission(context) &&
                        com.owlcoders.chitti.ui.screens.checkNotificationPermission(context) &&
                        com.owlcoders.chitti.ui.screens.checkAccessibilityPermission(context)
                    )
                }

                // Re-check system-settings state when the user comes back from Settings.
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            hasNotificationAccess = isNotificationServiceEnabled()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                // Main App Structure
                Box(modifier = Modifier.fillMaxSize().background(GeminiDarkBg)) {
                    if (isOnboardingComplete) {
                        ChittiScaffold(
                            navController = navController,
                            onMicClick = { startVoiceInput() }
                        ) { innerPadding ->
                            NavHost(
                                navController = navController,
                                startDestination = Screen.Home.route,
                                modifier = Modifier.padding(innerPadding)
                            ) {
                                composable(Screen.Home.route) {
                                    TodayScreen(
                                        events = events,
                                        onDeleteEvent = { event ->
                                            scope.launch { app.database.eventDao().deleteEvent(event) }
                                        },
                                        onQuickAction = { actionQuery ->
                                            scope.launch {
                                                val resp = dispatcher.processQuery(actionQuery, events, shouldSpeak = true)
                                                currentAssistantResponse = resp
                                                transcript = actionQuery
                                                voiceState = VoiceAssistantState.RESULT
                                            }
                                        }
                                    )
                                }


                                composable(Screen.Chat.route) {
                                    ChatBotScreen(
                                        events = events,
                                        chatHistory = chatHistory,
                                        memories = memories,
                                        dispatcher = dispatcher,
                                        ttsEngine = tts,
                                        onSaveMessage = { message ->
                                            scope.launch { app.database.chatHistoryDao().insertMessage(message) }
                                        },
                                        onStartVoice = { startVoiceInput() }
                                    )
                                }

                                composable(Screen.Inbox.route) {
                                    InboxScreen(
                                        notifications = notifications,
                                        onMarkProcessed = { notification ->
                                            scope.launch { app.database.notificationDao().markProcessed(notification.id) }
                                        },
                                        onDelete = { notification ->
                                            scope.launch { app.database.notificationDao().deleteNotification(notification) }
                                        }
                                    )
                                }

                                composable(Screen.Dashboard.route) {
                                    DashboardScreen(
                                        events = events,
                                        tasks = tasks,
                                        notificationCount = notificationCount,
                                        memoryCount = memoryCount,
                                        automationCount = automationHistoryCount
                                    )
                                }

                                composable(Screen.Automation.route) {
                                    AutomationScreen(history = automationHistory)
                                }

                                composable(Screen.Memory.route) {
                                    MemoryScreen(
                                        memories = memories,
                                        categories = memoryCategories,
                                        onAddMemory = { key, value, category ->
                                            scope.launch {
                                                val dao = app.database.memoryDao()
                                                val existing = dao.findMemory(key, category)
                                                if (existing != null) {
                                                    dao.updateMemory(existing.copy(value = value, updatedAt = System.currentTimeMillis()))
                                                } else {
                                                    dao.insertMemory(Memory(key = key, value = value, category = category))
                                                }
                                            }
                                        },
                                        onDeleteMemory = { memory ->
                                            scope.launch { app.database.memoryDao().deleteMemory(memory) }
                                        },
                                        onUpdateMemory = { memory ->
                                            scope.launch { app.database.memoryDao().updateMemory(memory) }
                                        }
                                    )
                                }

                                composable(Screen.AiLab.route) {
                                    AiLabScreen(
                                        onAddEvent = { event, task ->
                                            scope.launch {
                                                app.database.eventDao().insertEvent(event)
                                                app.database.taskDao().insertTask(task)
                                            }
                                        }
                                    )
                                }

                                composable(Screen.Settings.route) {
                                    SettingsScreen(
                                        hasNotificationAccess = hasNotificationAccess,
                                        onWipeData = {
                                            scope.launch {
                                                app.database.eventDao().deleteAllEvents()
                                                app.database.notificationDao().deleteAllNotifications()
                                                app.database.chatHistoryDao().deleteAllMessages()
                                                app.database.automationHistoryDao().deleteAllHistory()
                                                // Cancel armed alarms before dropping their rows
                                                app.database.reminderDao().getUpcoming(0L).forEach {
                                                    ReminderScheduler.cancel(context, it.taskId, "")
                                                }
                                                app.database.reminderDao().deleteAll()
                                                app.database.taskDao().deleteAllTasks()
                                                app.database.memoryDao().deleteAllMemories()
                                                app.replyIntents.clear()
                                            }
                                        },
                                        eventCount = events.size,
                                        taskCount = tasks.size,
                                        notificationCount = notificationCount,
                                        memoryCount = memoryCount,
                                        chatMessageCount = chatMessageCount,
                                        automationHistoryCount = automationHistoryCount,
                                        onClearNotifications = { scope.launch { app.database.notificationDao().deleteAllNotifications() } },
                                        onClearChatHistory = { scope.launch { app.database.chatHistoryDao().deleteAllMessages() } },
                                        onClearAutomationHistory = { scope.launch { app.database.automationHistoryDao().deleteAllHistory() } },
                                        onClearMemories = { scope.launch { app.database.memoryDao().deleteAllMemories() } }
                                    )
                                }

                                composable(Screen.Profile.route) {
                                    com.owlcoders.chitti.ui.settings.ProfileScreen()
                                }
                            }
                        }
                    } else {
                        com.owlcoders.chitti.ui.screens.PermissionsOnboardingScreen(
                            onAllPermissionsGranted = {
                                isOnboardingComplete = true
                            }
                        )
                    }

                    // Global Gemini Voice Overlay
                    GeminiVoiceOverlay(
                        state = voiceState,
                        transcript = transcript,
                        rmsLevel = rmsLevel,
                        assistantResponse = currentAssistantResponse,
                        onMicClick = { startVoiceInput() },
                        onDismiss = {
                            queryGen++
                            stt.stopListening()
                            tts.stop()
                            voiceState = VoiceAssistantState.IDLE
                        },
                        onStopSpeech = {
                            tts.stop()
                            voiceState = VoiceAssistantState.RESULT
                        },
                        onDocumentClick = { uriStr ->
                            try {
                                val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(viewIntent)
                            } catch (e: Exception) {
                                Log.e("ChittiMain", "Could not open document: ${e.message}")
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sttManager?.destroy()
        ttsEngine?.shutdown()
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        // Ask once per install, not on every launch/rotation: the system dialog is disruptive.
        val prefs = getSharedPreferences("chitti_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("battery_exemption_asked", false)) return
        prefs.edit().putBoolean("battery_exemption_asked", true).apply()
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.w("ChittiMain", "Battery optimisation dialog unavailable: ${e.message}")
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val packageNames = NotificationManagerCompat.getEnabledListenerPackages(this)
        return packageNames.contains(packageName)
    }
}

@Composable
fun ChittiScaffold(
    navController: NavHostController,
    onMicClick: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val drawerScreens = listOf(Screen.Dashboard, Screen.Automation, Screen.Memory, Screen.AiLab, Screen.Settings, Screen.Profile)
    var showMoreMenu by remember { mutableStateOf(false) }

    val reduceMotion = rememberReducedMotion()
    val infiniteTransition = rememberInfiniteTransition(label = "micHalo")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (reduceMotion) 1f else 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Scaffold(
        containerColor = GeminiDarkBg,
        bottomBar = {
            Column {
                // Light catching the top edge of the material instead of a hard divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, GeminiBorder, GeminiCyan.copy(alpha = 0.35f), GeminiBorder, Color.Transparent)))
                )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = GeminiSurface.copy(alpha = 0.96f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Home / Today
                    BottomNavItem(
                        screen = Screen.Home,
                        isSelected = currentRoute == Screen.Home.route,
                        onClick = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )

                    // 2. Inbox
                    BottomNavItem(
                        screen = Screen.Inbox,
                        isSelected = currentRoute == Screen.Inbox.route,
                        onClick = {
                            navController.navigate(Screen.Inbox.route) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )

                    // 3. Center Glowing Gemini Assistant Mic Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.offset(y = (-10).dp)
                    ) {
                        // Pulsing ambient halo
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(GeminiCyan.copy(alpha = 0.5f), GeminiBlue.copy(alpha = 0.3f), Color.Transparent)
                                    )
                                )
                        )

                        val micInteraction = remember { MutableInteractionSource() }
                        Surface(
                            modifier = Modifier
                                .size(52.dp)
                                .pressScale(micInteraction, pressed = 0.9f)
                                .clip(CircleShape)
                                .clickable(interactionSource = micInteraction, indication = null, onClick = onMicClick),
                            shape = CircleShape,
                            color = GeminiSurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(2.dp, GeminiGradient),
                            shadowElevation = 10.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = "Assistant Voice",
                                    tint = GeminiCyan,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }

                    // 4. AI Chat
                    BottomNavItem(
                        screen = Screen.Chat,
                        isSelected = currentRoute == Screen.Chat.route,
                        onClick = {
                            navController.navigate(Screen.Chat.route) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )

                    // 5. More Menu
                    Box {
                        val moreInteraction = remember { MutableInteractionSource() }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .pressScale(moreInteraction, pressed = 0.92f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(interactionSource = moreInteraction, indication = null) { showMoreMenu = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.MoreHoriz, contentDescription = "More", tint = TextSecondary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("More", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            modifier = Modifier.background(GeminiSurfaceElevated).border(1.dp, GeminiBorder, RoundedCornerShape(12.dp))
                        ) {
                            drawerScreens.forEach { screen ->
                                DropdownMenuItem(
                                    text = { Text(screen.label, color = TextPrimary) },
                                    leadingIcon = { Icon(screen.icon, contentDescription = screen.label, tint = GeminiCyan) },
                                    onClick = {
                                        showMoreMenu = false
                                        navController.navigate(screen.route) {
                                            popUpTo(Screen.Home.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            }
        },
        content = content
    )
}

@Composable
fun BottomNavItem(
    screen: Screen,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val tint by animateColorAsState(if (isSelected) GeminiCyan else TextSecondary, label = "navTint")
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .pressScale(interaction, pressed = 0.92f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(screen.icon, contentDescription = screen.label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(screen.label, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}
