import os

def create_file(path, content):
    dir_name = os.path.dirname(path)
    if dir_name:
        os.makedirs(dir_name, exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)

# Project root files
create_file("settings.gradle.kts", """pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Chitti"
include(":app")
""")

create_file("build.gradle.kts", """plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
}
""")

create_file("gradle.properties", """org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
""")

# App module build file
create_file("app/build.gradle.kts", """plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.owlcoders.chitti"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.owlcoders.chitti"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    
    // Room
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    annotationProcessor("androidx.room:room-compiler:$room_version")
    ksp("androidx.room:room-compiler:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
}
""")

# Manifest
create_file("app/src/main/AndroidManifest.xml", """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"/>

    <application
        android:name=".ChittiApp"
        android:allowBackup="true"
        android:icon="@android:drawable/ic_dialog_email"
        android:label="Chitti"
        android:roundIcon="@android:drawable/ic_dialog_email"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.DeviceDefault.DayNight">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        
        <service android:name=".services.NotificationCaptureService"
                 android:label="Chitti Notification Listener"
                 android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
                 android:exported="true">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>
    </application>

</manifest>
""")

pkg = "app/src/main/java/com/owlcoders/chitti/"

# App Class
create_file(pkg + "ChittiApp.kt", """package com.owlcoders.chitti

import android.app.Application
import com.owlcoders.chitti.db.AppDatabase

class ChittiApp : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    
    override fun onCreate() {
        super.onCreate()
    }
}
""")

# MainActivity
create_file(pkg + "MainActivity.kt", """package com.owlcoders.chitti

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
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestBatteryOptimizationExemption()
        
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen()
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
}

@Composable
fun MainScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Chitti - Notification Listener")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { /* Open Notification Access Settings */ }) {
            Text("Enable Notification Access")
        }
    }
}
""")

# Room Db, Entity, DAO
create_file(pkg + "db/CapturedEvent.kt", """package com.owlcoders.chitti.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class CapturedEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sourceApp: String,
    val rawText: String,
    val extractedWhat: String?,
    val extractedWhen: String?,
    val extractedWho: String?,
    val status: String,
    val timestamp: Long
)
""")

create_file(pkg + "db/CapturedEventDao.kt", """package com.owlcoders.chitti.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CapturedEventDao {
    @Query("SELECT * FROM events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<CapturedEvent>>

    @Insert
    suspend fun insertEvent(event: CapturedEvent)
}
""")

create_file(pkg + "db/AppDatabase.kt", """package com.owlcoders.chitti.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CapturedEvent::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): CapturedEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chitti_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
""")

# Notification Service
create_file(pkg + "services/NotificationCaptureService.kt", """package com.owlcoders.chitti.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.owlcoders.chitti.ChittiApp
import com.owlcoders.chitti.db.CapturedEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationCaptureService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let {
            val packageName = it.packageName
            val notification = it.notification
            val extras = notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE)
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

            if (text != null) {
                Log.d("ChittiCapture", "Captured from $packageName: $title - $text")
                // TODO: Apply keyword filter and then extract entities using LLM
                // Currently just saving to DB for skeleton phase
                saveToDatabase(packageName, text)
            }
        }
    }
    
    private fun saveToDatabase(sourceApp: String, rawText: String) {
        val app = application as ChittiApp
        serviceScope.launch {
            app.database.eventDao().insertEvent(
                CapturedEvent(
                    sourceApp = sourceApp,
                    rawText = rawText,
                    extractedWhat = null,
                    extractedWhen = null,
                    extractedWho = null,
                    status = "pending",
                    timestamp = System.currentTimeMillis()
                )
            )
            Log.d("ChittiCapture", "Saved to DB")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
""")

print("Scaffolding complete.")
