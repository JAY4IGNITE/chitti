# Chitti – Offline AI Personal Assistant & Mobile Automation Platform
## Comprehensive Product Specification & Implementation Plan

---

## 1. Product Vision (Recap)

Chitti is an Android‑first, privacy‑focused, offline AI personal assistant that **sees → understands → remembers → acts** on information arriving on the device (notifications, messages, documents, images, voice, user input). It works **without an internet connection** whenever technically possible, keeping all personal data on‑device.  

Beyond simple reminders, Chitti evolves into a **local AI operating layer** that:

- Detects commitments, deadlines, payments, meetings, and requests from notifications and shared text.  
- Extracts structured entities (Task, Reminder, Deadline, Meeting, Payment, Person, Place, Note, Document, Conversation, Event).  
- Organizes information into **Today**, **Upcoming**, **Promises**, and a searchable **Personal Memory**.  
- Provides an **offline chatbot** (LLM‑powered) for Q&A, task creation, device commands, and memory lookup.  
- Integrates **OCR + camera intelligence** for document scanning, screenshot analysis, and image‑to‑task extraction.  
- Executes **supported Android actions** (app launch, deep links, calendar/reminder creation, message drafting, file search) via a controlled action orchestrator.  
- Offers **voice interaction** using **Cartesia** for Speech‑to‑Text (STT) and Text‑to‑Speech (TTS) while staying offline‑first (models can be cached locally).  
- Maintains a **local vector database / RAG** pipeline for semantic search over notifications, notes, documents, OCR results, and memories.  
- Generates a **daily digest** and provides an **action center** for quick execution.  
- Keeps privacy transparent via a dashboard showing what data is stored, processed, and deletable.  
- Optimizes battery/performance via a layered inference pipeline (cheap filter → small model → LLM only when needed).  

---

## 2. Technical Architecture

### 2.1 High‑Level Modules

```
Chitti App
│
├─ UI Layer (Jetpack Compose)
│   ├─ home/
│   ├─ chat/
│   ├─ inbox/
│   ├─ today/
│   ├─ automation/
│   ├─ camera/
│   ├─ documents/
│   ├─ memory/
│   └─ settings/
│
├─ AI Orchestration Layer
│   ├─ llm/               (Local LLM provider interface)
│   ├─ stt/               (Cartesia STT provider)
│   ├─ tts/               (Cartesia TTS provider)
│   ├─ classifier/        (Intent & deadline detection)
│   ├─ extractor/         (Entity extraction – rule‑based + ML)
│   ├─ embeddings/        (Sentence‑transformer / MiniLM)
│   ├─ ocr/               (Offline OCR – Tesseract + custom model)
│   └─ action_planner/    (Maps intents to allowed actions)
│
├─ Notification Processor
│   ├─ listener (NotificationListenerService)
│   ├─ preprocess (language detection, cheap filter)
│   ├─ intent_classifier
│   ├─ entity_extractor
│   ├─ importance_scorer
│   ├─ duplicate_detector
│   └─ structured_event_emitter
│
├─ Local Persistence Layer
│   ├─ Room Database (entities: tasks, deadlines, reminders, commitments, events, notifications, documents, memories, people, automation_history, chat_history)
│   └─ Vector Store (FAISS or SQLite‑VSS) for embeddings
│
├─ Automation Engine
│   ├─ action_registry (whitelisted Android actions)
│   ├─ permission_checker
│   ├─ confirmation_manager
│   ├─ intent_executor (uses PendingIntents, ShareIntents, CalendarContract, AlarmManager, etc.)
│   └─ audit_logger
│
├─ Voice Interface
│   ├─ stt_service (Cartesia streaming STT)
│   ├─ tts_service (Cartesia TTS, cached locally)
│   └─ voice_command_parser (routes STT output to chatbot/intents)
│
└─ Core Utilities
    ├─ permissions_handler
    ├─ battery_optimizer
    ├─ logger
    └─ di (Hilt)
```

### 2.2 Data Flow (SEE → UNDERSTAND → REMEMBER → ACT)

