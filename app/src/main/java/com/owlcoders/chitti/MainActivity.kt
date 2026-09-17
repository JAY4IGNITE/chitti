package com.owlcoders.chitti

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.ui.theme.ChittiTheme
import com.owlcoders.chitti.ui.screens.TodayScreen
import com.owlcoders.chitti.db.CapturedEvent
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.animation.Crossfade
import com.owlcoders.chitti.ui.screens.DashboardScreen
import com.owlcoders.chitti.ui.screens.SettingsScreen
import com.owlcoders.chitti.ui.screens.ChatBotScreen
import androidx.compose.runtime.*
import android.util.Log
import android.graphics.Bitmap
import android.content.ComponentName
import androidx.core.app.NotificationManagerCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

sealed class Screen(val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String) {
    object Home : Screen("home", Icons.Filled.Home, "Desk")
    object Chat : Screen("chat", Icons.Filled.Person, "Chat")
    object Dashboard : Screen("dashboard", Icons.Filled.List, "Dashboard")
    object Settings : Screen("settings", Icons.Filled.Settings, "Settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestBatteryOptimizationExemption()
        
        setContent {
            ChittiTheme {
                // Collect real data from Room
                var searchQuery by remember { mutableStateOf("") }
                
                val events by if (searchQuery.isEmpty()) {
                    (application as ChittiApp).database.eventDao().getAllEvents().collectAsState(initial = emptyList())
                } else {
                    (application as ChittiApp).database.eventDao().searchEvents(searchQuery).collectAsState(initial = emptyList())
                }
                
                val scope = rememberCoroutineScope()
                var hasNotificationAccess by remember { mutableStateOf(isNotificationServiceEnabled()) }
                
                val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
                    if (bitmap != null) {
                        Log.d("ChittiVision", "Captured image. Running OCR...")
                        val image = InputImage.fromBitmap(bitmap, 0)
                        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        recognizer.process(image)
                            .addOnSuccessListener { visionText ->
                                Log.d("ChittiVision", "OCR Text: ${visionText.text}")
                                scope.launch {
                                    val engine = (application as ChittiApp).extractionEngine
                                    val extracted = engine?.extract("OCR FROM FLYER: ${visionText.text}")
                                    Log.d("ChittiVision", "Extracted task from flyer!")
                                    // Save it to DB
                                    (application as ChittiApp).database.eventDao().insertEvent(
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
                
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
                
                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = androidx.compose.ui.graphics.Color.White
                        ) {
                            val screens = listOf(Screen.Home, Screen.Chat, Screen.Dashboard, Screen.Settings)
                            screens.forEach { screen ->
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = screen.label) },
                                    label = { Text(screen.label) },
                                    selected = currentScreen == screen,
                                    onClick = { currentScreen = screen }
                                )
                            }
                        }
                    },
                    floatingActionButton = {
                        if (currentScreen == Screen.Home) {
                            FloatingActionButton(
                                onClick = { cameraLauncher.launch(null) }
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Scan Flyer")
                            }
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (hasNotificationAccess) {
                            Crossfade(targetState = currentScreen) { screen ->
                                when (screen) {
                                    Screen.Home -> {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            OutlinedTextField(
                                                value = searchQuery,
                                                onValueChange = { searchQuery = it },
                                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                placeholder = { Text("Search Contextual Memory...") },
                                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedContainerColor = androidx.compose.ui.graphics.Color.White,
                                                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.White
                                                )
                                            )
                                            TodayScreen(events = events, onDeleteEvent = { event ->
                                                scope.launch {
                                                    (application as ChittiApp).database.eventDao().deleteEvent(event)
                                                }
                                            })
                                        }
                                    }
                                    Screen.Chat -> {
                                        ChatBotScreen(events = events)
                                    }
                                    Screen.Dashboard -> {
                                        DashboardScreen(events = events)
                                    }
                                    Screen.Settings -> {
                                        SettingsScreen(
                                            hasNotificationAccess = hasNotificationAccess,
                                            onWipeData = {
                                                scope.launch {
                                                    (application as ChittiApp).database.eventDao().deleteAllEvents()
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Chitti - Notification Listener")
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = {
                                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    startActivity(intent)
                                }) {
                                    Text("Enable Notification Access")
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { hasNotificationAccess = isNotificationServiceEnabled() }) {
                                    Text("I've Enabled It")
                                }
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
