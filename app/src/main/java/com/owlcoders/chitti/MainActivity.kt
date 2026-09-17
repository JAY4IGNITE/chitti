package com.owlcoders.chitti

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.owlcoders.chitti.db.CapturedEvent
import com.owlcoders.chitti.db.entities.ChatHistoryEntity
import com.owlcoders.chitti.db.entities.Memory
import com.owlcoders.chitti.ui.screens.*
import com.owlcoders.chitti.ui.theme.ChittiTheme
import kotlinx.coroutines.launch

// Navigation routes
sealed class Screen(val route: String, val icon: ImageVector, val label: String) {
    object Home : Screen("home", Icons.Filled.Home, "Desk")
    object Chat : Screen("chat", Icons.Filled.SmartToy, "Chat")
    object Inbox : Screen("inbox", Icons.Filled.Inbox, "Inbox")
    object Dashboard : Screen("dashboard", Icons.Filled.Dashboard, "Stats")
    object Automation : Screen("automation", Icons.Filled.AutoAwesome, "Actions")
    object Documents : Screen("documents", Icons.Filled.Description, "Docs")
    object Memory : Screen("memory", Icons.Filled.Psychology, "Memory")
    object AiLab : Screen("ailab", Icons.Filled.Science, "AI Lab")
    object Settings : Screen("settings", Icons.Filled.Settings, "Settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestBatteryOptimizationExemption()

        setContent {
            ChittiTheme {
                val navController = rememberNavController()
                val scope = rememberCoroutineScope()
                val app = application as ChittiApp

                // ----- Collect Data from Room -----
                var searchQuery by remember { mutableStateOf("") }

                val events by if (searchQuery.isEmpty()) {
                    app.database.eventDao().getAllEvents().collectAsState(initial = emptyList())
                } else {
                    app.database.eventDao().searchEvents(searchQuery).collectAsState(initial = emptyList())
                }

                val tasks by app.database.taskDao().getAllTasks().collectAsState(initial = emptyList())
                val notifications by app.database.notificationDao().getAllNotifications().collectAsState(initial = emptyList())
                val memories by app.database.memoryDao().getAllMemories().collectAsState(initial = emptyList())
                val memoryCategories by app.database.memoryDao().getCategories().collectAsState(initial = emptyList())
                val documents by app.database.documentDao().getAllDocuments().collectAsState(initial = emptyList())
                val automationHistory by app.database.automationHistoryDao().getRecentHistory(100).collectAsState(initial = emptyList())
                val chatHistory by app.database.chatHistoryDao().getAllMessages().collectAsState(initial = emptyList())

                // ----- Counts for dashboard/settings -----
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
                var previewMode by remember { mutableStateOf(false) }

                // Camera launcher for OCR
                val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
                    if (bitmap != null) {
                        Log.d("ChittiVision", "Captured image. Running OCR...")
                        val image = InputImage.fromBitmap(bitmap, 0)
                        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        recognizer.process(image)
                            .addOnSuccessListener { visionText ->
                                Log.d("ChittiVision", "OCR Text: ${visionText.text}")
                                scope.launch {
                                    val engine = app.extractionEngine
                                    val extracted = engine?.extract("OCR FROM FLYER: ${visionText.text}")
                                    app.database.eventDao().insertEvent(
                                        CapturedEvent(
                                            sourceApp = "com.owlcoders.chitti.vision",
                                            rawText = "Flyer text: ${visionText.text.take(50)}...",
                                            extractedWhat = extracted?.what,
                                            extractedWhen = extracted?.whenTime,
                                            extractedWho = extracted?.who,
                                            category = extracted?.category,
                                            urgency = extracted?.urgency,
                                            status = "extracted",
                                            timestamp = System.currentTimeMillis()
                                        )
                                    )
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("ChittiVision", "OCR Failed", e)
                            }
                    }
                }

                // ----- UI -----
                if (hasNotificationAccess || previewMode) {
                    ChittiScaffold(
                        navController = navController,
                        onFabClick = { cameraLauncher.launch(null) }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Home.route,
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable(Screen.Home.route) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        placeholder = { Text("Search Contextual Memory...") },
                                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = androidx.compose.ui.graphics.Color.White,
                                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.White
                                        )
                                    )
                                    TodayScreen(
                                        events = events,
                                        onDeleteEvent = { event ->
                                            scope.launch {
                                                app.database.eventDao().deleteEvent(event)
                                            }
                                        }
                                    )
                                }
                            }

                            composable(Screen.Chat.route) {
                                ChatBotScreen(
                                    events = events,
                                    chatHistory = chatHistory,
                                    memories = memories,
                                    onSaveMessage = { message ->
                                        scope.launch {
                                            app.database.chatHistoryDao().insertMessage(message)
                                        }
                                    }
                                )
                            }

                            composable(Screen.Inbox.route) {
                                InboxScreen(
                                    notifications = notifications,
                                    onMarkProcessed = { notification ->
                                        scope.launch {
                                            app.database.notificationDao().markProcessed(notification.id)
                                        }
                                    },
                                    onDelete = { notification ->
                                        scope.launch {
                                            app.database.notificationDao().deleteNotification(notification)
                                        }
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

                            composable(Screen.Documents.route) {
                                DocumentsScreen(
                                    documents = documents,
                                    onPickFile = {
                                        // Launch SAF file picker
                                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                            addCategory(Intent.CATEGORY_OPENABLE)
                                            type = "*/*"
                                        }
                                        startActivity(intent)
                                    },
                                    onDeleteDocument = { doc ->
                                        scope.launch {
                                            app.database.documentDao().deleteDocument(doc)
                                        }
                                    }
                                )
                            }

                            composable(Screen.Memory.route) {
                                MemoryScreen(
                                    memories = memories,
                                    categories = memoryCategories,
                                    onAddMemory = { key, value, category ->
                                        scope.launch {
                                            app.database.memoryDao().insertMemory(
                                                Memory(key = key, value = value, category = category)
                                            )
                                        }
                                    },
                                    onDeleteMemory = { memory ->
                                        scope.launch {
                                            app.database.memoryDao().deleteMemory(memory)
                                        }
                                    },
                                    onUpdateMemory = { memory ->
                                        scope.launch {
                                            app.database.memoryDao().updateMemory(memory)
                                        }
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
                                        }
                                    },
                                    eventCount = events.size,
                                    taskCount = tasks.size,
                                    notificationCount = notificationCount,
                                    memoryCount = memoryCount,
                                    documentCount = documentCount,
                                    chatMessageCount = chatMessageCount,
                                    automationHistoryCount = automationHistoryCount,
                                    onClearNotifications = {
                                        scope.launch { app.database.notificationDao().deleteAllNotifications() }
                                    },
                                    onClearChatHistory = {
                                        scope.launch { app.database.chatHistoryDao().deleteAllMessages() }
                                    },
                                    onClearAutomationHistory = {
                                        scope.launch { app.database.automationHistoryDao().deleteAllHistory() }
                                    },
                                    onClearMemories = {
                                        scope.launch {
                                            memories.forEach { app.database.memoryDao().deleteMemory(it) }
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Onboarding: Request notification access
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🤖 Chitti", style = MaterialTheme.typography.headlineLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Offline AI Personal Assistant")
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Chitti needs Notification Listener access to work")
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                startActivity(intent)
                            }) {
                                Text("Enable Notification Access")
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(onClick = { hasNotificationAccess = isNotificationServiceEnabled() }) {
                                Text("I've Enabled It — Continue")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { previewMode = true }) {
                                Text("Explore App (Preview Mode)")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
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
    onFabClick: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Bottom nav shows 5 primary destinations
    val bottomScreens = listOf(Screen.Home, Screen.Chat, Screen.Inbox, Screen.Dashboard, Screen.Settings)
    // Drawer/secondary destinations
    val drawerScreens = listOf(Screen.AiLab, Screen.Automation, Screen.Documents, Screen.Memory)

    var showMoreMenu by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomScreens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(Screen.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        },
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = {
                    Text(
                        when (currentRoute) {
                            Screen.Home.route -> "Chitti"
                            Screen.Chat.route -> "AI Chat"
                            Screen.Inbox.route -> "Inbox"
                            Screen.Dashboard.route -> "Dashboard"
                            Screen.Settings.route -> "Settings"
                            Screen.AiLab.route -> "AI Lab & Benchmark"
                            Screen.Automation.route -> "Automation"
                            Screen.Documents.route -> "Documents"
                            Screen.Memory.route -> "Memory"
                            else -> "Chitti"
                        }
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            drawerScreens.forEach { screen ->
                                DropdownMenuItem(
                                    text = { Text(screen.label) },
                                    leadingIcon = { Icon(screen.icon, contentDescription = screen.label) },
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
            )
        },
        floatingActionButton = {
            if (currentRoute == Screen.Home.route) {
                FloatingActionButton(onClick = onFabClick) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = "Scan Flyer")
                }
            }
        },
        content = content
    )
}