1. **SEE** – NotificationListenerService (or file‑picker/camera) captures raw input.  
2. **PREPROCESS** – Language detection, cheap regex filter (e.g., look for date/time patterns) to avoid heavy inference on noise.  
3. **INTENT CLASSIFICATION** – Small transformer (DistilBERT‑base‑uncased‑finetuned) predicts intent: `deadline`, `request`, `payment`, `meeting`, `reminder`, `none`.  
4. **ENTITY EXTRACTION** – Combination of rule‑based regex (dates, times, amounts) + lightweight NER model (DistilBERT‑NER) for persons, locations, amounts.  
5. **STRUCTURED EVENT** – Populate a `DetectedEvent` object (type, task, datetime, location, amount, priority, source).  
6. **IMPORTANCE SCORING** – Weighted sum (deadline proximity, financial impact, user‑defined tags).  
7. **DEDUPLICATION** – Check recent events (last 5 min) by source + hash of task+time.  
8. **STORE** – Persist to Room; generate embedding via MiniLM and insert into vector store.  
9. **REMEMBER** – Update personal memory if user confirms or explicitly adds.  
10. **ACT** – Action Planner maps intent → allowed action (e.g., `CREATE_REMINDER`, `OPEN_APP`, `DRAFT_MESSAGE`). Permission & confirmation checks happen before execution.  
11. **FEEDBACK** – Result returned to UI; chatbot can be queried for follow‑up.

---

## 3. Technology Stack

| Domain                     | Choice                                                                                                                                          | Reasoning                                                                                                          |
|----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| **Language**               | Kotlin 1.9+                                                                                                                                     | Official Android language, coroutines support, interoperable with Java.                                            |
| **UI Framework**           | Jetpack Compose (Material3)                                                                                                                     | Declarative, modern, efficient UI; easy theming & dark mode.                                                      |
| **Architecture**           | Clean Architecture + MVVM with Hilt DI                                                                                                          | Clear separation, testability, dependency injection.                                                               |
| **Database**               | Room (SQLite) + SQLite‑VSS (vector extension) **or** FAISS via native lib                                                                      | ACID transactions, LiveData/Flow support; vector search for RAG.                                                  |
| **Background Work**        | WorkManager (for periodic sync/cleanup), Foreground Service (for persistent notification listener)                                            | Battery‑friendly, respects OS limits.                                                                              |
| **AI Runtime**             | ONNX Runtime Mobile (or TensorFlow Lite)                                                                                                        | Supports quantized models, CPU‑only, cross‑platform.                                                               |
| **LLM**                    - **Primary**: `Phi‑2‑mini` (3.8 B) quantized to 4‑bit (≈2 GB) <br> - **Fallback**: `TinyLlama‑1.1B` quantized (≈600 MB) | Small enough to run on modern Snapdragon 8+ devices (< 2 GB RAM), good instruction‑following, offline.            |
| **Intent Classifier**      | DistilBERT‑base‑uncased finetuned on Chitti dataset (≈80 MB)                                                                                    | Fast (< 10 ms), good accuracy for 6‑class intent.                                                                  |
| **Entity Extractor**       | DistilBERT‑NER (multilingual) + custom regex for dates/times/amounts (≈100 MB)                                                                  | Handles English, Telugu‑English code‑mix, Hindi‑English.                                                          |
| **Embedding Model**        | `sentence‑transformers/all‑MiniLM‑L6‑v2` quantized (≈50 MB)                                                                                     | Produces 384‑dim vectors, suitable for FAISS/SQLite‑VSS.                                                          |
| **OCR Engine**             | **Primary**: Tesseract 5 with LSTM data (English + Telugu + Hindi) <br> **Secondary**: Custom CNN‑based model for structured forms (invoices, fee notices) | Fully offline, supports Indian scripts, good accuracy on clean scans.                                            |
| **STT/TTS**                | **Cartesia** (cloud API) **with optional local caching** <br> - STT: Whisper‑tiny (quantized) <br> - TTS: Tacotron2 + HiFi‑GAN (quantized) | Provides high‑quality, low‑latency voice; can be switched to fully local models if needed.                         |
| **Language Detection**     | fastText language ID model (≈5 MB)                                                                                                              | Quick detection of English/Telugu/Hindi/mixed.                                                                     |
| **Vector Store**           | SQLite‑VSS (embeddings stored as BLOB, indexed with HNSW) **or** FAISS via JNI                                                                   | Enables semantic search over notifications, notes, docs.                                                          |
| **Navigation**             | Jetpack Compose Navigation                                                                                                                      | Declarative navigation, deep link support.                                                                         |
| **Permissions**            | AndroidX Core                                                                                                                                   | Runtime permission handling.                                                                                       |
| **Testing**                | JUnit5, MockK, Espresso, UI Automator, AndroidX Test, Firebase Test Lab (for device matrix)                                                    | Unit, UI, instrumentation tests.                                                                                  |
| **CI/CD**                  | GitHub Actions (build, test, lint) → Play Store Internal Test track                                                                              | Automated pipeline.                                                                                                |
| **Analytics (opt‑in)**     | Firebase Analytics (only if user enables online enhancements)                                                                                   | Optional, privacy‑first.                                                                                           |
| **Crash Reporting**        | Firebase Crashlytics (opt‑in)                                                                                                                   | Optional.                                                                                                          |
| **Localization**           | Android resource files (values‑te, values‑hi, values‑en)                                                                                        | Supports Telugu, Hindi, English.                                                                                  |
| **Theming**                | Material3 dynamic colors                                                                                                                        | Modern look, respects user wallpaper/theme.                                                                        |

