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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import android.util.Log
import android.content.ComponentName
import androidx.core.app.NotificationManagerCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestBatteryOptimizationExemption()
        
        setContent {
            ChittiTheme {
                // Collect real data from Room
                val events by (application as ChittiApp).database.eventDao().getAllEvents().collectAsState(initial = emptyList())
                val scope = rememberCoroutineScope()
                var hasNotificationAccess by remember { mutableStateOf(isNotificationServiceEnabled()) }
                
                Scaffold(
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = {
                                // DEMO REPLAY: Inject fake notification into LLM directly
                                scope.launch {
                                    val engine = (application as ChittiApp).extractionEngine
                                    Log.d("ChittiDemo", "Replay triggered. Running LLM...")
                                    val extracted = engine?.extract("repu class unda? 9 ki?")
                                    Log.d("ChittiDemo", "Extracted: ${extracted?.what} at ${extracted?.whenTime}")
                                }
                            }
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Debug Replay")
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (hasNotificationAccess) {
                            TodayScreen(events = events)
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
