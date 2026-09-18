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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.navigation.NavBackStackEntry
import com.owlcoders.chitti.ui.components.ChittiMotion
import com.owlcoders.chitti.ui.components.Space
import com.owlcoders.chitti.ui.components.rememberHaptics
import com.owlcoders.chitti.ui.components.spotlight
import com.owlcoders.chitti.ui.components.staggeredEntrance
import kotlin.math.roundToInt
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

/** [icon] is the resting (outlined) glyph, [activeIcon] the selected (filled) one. */
sealed class Screen(val route: String, val icon: ImageVector, val label: String, val activeIcon: ImageVector = icon) {
    object Home : Screen("home", Icons.Outlined.Home, "Today", Icons.Filled.Home)
    object Chat : Screen("chat", Icons.Outlined.SmartToy, "Chat", Icons.Filled.SmartToy)
    object Inbox : Screen("inbox", Icons.Outlined.Inbox, "Inbox", Icons.Filled.Inbox)
    object Dashboard : Screen("dashboard", Icons.Outlined.Insights, "Stats", Icons.Filled.Insights)
    object Automation : Screen("automation", Icons.Outlined.AutoAwesome, "Actions", Icons.Filled.AutoAwesome)
    object Memory : Screen("memory", Icons.Outlined.Psychology, "Memory", Icons.Filled.Psychology)
    object AiLab : Screen("ailab", Icons.Outlined.Science, "AI Lab", Icons.Filled.Science)
    object Settings : Screen("settings", Icons.Outlined.Settings, "Settings", Icons.Filled.Settings)
    object Profile : Screen("profile", Icons.Outlined.Person, "Profile", Icons.Filled.Person)
    /** Not a destination: the bottom-bar slot that opens the More menu. */
    object More : Screen("more", Icons.Outlined.GridView, "More", Icons.Filled.GridView)
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
                val latestMemories = remember { mutableStateOf<List<Memory>>(emptyList()) }
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
                                    dispatcher.processQuery(finalQuery, latestEvents.value, latestMemories.value, shouldSpeak = true)
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
                SideEffect { latestMemories.value = memories }
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
                            val reduceMotion = rememberReducedMotion()
                            NavHost(
                                navController = navController,
                                startDestination = Screen.Home.route,
                                modifier = Modifier.padding(innerPadding),
                                enterTransition = navEnter(reduceMotion),
                                exitTransition = navExit(reduceMotion),
                                popEnterTransition = navEnter(reduceMotion),
                                popExitTransition = navExit(reduceMotion)
                            ) {
                                composable(Screen.Home.route) {
                                    TodayScreen(
                                        events = events,
                                        onDeleteEvent = { event ->
                                            scope.launch { app.database.eventDao().deleteEvent(event) }
                                        },
                                        onQuickAction = { actionQuery ->
                                            scope.launch {
                                                val resp = dispatcher.processQuery(actionQuery, events, memories, shouldSpeak = true)
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

/** Routes that live in the bottom bar. Moving between them is a lateral switch, not a push. */
private val TabRoutes = setOf(Screen.Home.route, Screen.Inbox.route, Screen.Chat.route)

/** Key for the "More" slot in the bottom bar, highlighted while any secondary screen is showing. */
private const val MoreKey = "more"

/**
 * Screen transitions, following "enter and exit along the same path":
 *  - tab to tab is a quick fade-through with a hint of scale (no direction, since tabs are peers);
 *  - a secondary screen (from More) arrives from the right and leaves back to the right, while
 *    the screen underneath recedes slightly left, so the spatial model is consistent both ways.
 * Under reduced motion everything is a plain cross-fade.
 */
private fun isTab(entry: NavBackStackEntry) = entry.destination.route in TabRoutes

private fun navEnter(reduce: Boolean): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    when {
        reduce -> fadeIn(tween(160))
        !isTab(targetState) -> slideInHorizontally(ChittiMotion.settle()) { it / 3 } + fadeIn(tween(200))
        !isTab(initialState) -> slideInHorizontally(ChittiMotion.settle()) { -it / 10 } + fadeIn(tween(200))
        else -> fadeIn(tween(200, delayMillis = 60)) + scaleIn(ChittiMotion.settle(), initialScale = 0.98f)
    }
}

private fun navExit(reduce: Boolean): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    when {
        reduce -> fadeOut(tween(120))
        !isTab(initialState) -> slideOutHorizontally(ChittiMotion.settle()) { it / 3 } + fadeOut(tween(160))
        !isTab(targetState) -> slideOutHorizontally(ChittiMotion.settle()) { -it / 10 } + fadeOut(tween(160))
        else -> fadeOut(tween(90))
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

    fun go(route: String) {
        navController.navigate(route) {
            popUpTo(Screen.Home.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val selectedKey = when (currentRoute) {
        null, Screen.Home.route -> Screen.Home.route
        Screen.Inbox.route, Screen.Chat.route -> currentRoute
        else -> MoreKey
    }

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            ChittiBottomBar(
                selectedKey = selectedKey,
                currentRoute = currentRoute,
                onNavigate = ::go,
                onMicClick = onMicClick
            )
        },
        content = content
    )
}

/**
 * The bottom bar. One selection pill slides between slots on a spring (a single object moving,
 * rather than one highlight fading out while another fades in), icons fill when selected, and the
 * top edge catches the light instead of being cut by a divider.
 */
@Composable
private fun ChittiBottomBar(
    selectedKey: String,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onMicClick: () -> Unit
) {
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    var containerOrigin by remember { mutableStateOf(Offset.Zero) }
    val slots = remember { mutableStateMapOf<String, Rect>() }
    var showMore by remember { mutableStateOf(false) }

    val pillX = remember { Animatable(0f) }
    val pillW = remember { Animatable(0f) }
    var pillPlaced by remember { mutableStateOf(false) }
    val target = slots[selectedKey]
    LaunchedEffect(target) {
        if (target == null) return@LaunchedEffect
        if (!pillPlaced) {
            pillX.snapTo(target.left)
            pillW.snapTo(target.width)
            pillPlaced = true
        } else {
            launch { pillX.animateTo(target.left, ChittiMotion.Settle) }
            pillW.animateTo(target.width, ChittiMotion.Settle)
        }
    }

    fun Modifier.slot(key: String) = onGloballyPositioned { coords ->
        val pos = coords.positionInRoot() - containerOrigin
        slots[key] = Rect(pos, Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
    }

    Column(modifier = Modifier.fillMaxWidth().background(Surface1)) {
        // Light catching the top edge of the material: brightest in the middle, gone at the sides.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(Hairline, EdgeHighlight, Hairline)))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { containerOrigin = it.positionInRoot() }
        ) {
            if (pillPlaced && target != null) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(pillX.value.roundToInt(), target.top.roundToInt()) }
                        .size(
                            width = with(density) { pillW.value.toDp() },
                            height = with(density) { target.height.toDp() }
                        )
                        .clip(RoundedCornerShape(14.dp))
                        .background(AccentWash)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.s, vertical = Space.s),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(
                    screen = Screen.Home,
                    isSelected = selectedKey == Screen.Home.route,
                    modifier = Modifier.slot(Screen.Home.route),
                    onClick = { onNavigate(Screen.Home.route) }
                )
                BottomNavItem(
                    screen = Screen.Inbox,
                    isSelected = selectedKey == Screen.Inbox.route,
                    modifier = Modifier.slot(Screen.Inbox.route),
                    onClick = { onNavigate(Screen.Inbox.route) }
                )

                // Talk to Chitti: the primary action, so it is the one solid accent in the bar.
                val micInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .pressScale(micInteraction, pressed = 0.88f)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(Accent, AccentDeep)))
                        .border(1.dp, EdgeHighlight, CircleShape)
                        .clickable(interactionSource = micInteraction, indication = null) {
                            haptics.press()
                            onMicClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Talk to Chitti",
                        tint = OnAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }

                BottomNavItem(
                    screen = Screen.Chat,
                    isSelected = selectedKey == Screen.Chat.route,
                    modifier = Modifier.slot(Screen.Chat.route),
                    onClick = { onNavigate(Screen.Chat.route) }
                )

                Box(modifier = Modifier.slot(MoreKey)) {
                    BottomNavItem(
                        screen = Screen.More,
                        isSelected = selectedKey == MoreKey,
                        onClick = { showMore = !showMore }
                    )
                    MoreMenu(
                        expanded = showMore,
                        currentRoute = currentRoute,
                        onDismiss = { showMore = false },
                        onSelect = { route ->
                            showMore = false
                            onNavigate(route)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNavItem(
    screen: Screen,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint by animateColorAsState(if (isSelected) AccentBright else TextMid, ChittiMotion.settle(), label = "navTint")
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .pressScale(interaction, pressed = 0.92f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        // Filled when selected, outlined otherwise: the state is readable without colour.
        Crossfade(targetState = isSelected, animationSpec = tween(150), label = "navIcon") { selected ->
            Icon(
                imageVector = if (selected) screen.activeIcon else screen.icon,
                contentDescription = screen.label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            screen.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

private val MoreDestinations = listOf(
    Screen.Dashboard to Sky,
    Screen.Automation to Amber,
    Screen.Memory to Iris,
    Screen.AiLab to Mint,
    Screen.Settings to TextMid,
    Screen.Profile to Accent
)

/**
 * The More menu, anchored to its button: it grows up and out of the button's corner and shrinks
 * back into it, so where it came from and where it goes are the same place.
 */
@Composable
private fun MoreMenu(
    expanded: Boolean,
    currentRoute: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val transition = remember { MutableTransitionState(false) }
    transition.targetState = expanded
    if (!transition.currentState && !transition.targetState) return

    val reduce = rememberReducedMotion()
    val density = LocalDensity.current
    val gapPx = with(density) { 10.dp.roundToPx() }

    Popup(
        popupPositionProvider = remember(gapPx) { AboveAnchorEndAligned(gapPx) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        AnimatedVisibility(
            visibleState = transition,
            enter = if (reduce) fadeIn(tween(120)) else
                fadeIn(tween(120)) + scaleIn(ChittiMotion.settle(), initialScale = 0.6f, transformOrigin = TransformOrigin(0.9f, 1f)),
            exit = if (reduce) fadeOut(tween(100)) else
                fadeOut(tween(140)) + scaleOut(ChittiMotion.settle(), targetScale = 0.6f, transformOrigin = TransformOrigin(0.9f, 1f))
        ) {
            Column(
                modifier = Modifier
                    .width(260.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Surface2)
                    .border(1.dp, HairlineStrong, RoundedCornerShape(22.dp))
                    .padding(Space.s)
            ) {
                MoreDestinations.chunked(3).forEachIndexed { row, items ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        items.forEachIndexed { col, (screen, tint) ->
                            MoreTile(
                                screen = screen,
                                tint = tint,
                                selected = screen.route == currentRoute,
                                modifier = Modifier
                                    .weight(1f)
                                    .staggeredEntrance(row * 3 + col, stepMs = 25),
                                onClick = { onSelect(screen.route) }
                            )
                        }
                    }
                    if (row == 0) Spacer(Modifier.height(Space.xs))
                }
            }
        }
    }
}

@Composable
private fun MoreTile(
    screen: Screen,
    tint: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .pressScale(interaction, pressed = 0.94f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Surface3 else Color.Transparent)
            .spotlight(tint.copy(alpha = 0.16f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = Space.m),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(screen.activeIcon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(Space.s))
        Text(screen.label, style = MaterialTheme.typography.labelMedium, color = if (selected) TextHigh else TextMid, maxLines = 1)
    }
}

/** Places a popup above its anchor with their end edges aligned, clamped to the window. */
private class AboveAnchorEndAligned(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}