---

## 4. Repository Structure

```
chitti/
├─ app/                         # Application module
│   ├─ src/
│   │   ├─ main/
│   │   │   ├─ java/com/example/chitti/
│   │   │   │   ├─ di/                     # Hilt modules
│   │   │   │   ├─ ui/
│   │   │   │   │   ├─ home/
│   │   │   │   │   ├─ chat/
│   │   │   │   │   ├─ inbox/
│   │   │   │   │   ├─ today/
│   │   │   │   │   ├─ automation/
│   │   │   │   │   ├─ camera/
│   │   │   │   │   ├─ documents/
│   │   │   │   │   ├─ memory/
│   │   │   │   │   └─ settings/
│   │   │   │   ├─ ai/
│   │   │   │   │   ├─ llm/
│   │   │   │   │   ├─ stt/
│   │   │   │   │   ├─ tts/
│   │   │   │   │   ├─ classifier/
│   │   │   │   │   ├─ extractor/
│   │   │   │   │   ├─ embeddings/
│   │   │   │   │   ├─ ocr/
│   │   │   │   │   └─ action_planner/
│   │   │   │   ├─ notification/
│   │   │   │   ├─ automation/
│   │   │   │   ├─ calendar/
│   │   │   │   ├─ reminders/
│   │   │   │   ├─ documents/
│   │   │   │   ├─ memory/
│   │   │   │   ├─ database/
│   │   │   │   ├─ permissions/
│   │   │   │   └─ core/
│   │   │   └─ res/                        # values, drawable, layout (for compatibility)
│   │   └─ AndroidManifest.xml
│   └─ build.gradle.kts
│
├─ libs/                        # Pre‑built native libs (Tesseract, FAISS, ONNX Runtime)
│
├─ scripts/                     # Model conversion, quantization scripts
│
├─ docs/                        # Architecture diagrams, API specs
│
└─ build.gradle.kts             # Root Gradle
```

---

## 5. Dependency Plan (Gradle Kotlin DSL – key entries)

```kotlin
// Core AndroidX
implementation("androidx.core:core-ktx:1.13.0")
implementation("androidx.appcompat:appcompat:1.7.0")
implementation("com.google.android.material:material:1.12.0")
implementation("androidx.constraintlayout:constraintlayout:2.1.4")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
implementation("androidx.activity:activity-compose:1.9.0")
implementation("androidx.compose.ui:ui:1.7.0")
implementation("androidx.compose.material3:material3:1.3.0")
implementation("androidx.compose.ui:ui-tooling-preview:1.7.0")
implementation("androidx.navigation:navigation-compose:2.8.0")

// DI
implementation("com.google.dagger:hilt-android:2.51")
kapt("com.google.dagger:hilt-android-compiler:2.51")

// Room + VSS
implementation("androidx.room:room-runtime:2.7.0")
kapt("androidx.room:room-compiler:2.7.0")
implementation("androidx.room:room-ktx:2.7.0")
// SQLite‑VSS (via https://github.com/alexmojaki/sqlite-vss)
implementation("com.github.alexmojaki:sqlite-vss:0.5.0")

// WorkManager
implementation("androidx.work:work-runtime-ktx:2.9.0")

// ONNX Runtime Mobile
implementation("com.microsoft.onnxruntime:onnxruntime-mobile:1.19.0")

// TensorFlow Lite (fallback)
implementation("org.tensorflow:tensorflow-lite:2.16.1")
implementation("org.tensorflow:tensorflow-lite-support:0.4.0")

// fastText language ID
implementation("com.github.kojimako:fasttext-java:0.2.0")

// Tesseract (via TessTwo)
implementation("com.rmtheis:tess-two:9.1.0")

// Cartesia SDK (if provided as Maven)
implementation("ai.cartesia:cartesia-sdk:0.1.0") // placeholder

// Logging
implementation("com.orhanobut:logger:2.2.0")

// Testing
testImplementation("junit:junit:4.13.2")
testImplementation("org.mockito.kotlin:mockito-kotlin:5.0.0")
androidTestImplementation("androidx.test.ext:junit:1.2.0")
androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
debugImplementation("com.squareup.leakcanary:leakcanary-android:2.13")
```

