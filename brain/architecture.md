# 🏗️ System Architecture & Engineering Design

```
+---------------------------------------------------------------------------------+
|                                 USER INTERFACE                                  |
|         Jetpack Compose (Material3 1.2.1) + Gemini Cosmic Iridescent Theme      |
+---------------------------------------------------------------------------------+
|  [Today Screen]  |  [File Finder]  |  [Chat / Ask]  |  [AI Lab]  |  [Dashboard] |
+---------------------------------------------------------------------------------+
|                   GEMINI VOICE OVERLAY (Modal Bottom Sheet)                     |
|           • Real-time RMS Waveform Visualizer  • Cosmic Rotating Spinner        |
|           • Glowing Iridescent Lightbar        • Pulsing Mic Interaction Orb    |
+---------------------------------------------------------------------------------+
                                         |
                                         v
+---------------------------------------------------------------------------------+
|                        INTENT & ORCHESTRATION LAYER                             |
+---------------------------------------------------------------------------------+
|  [AssistantIntentDispatcher]  -->  Pattern Matching, Entity Extraction, Routing |
|         |                                  |                        |           |
|         +--> [AppLauncher]                 +--> [FileFinder]        +--> [TTS]  |
|              • Package query                    • MediaStore query       • Safe |
|              • System actions (Camera,          • TimeFilter engine        text |
|                Flashlight, Settings)            • Intent-based open      clean  |
+---------------------------------------------------------------------------------+
                                         |
                                         v
+---------------------------------------------------------------------------------+
|                       INTELLIGENCE & INFERENCE ENGINE                           |
+---------------------------------------------------------------------------------+
|  [Speech-to-Text Manager]       |  [MediaPipe Tasks GenAI] (Gemma 2B INT4)      |
|  • Android SpeechRecognizer     |  • /data/local/tmp/gemma.bin                  |
|  • Synchronous lifecycle        |  • On-Device RAG over Local Commitments       |
|  • Low-latency silence timeouts |  • Offline Contextual Text Generation         |
+---------------------------------+-----------------------------------------------+
|  [ML Kit Vision Text OCR]       |  [Silero Voice Activity Detection (VAD)]      |
|  • Play Services OCR engine     |  • ONNX Runtime Android (libonnxruntime.so)   |
|  • On-device camera/image scan  |  • Low-power energy threshold detection       |
+---------------------------------------------------------------------------------+
                                         |
                                         v
+---------------------------------------------------------------------------------+
|                          DATA & SYSTEM CAPTURE LAYER                            |
+---------------------------------------------------------------------------------+
|  [NotificationCaptureService]   |  [Room Database (SQLite)]                     |
|  • BIND_NOTIFICATION_LISTENER   |  • EventDao, TaskDao, MemoryDao,              |
|  • Autonomous push parsing      |    NotificationDao, DocumentDao               |
+---------------------------------+-----------------------------------------------+
|  [MediaStore ContentResolver]   |  [System Automation & Alarms]                 |
|  • READ_MEDIA_IMAGES, Files     |  • AlarmManager & BroadcastReceiver           |
|  • Deep historical queries      |  • WorkManager periodic maintenance           |
+---------------------------------------------------------------------------------+
```

---

## 1. Architectural Principles

1. **Uncompromising Data Sovereignty (Zero Cloud Egress)**
   All classification, extraction, audio recognition, and file indexing logic executes inside the application sandbox or via local Android platform services.
2. **Deterministic Fallbacks for Extreme Reliability**
   GenAI models can experience resource starvation under heavy memory pressure. Chitti employs a dual-tier processing architecture:
   - **Tier 1**: On-device Gemma 2B LLM inference via MediaPipe GenAI.
   - **Tier 2**: Instant deterministic regex and rule-based extraction engines capable of parsing multi-lingual (English, Telugu-English code-mix, Hinglish) text in < 5 milliseconds.
3. **Thread Safety & Looper Contracts**
   Android's `SpeechRecognizer` enforces strict execution on the calling thread's Looper. Chitti guarantees all recognizer creation, listening commands, and callback listeners are marshalled to `Looper.getMainLooper()`, eliminating thread deadlocks and race conditions.

---

## 2. Core Subsystems

### 2.1 Speech & Audio Pipeline
- **Engine**: `SpeechToTextManager` wrapping `android.speech.SpeechRecognizer`.
- **Latency Optimization**: Configured with custom `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS = 2000` and `EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS = 1500`.
- **Partial Trigger Acceleration**: Parses streaming partial transcripts. Unambiguous action commands (e.g. "open whatsapp", "turn on flashlight") execute immediately on partial match without waiting for trailing audio silence.
- **Audio Visualization**: RMS sound level normalized to `[0.05f .. 1.0f]` and animated in Jetpack Compose at 60 FPS using `animateDpAsState` with spring physics.

### 2.2 Intent Dispatcher & App Launcher
- Located in `com.owlcoders.chitti.automation`.
- Resolves natural voice queries into 6 distinct categories:
  1. `APP_LAUNCH`: Queries `PackageManager` for package names (`com.whatsapp`, `com.google.android.youtube`, etc.) or matches launcher activity labels.
  2. `DEVICE_CONTROL`: Direct hardware access via `CameraManager.setTorchMode` for flashlight toggle.
  3. `FILE_SEARCH`: Triggers `FileFinder.queryFiles` across device storage.
  4. `TASK_SCHEDULE`: Queries Room DB `EventDao` for pending agenda commitments.
  5. `CONVERSATION`: Contextual responses for identity, capabilities, time, and humor.
  6. `FALLBACK_RAG`: Passes input through `ExtractionEngine.generateRagResponse`.

### 2.3 Deep File Finder Engine
- Queries `MediaStore.Files.getContentUri("external")` and `MediaStore.Images.Media.EXTERNAL_CONTENT_URI`.
- Implements dynamic SQL projection and selection builders filtering across:
  - `DATE_MODIFIED` and `DATE_ADDED`.
  - Dedicated `TimeFilter.LONG_AGO` targeting records older than 180 days (6 months).
  - Category MIME type partitioning: Documents (`application/pdf`, docx, txt), Images (`image/*`), Media (`audio/*`, `video/*`), Archives (`application/zip`).
- Safe execution: Employs `ContentUris.withAppendedId` and launches system viewing intents with `FLAG_GRANT_READ_URI_PERMISSION`.

### 2.4 Local Storage & Persistence (Room)
Chitti maintains an offline relational database (`chitti_database`) with 5 primary entities:
- `CapturedEvent`: Extracted commitments, tasks, and deadlines with urgency ratings.
- `TaskEntity`: User-created checklist items.
- `NotificationEntity`: Raw notification event log with originating package name.
- `MemoryItem`: Knowledge snippets learned from user interactions.
- `Document`: OCR scanned documents with full-text searchable content.
