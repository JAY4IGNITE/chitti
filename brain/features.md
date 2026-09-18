# ✨ Features Specification & Capabilities Guide

Chitti integrates a comprehensive suite of intelligent on-device tools designed for real-world mobile workflows.

---

## 1. Natural Voice AI Assistant & Voice Overlay

### 1.1 Overview
An intuitive, floating Gemini-style interface that provides fluid speech recognition and immediate spoken responses.

### 1.2 Key Mechanics
- **Dynamic Waveform Visualizer**: Animates 7 multi-colored iridescent bars in real-time according to RMS microphone dB levels.
- **Glowing Cosmic Mic Orb**: Pulsing outer ripple and gradient inner orb indicating system listening and speaking states.
- **Fast-Response Silence Trigger**: Calibrated silence threshold (1,500ms – 2,000ms) with instant trigger upon recognizing complete app launcher commands.
- **Integrated Spoken TTS**: Cleans raw text, strips markdown artifacts (`*`, `#`, links), and articulates human-like spoken feedback via Android's local TextToSpeech engine.

---

## 2. Instant App Launcher & System Controls

### 2.1 Supported Voice Commands
Users can launch any application installed on their device using natural language:

| Spoken Intent | Resolved Action | Supported Synonyms |
| :--- | :--- | :--- |
| *"Open WhatsApp"* | Launches `com.whatsapp` or `com.whatsapp.w4b` | "Launch WhatsApp", "Go to WhatsApp" |
| *"Open YouTube"* | Launches `com.google.android.youtube` | "Start YouTube", "Open YT" |
| *"Open Camera"* | Launches `MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA` | "Take photo", "Open cam" |
| *"Open Settings"* | Launches `android.provider.Settings.ACTION_SETTINGS` | "Phone settings" |
| *"Open Phone"* | Launches `Intent.ACTION_DIAL` | "Dialer", "Call" |
| *"Turn on Flashlight"* | Invokes `CameraManager.setTorchMode(cameraId, true)` | "Torch on", "Flashlight on" |
| *"Turn off Flashlight"* | Invokes `CameraManager.setTorchMode(cameraId, false)` | "Torch off", "Stop flashlight" |
| *"Open Chrome"* | Launches `com.android.chrome` | "Open browser" |
| *"Open Spotify"* | Launches `com.spotify.music` | "Play music" |
| *"Open Maps"* | Launches `com.google.android.apps.maps` | "Google Maps", "Directions" |

---

## 3. Deep Historical File Finder ("Long Ago" Discovery)

### 3.1 The Problem It Solves
Users frequently misplace documents downloaded months or years ago (e.g. tax forms, hall tickets, old receipts). Traditional Android file pickers prioritize recent downloads, making historical discovery cumbersome.

### 3.2 Time Filters
- **Today**: Files modified in the last 24 hours.
- **Past Week**: Files modified in the last 7 days.
- **Past Month**: Files modified in the last 30 days.
- **Long Ago**: Files older than 180 days (6 months to multiple years), querying deep device storage.
- **All Time**: Unfiltered device storage query with indexed search.

### 3.3 Category Partitioning
- **All**: All file types.
- **Documents**: PDFs, DOC, DOCX, TXT, RTF, PPT, XLS.
- **Images**: PNG, JPG, JPEG, WEBP.
- **Media**: MP3, MP4, WAV, MKV.
- **Archives**: ZIP, RAR, 7Z, TAR.

### 3.4 In-App Actions
- **Instant Search**: Real-time keyword filtering by file name, path, and MIME type.
- **Native File Viewer**: Tap any file card to open with Android's system intent chooser (`FLAG_GRANT_READ_URI_PERMISSION`).
- **Pick Any File (SAF)**: Full Storage Access Framework document picker.
- **Scan Doc (Camera OCR)**: Camera capture with on-device ML Kit OCR to convert physical paper documents into searchable local index entries.

---

## 4. Autonomous Notification Capture & Event Extraction

### 4.1 System Listener
- Bound via `android.service.notification.NotificationListenerService`.
- Intercepts incoming messages from messaging apps (WhatsApp, Telegram, Slack, SMS).
- Filters out spam, marketing, OTPs, and low-priority alerts.

### 4.2 Entity Extraction Pipeline
- Extracts:
  - **What**: The core commitment (e.g. "Submit Hackathon Project").
  - **When**: Extracted timestamp or relative day ("tomorrow 10:00 AM").
  - **Who**: Sender or referenced individual.
  - **Category**: Work, Academic, or Personal.
  - **Urgency**: High, Medium, or Low.
- Dual execution: Gemma 2B INT4 LLM with rule-based regex fallback for multi-lingual and code-mixed formats (e.g. Telugu-English *"repu submission undi"*).

---

## 5. Agenda & Productivity Dashboard

- **Today Screen**: Prioritized cards showing upcoming commitments with urgency indicator badges (Red = High, Amber = Medium, Cyan = Work).
- **One-Tap Actions**: Quick chips on home screen to trigger WhatsApp, YouTube, File Finder, or Flashlight without voice.
- **Swipe-to-Delete**: Quick dismissal of completed commitments.
- **Dashboard Analytics**: Visual bar graphs displaying task distribution across categories and urgency levels.