*All AI model files (ONNX/TFLite) will be placed in `app/src/main/assets/models/` and loaded at runtime.*

---

## 6. Permission Matrix

| Permission                              | Why Required                                                                 | Where Used                                                                 | Effect if Denied                                                            |
|-----------------------------------------|------------------------------------------------------------------------------|----------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| `POST_NOTIFICATIONS` (Android 13+)      | Listen to incoming notifications                                            | `NotificationListenerService`                                              | No notification processing; fallback to manual share.                      |
| `READ_SMS`, `RECEIVE_SMS`               | Read SMS for OTPs, reminders (optional)                                    | SMS listener (if enabled)                                                  | Cannot auto‑detect SMS‑based commitments.                                   |
| `READ_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE` (Android 13) | Access downloaded documents, images, screenshots | File picker, document scanner, OCR, local search                         | Cannot read user‑provided files; OCR limited to camera capture only.       |
| `CAMERA`                                 | Capture images for OCR, document scanning                                   | Camera module, OCR                                                         | No camera‑based OCR; user must upload existing images.                     |
| `RECORD_AUDIO`                           | Speech‑to‑Text input via Cartesia (or local fallback)                       | Voice command UI                                                           | Voice input disabled; fallback to typing.                                 |
| `CALENDAR` (READ/WRITE)                 | Create calendar events, read existing events                                | Calendar integration, daily digest                                         | Cannot add events to user calendar; can only show suggestions.            |
| `REMINDERS` (via `AlarmManager`/`Notification`) | Set reminders/alarms                                                       | Reminder creation, daily digest alerts                                     | Reminders will not fire; user must set manually.                           |
| `BIND_NOTIFICATION_LISTENER_SERVICE`    | System binding for notification listener                                    | Manifest‑declared service                                                  | Listener won’t start; no automatic notification processing.                |
| `FOREGROUND_SERVICE`                    | Keep listener alive while app in background                                 | NotificationListenerService                                                | Service may be killed; delayed processing.                                 |
| `ACCESS_FINE_LOCATION` (optional)       | Extract location from text (e.g., “Lab 2”) – not for tracking               | Entity extraction (location detection)                                     | Location field may be blank; no impact on core.                            |
| `INTERNET` (optional, for Cartesia)     | Call Cartesia cloud STT/TTS if local models not used                        | STT/TTS modules                                                            | If denied, app falls back to local offline STT/TTS (if bundled).           |
| `BLUETOOTH` (optional)                  | For future wearable integration                                             | Not used in MVP                                                            | N/A                                                                         |

*All permissions are requested at runtime with clear rationales; denied permissions gracefully disable the related feature.*

---

## 7. AI Model Plan

