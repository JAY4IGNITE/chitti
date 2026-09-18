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
import com.owlcoders.chitti.ui.components.GeminiVoiceOverlay
import com.owlcoders.chitti.ui.components.VoiceAssistantState
import com.owlcoders.chitti.ui.screens.*
import com.owlcoders.chitti.ui.theme.*
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val icon: ImageVector, val label: String) {
    object Home : Screen("home", Icons.Filled.Home, "Today")
    object Files : Screen("files", Icons.Filled.FolderOpen, "Files")
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
        requestBatteryOptimizationExemption()

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
                val documentFinder = remember { DocumentFinder(context, app.database) }
                val fileFinder = remember { FileFinder(context, app.database) }
                val dispatcher = remember { AssistantIntentDispatcher(context, appLauncher, fileFinder, documentFinder, tts) }

                // Assistant Voice State
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
                            scope.launch {
                                val response = dispatcher.processQuery(finalQuery, emptyList(), shouldSpeak = true)
                                currentAssistantResponse = response
                                voiceState = VoiceAssistantState.SPEAKING
                            }
                        },
                        onRmsLevel = { rms ->
                            rmsLevel = rms
                        },
                        onErrorMessage = { errMsg ->
                            Log.w("ChittiMain", "STT Error: $errMsg")
                            if (voiceState == VoiceAssistantState.LISTENING) {
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
                sttManager = stt

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
                val tasks by app.database.taskDao().getAllTasks().collectAsState(initial = emptyList())
                val notifications by app.database.notificationDao().getAllNotifications().collectAsState(initial = emptyList())
                val memories by app.database.memoryDao().getAllMemories().collectAsState(initial = emptyList())
                val memoryCategories by app.database.memoryDao().getCategories().collectAsState(initial = emptyList())
                val documents by app.database.documentDao().getAllDocuments().collectAsState(initial = emptyList())
                val automationHistory by app.database.automationHistoryDao().getRecentHistory(100).collectAsState(initial = emptyList())
                val chatHistory by app.database.chatHistoryDao().getAllMessages().collectAsState(initial = emptyList())

                var notificationCount by remember { mutableIntStateOf(0) }
                var memoryCount by remember { mutableIntStateOf(0) }
                var documentCount by remember { mutableIntStateOf(0) }
                var chatMessageCount by remember { mutableIntStateOf(0) }
                var automationHistoryCount by remember { mutableIntStateOf(0) }

                LaunchedEffect(notifications) { notificationCount = notifications.size }
                LaunchedEffect(memories) { memoryCount = memories.size }
                LaunchedEffect(documents) { documentCount = documents.size }
                LaunchedEffect(chatHistory) { chatMessageCount = chatHistory.size }
                LaunchedEffect(automationHistory) { automationHistoryCount = automationHistory.size }

                var hasNotificationAccess by remember { mutableStateOf(isNotificationServiceEnabled()) }
                
                // Onboarding state
                var isOnboardingComplete by remember { 
                    mutableStateOf(
                        com.owlcoders.chitti.ui.screens.checkMicPermission(context) &&
                        com.owlcoders.chitti.ui.screens.checkNotificationPermission(context) &&
                        com.owlcoders.chitti.ui.screens.checkAccessibilityPermission()
                    ) 
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

                                composable(Screen.Files.route) {
                                    FilesScreen(
                                        fileFinder = fileFinder,
                                        onImportUri = { uri ->
                                            scope.launch {
                                                documentFinder.importDocumentUri(uri)
                                            }
                                        },
                                        onScanBitmap = { bitmap ->
                                            scope.launch {
                                                documentFinder.importBitmapFromCamera(bitmap)
                                            }
                                        },
                                        onOpenFile = { file ->
                                            fileFinder.openFile(file)
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
                                        documentCount = documentCount,
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
                                                app.database.memoryDao().insertMemory(Memory(key = key, value = value, category = category))
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
                                                app.database.documentDao().getAllDocuments()
                                            }
                                        },
                                        eventCount = events.size,
                                        taskCount = tasks.size,
                                        notificationCount = notificationCount,
                                        memoryCount = memoryCount,
                                        documentCount = documentCount,
                                        chatMessageCount = chatMessageCount,
                                        automationHistoryCount = automationHistoryCount,
                                        onClearNotifications = { scope.launch { app.database.notificationDao().deleteAllNotifications() } },
                                        onClearChatHistory = { scope.launch { app.database.chatHistoryDao().deleteAllMessages() } },
                                        onClearAutomationHistory = { scope.launch { app.database.automationHistoryDao().deleteAllHistory() } },
                                        onClearMemories = { scope.launch { memories.forEach { app.database.memoryDao().deleteMemory(it) } } }
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
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
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

    val drawerScreens = listOf(Screen.Inbox, Screen.Dashboard, Screen.Automation, Screen.Memory, Screen.AiLab, Screen.Settings, Screen.Profile)
    var showMoreMenu by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Scaffold(
        containerColor = GeminiDarkBg,
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = GeminiSurface,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, GeminiBorder)
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

                    // 2. Files Finder
                    BottomNavItem(
                        screen = Screen.Files,
                        isSelected = currentRoute == Screen.Files.route,
                        onClick = {
                            navController.navigate(Screen.Files.route) {
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

                        Surface(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onMicClick),
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showMoreMenu = true }
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
    val tint = if (isSelected) GeminiCyan else TextSecondary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(screen.icon, contentDescription = screen.label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(screen.label, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}