| Model                     | Purpose                                 | Format                | Approx. Size (quantized) | RAM Usage (peak) | Inference Runtime | Input                                   | Output                                      | Fallback / Notes                                 |
|---------------------------|----------------------------------------|-----------------------|--------------------------|------------------|-------------------|-----------------------------------------|---------------------------------------------|--------------------------------------------------|
| **Phi‑2‑mini (3.8 B)**    | Primary LLM for chatbot, intent fallback, summarization | ONNX (4‑bit) / TFLite | ~2.0 GB                  | ~2.5 GB          | ONNX Runtime Mobile | Tokenized text (≤ 512 tokens)           | Generated text (≤ 256 tokens)                | TinyLlama‑1.1B (≈600 MB) if OOM                |
| **TinyLlama‑1.1B**        | Fallback LLM (lighter)                 | ONNX (4‑bit)          | ~0.6 GB                  | ~0.9 GB          | ONNX Runtime Mobile | Same as above                           | Same as above                                 | Used when device RAM < 3 GB                     |
| **DistilBERT‑Intent**     | Intent classification (6 classes)      | ONNX (FP16)           | ~80 MB                   | ~120 MB          | ONNX Runtime Mobile | Tokenized sentence (≤ 128 tokens)       | Logits over 6 intents                       | Rule‑based regex fallback (date/time detection) |
| **DistilBERT‑NER**        | Entity extraction (Person, Location, Amount, Date/Time) | ONNX (FP16) | ~100 MB | ~150 MB | ONNX Runtime Mobile | Tokenized sentence (≤ 128 tokens) | Span labels (BIO) per token                 | Regex‑only extraction for dates/amounts        |
| **all‑MiniLM‑L6‑v2**      | Sentence embeddings for RAG            | ONNX (FP16)           | ~50 MB                   | ~80 MB           | ONNX Runtime Mobile | Tokenized text (≤ 256 tokens)           | 384‑dim vector                              | None (embedding is cheap)                      |
| **Tesseract LSTM**        | OCR (English, Telugu, Hindi)           | Traineddata files    | ~30 MB per language      | ~150 MB (runtime) | Tesseract native   | Image (gray)                            | UTF‑8 text string                           | EasyOCR (if bundled) as secondary              |
| **Custom Form Model**     | Detect structured fields (fee notices) | TensorFlow Lite      | ~20 MB                   | ~40 MB           | TensorFlow Lite    | Image (224×224)                         | Key‑value pairs (date, amount, etc.)        | Regex fallback on OCR text                     |
| **fastText Language ID**  | Detect language/mix                    | Binary                | ~5 MB                    | ~10 MB           | fastText           | Raw text (≤ 500 chars)                  | Lang code + probability                     | Simple char‑heuristic fallback                |
| **Cartesia STT (Whisper‑tiny)** | Speech‑to‑Text (online)            | ONNX (quantized)     | ~200 MB (if cached)      | ~250 MB          | ONNX Runtime Mobile | Audio waveform (16 kHz)                 | Text transcription                          | Local Vosk‑small (if Cartesia unavailable)    |
| **Cartesia TTS (Tacotron2+HiFi‑GAN)** | Text‑to‑Speech (online)        | ONNX (quantized)     | ~150 MB (if cached)      | ~200 MB          | ONNX Runtime Mobile | Text token sequence                     | Waveform (16 kHz)                           | Local Piper TTS (fallback)                     |

*All models are stored in `assets/models/` and loaded lazily via a ModelManager that keeps only the currently needed model in memory to reduce RAM pressure.*

---

## 8. Database Schema (Room)

| Table            | Columns (key)                                                                                                                                                                                                                              | Description                                                                 |
|------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| **tasks**        | `id INTEGER PK`, `title TEXT`, `description TEXT`, `status TEXT` (pending/done), `priority INTEGER`, `created_at INTEGER`, `updated_at INTEGER`, `source_type TEXT`, `source_id INTEGER` | Generic task entity (can be reminder, deadline, commitment).               |
| **deadlines**    | `id INTEGER PK FK→tasks(id)`, `due_date INTEGER (epoch ms)`, `due_time TEXT`, `location TEXT`                                                                                                                                            | Stores date/time/location specifics.                                        |
| **reminders**    | `id INTEGER PK FK→tasks(id)`, `trigger_time INTEGER (epoch ms)`, `repeat_rule TEXT` (none/daily/weekly), `alert_before INTEGER` (minutes)                                                                                                 | Alarm/reminder specifics.                                                  |
| **commitments**  | `id INTEGER PK FK→tasks(id)`, `promise_to TEXT`, `promise_text TEXT`, `created_from TEXT` (notification/sms/etc.)                                                                                                                         | Tracks promises made to others.                                            |
| **events**       | `id INTEGER PK FK→tasks(id)`, `event_title TEXT`, `event_start INTEGER`, `event_end INTEGER`, `event_location TEXT`, `calendar_id TEXT` (external calendar uid)                                                                          | Calendar‑synced events.                                                    |
| **notifications**| `id INTEGER PK`, `package_name TEXT`, `post_time INTEGER`, `raw_title TEXT`, `raw_text TEXT`, `processed INTEGER` (0/1), `hash TEXT` (for dedup)                                                                                         | Raw notification payload; processed flag after extraction.                  |
| **documents**    | `id INTEGER PK`, `file_path TEXT`, `mime_type TEXT`, `title TEXT`, `content_text TEXT`, `ocr_text TEXT`, `embedding BLOB`, `indexed INTEGER`                                                                                               | Indexed files for search & RAG.                                            |
| **memories**     | `id INTEGER PK`, `category TEXT` (college/branch/project/person/note), `key TEXT`, `value TEXT`, `updated_at INTEGER`                                                                                                                      | User‑editable personal knowledge base.                                     |
| **people**       | `id INTEGER PK`, `name TEXT`, `phone TEXT`, `email TEXT`, `relation TEXT`, `last_interaction INTEGER`                                                                                                                                                    | Contacts extracted from messages/notifications.                            |
| **automation_history** | `id INTEGER PK`, `action_type TEXT`, `parameters JSON`, `executed_at INTEGER`, `result TEXT`, `user_confirmed INTEGER`                                                                                                                   | Audit log of performed actions.                                            |
| **chat_history** | `id INTEGER PK`, `role TEXT` (user/assistant), `message TEXT`, `timestamp INTEGER`, `intent TEXT` (optional)                                                                                                                            | Conversation log for context.                                              |
| **embeddings**   | `entity_type TEXT`, `entity_id INTEGER`, `vector BLOB` (384‑dim)                                                                                                                                                                           | Separate table for fast vector search (or use SQLite‑VSS virtual table).   |

*Indices on `created_at`, `due_date`, `post_time`, and foreign keys for cascade delete.*

---

## 9. Action Registry (Whitelisted Android Actions)

| Action ID                | Description                                                                                       | Required Permissions                | Confirmation Needed? | Implementation Hint |
|--------------------------|---------------------------------------------------------------------------------------------------|-------------------------------------|----------------------|---------------------|
| `OPEN_APP`               | Launch an installed app via package name or resolved intent.                                      | None                                | No                   | `PackageManager.getLaunchIntentForPackage` |
| `OPEN_DEEP_LINK`         | Open a URL with `Intent.ACTION_VIEW`.                                                             | None                                | No                   | `Intent(Intent.ACTION_VIEW, Uri.parse(url))` |
| `CREATE_REMINDER`        | Create a reminder via `AlarmManager` or `ReminderContract` (API 24+).                            | `SCHEDULE_EXACT_ALARM` (if exact)   | Yes (if exact time)  | Use `AlarmManager.setExactAndAllowWhileIdle`. |
| `CREATE_CALENDAR_EVENT`  | Insert into `CalendarContract.Events`.                                                            | `WRITE_CALENDAR`                    | Yes (unless user enabled “auto‑add”) | Use `ContentResolver.insert`. |
| `DRAFT_MESSAGE`          | Fill a share intent with `ACTION_SENDTO` or `ACTION_SEND` (pre‑filled text).                     | None                                | No                   | Build `Intent` with `EXTRA_TEXT`. |
| `SEND_MESSAGE`           | Actually send SMS via `SmsManager` (only if user granted).                                        | `SEND_SMS`                          | Yes                  | Use `SmsManager.sendTextMessage`. |
| `SHARE_TEXT`             | Share plain text via chooser.                                                                     | None                                | No                   | `Intent.createChooser`. |
| `SEARCH_FILES`           | Query local `documents` table + file system (via `Storage Access Framework`).                     | `READ_EXTERNAL_STORAGE`/`MANAGE_EXTERNAL_STORAGE` | No   | Use `DocumentFile` + SQL `LIKE`/`MATCH`. |
| `OPEN_SETTINGS`          | Launch Android Settings.                                                                          | None                                | No                   | `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`. |
| `TOGGLE_FLASHLIGHT`      | Turn camera flash on/off.                                                                         | `CAMERA`                            | No                   | Use `CameraManager.setTorchMode`. |
| `START_VOICE_INPUT`      | Launch STT UI (Cartesia local or system).                                                         | `RECORD_AUDIO`                      | No                   | Start activity with recognizer intent or custom STT service. |
| `SHOW_OCR_RESULTS`       | Display OCR‑extracted text in a dialog.                                                           | None                                | No                   | Simple UI. |
| `SET_PRIORITY`           | Update priority field of a task.                                                                  | None                                | No                   | Room update. |
| `MARK_DONE`              | Set task status to DONE.                                                                          | None                                | No                   | Room update. |
| `SNOOZE`                 | Delay reminder/deadline by N minutes.                                                             | None                                | No                   | Update trigger time. |
| `DELETE_TASK`            | Remove task and related rows.                                                                     | None                                | Yes (confirm)        | Room delete + cascade. |
| `ADD_TO_MEMORY`          | Insert or update a row in `memories`.                                                             | None                                | No                   | Room upsert. |
| `QUERY_MEMORY`           | Return matching memories (full‑text search).                                                      | None                                | No                   | FTS5 or simple `LIKE`. |
| `LAUNCH_CAMERA`          | Open camera for picture capture.                                                                  | `CAMERA`                            | No                   | `MediaStore.ACTION_IMAGE_CAPTURE`. |
| `LAUNCH_FILE_PICKER`     | Open SAF to pick a file.                                                                          | None                                | No                   | `Intent.ACTION_OPEN_DOCUMENT`. |
| `RUN_BACKUP` (online)    | Trigger encrypted cloud backup (if user enabled).                                                 | `INTERNET`                          | Yes                  | Call backup service. |

*All actions go through the `ActionExecutor` which checks permission, runs confirmation dialog (if needed), executes, logs to `automation_history`, and returns a result.*

---

## 10. Development Roadmap (Milestones)

| Milestone | Goal | Key Features | Target Duration |
|-----------|------|--------------|-----------------|
| **M0 – Project Setup** | Repo, CI, basic Android app with Compose | Gradle, Hilt, Navigation, basic UI skeleton | 3 days |
| **M1 – Notification Core** | Listen, store, simple regex deadline detection | `NotificationListenerService`, Room, `tasks`/`deadlines` tables, UI “Inbox” screen | 5 days |
| **M2 – Intent & Entity Models** | Add DistilBERT intent classifier & NER, ONNX runtime integration | Intent classification, entity extraction (date/time, amount, person, location), confidence scoring | 7 days |
| **M3 – Priority & Dedup** | Importance scoring, duplicate detection, UI “Today” screen | Priority algorithm, dedup hash, display of detected items with action cards | 4 days |
| **M4 – Reminder & Calendar Actions** | Implement `CREATE_REMINDER`, `CREATE_CALENDAR_EVENT` with confirmations | AlarmManager, CalendarContract, action registry, confirmation dialogs | 5 days |
| **M5 – Basic Offline Chatbot** | TinyLlama/Phi‑2 mini for Q&A, task creation via chat | LLM loading, prompt templating, chat UI, intent → action mapping | 8 days |
| **M6 – Local Search & Embeddings** | MiniLM embeddings, SQLite‑VSS, semantic search over notifications & documents | Embedding generation, vector store, search bar in chat & documents screen | 6 days |
| **M7 – OCR + Camera Module** | Tesseract integration, image capture, OCR preview, simple post‑processing (date/amount regex) | CameraX, TessTwo, OCR result dialog, “Add to Calendar/Reminder” buttons | 7 days |
| **M8 – Document Intelligence** | PDF/PNG/JPG parsing, text extraction, summarization (LLM), entity extraction from docs | PdfRenderer library, OCR fallback, summarization pipeline, “Ask about document” chat feature | 8 days |
| **M9 – Voice Interaction (Cartesia)** | STT/TTS via Cartesia (online) with local fallback option, voice command to chat | Permission handling, streaming STT, TTS playback, voice‑to‑intent pipeline | 6 days |
| **M10 – Personal Memory System** | CRUD UI for memories, linking memories to detected items, memory‑augmented chat | Memory editor, tagging, memory search, chat uses memory context | 5 days |
| **M11 – Automation Engine & Action Orchestrator** | Generic action planner, permission & confirmation layer, audit log | Action registry, executor service, audit UI, undo capability | 6 days |
| **M12 – Daily Digest & Smart Prioritization** | Evening digest generation, smart priority tuning UI | Digest generator (uses today/upcoming/promises), settings for priority weights, notification channel tuning | 4 days |
| **M13 – Privacy Dashboard & Settings** | Transparent stats, data deletion, model management, online toggle | Dashboard fragment, clear data button, model selector (local/online), battery optimization settings | 4 days |
| **M14 – Performance & Battery Optimization** | Profile, reduce wake‑locks, batch model loads, use WorkManager for cleanup | Background task scheduling, model unloading, wakelock stats, battery‑friendly flags | 5 days |
| **M15 – Polish, Testing, Release Candidate** | QA, UI/UX refinements, localization (EN/TE/HI), Play Store internal test | Espresso UI tests, unit tests, crash‑free runs, localization, store listing | 7 days |
| **Total** | — | — | **≈ 90 days (3 months)** |

*Parallel work possible: e.g., OCR and document pipelines can proceed alongside chatbot development.*

---

## 11. Test Plan

| Test Type | Scope | Tools / Frameworks | Acceptance Criteria |
|-----------|-------|--------------------|---------------------|
| **Unit Tests** | ViewModels, Use Cases, Model managers, ActionExecutor | JUnit5, MockK, Turbine (for Flows) | ≥ 80 % coverage; each business logic function tested. |
| **Instrumentation UI Tests** | Main navigation, action cards, chat flow, OCR dialog, memory editor | Espresso, Compose Test | Critical user flows (notification → add reminder, voice command → open app) succeed on API 21‑34 emulators & real devices. |
| **Notification Tests** | Simulate incoming notifications (via `adb shell cmd notification post`) | AndroidJUnitRunner + `NotificationListenerService` test harness | Detection accuracy ≥ 85 % on a curated dataset of 200 sample notifications (English/Telugu/Hindi mix). |
| **OCR Tests** | Sample images of fee notices, exam schedules, handwritten notes | JUnit + leptonica/Tesseract compare | Character error rate (CER) ≤ 10 % on clean prints; ≥ 70 % recall of date/amount fields. |
| **STT/TTS Tests** | Cartesia API mock (wiremock) + local fallback | MockWebServer, AudioRecord playback | Latency < 1.2 s for STT, intelligibility MOS ≥ 3.5 for TTS. |
| **AI Accuracy Tests** | End‑to‑end pipeline: notification → structured event → action suggestion | Custom test harness with golden dataset | Intent classification F1 ≥ 0.88; Entity extraction F1 ≥ 0.82; Action suggestion relevance ≥ 0.8 (user study). |
| **Permission Tests** | Deny each permission, verify graceful degradation | UI Automator + permission mocking | Feature disabled, appropriate toast/dialog shown, no crash. |
| **Battery Tests** | Run typical usage (10 notifications/hr, 5 chat turns, 2 OCR scans) for 4 hrs | Android Battery Historian, `adb shell dumpsys battery` | Average drain ≤ 5 % per hour with idle screen; peak CPU < 30 % during inference bursts. |
| **Memory Tests** | Monitor RAM usage via `adb shell procrank` | Android Studio Profiler | Peak RAM ≤ 450 MB on a 4 GB device (models loaded/unloaded as needed). |
| **Stress / Longevity** | 24‑hour background run with periodic notifications & actions | Custom script + logcat monitoring | No crashes, memory leak < 5 MB growth, no ANR. |
| **Local‑Only Mode** | Turn off internet, verify all core features work | Disable Wi‑Fi/Mobile data, run test suite | Notification detection, chat, OCR, reminders, file search, memory all functional. |
| **Online‑Enhancement Mode** | Enable Cartesia, cloud backup (if any) | Same as above with network | Online features activate, fallback to local when network lost. |

*All tests run on GitHub Actions (emulators) and nightly on a device farm (Firebase Test Lab) covering a range of SoCs (Snapdragon 8 Gen 2, MediaTek Dimensity 9000, Exynos 2200).*

---

## 12. Final Design Principle (Recap)

> **Something arrives → Chitti understands it → Chitti remembers it → Chitti organizes it → Chitti offers the next action → User confirms when necessary → Chitti executes the supported action**

Chitti is built as a **single intelligent layer** that sits between the raw streams hitting the phone (notifications, messages, files, camera, voice) and the user’s intent to act. By keeping all processing local, exposing privacy controls, and providing a clear, consistent action flow, the assistant reduces cognitive load and turns the phone into a proactive partner rather than a passive repository.

---

**End of Specification**  
*(This document serves as the definitive plan for implementing Chitti. All subsequent development should trace back to the sections above.)*  

---