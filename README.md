# Chitti - The 100% On-Device AI Notification Assistant 🤖

Chitti is a privacy-first, purely on-device AI assistant built for the **iQOO Hackathon City Battle**. It acts as your personal secretary by passively intercepting notifications (like WhatsApp or SMS) and extracting critical commitments, deadlines, and meetings into actionable items—without ever sending your data to the cloud.

---

## 🌟 Key Features

* **Zero Cloud, Total Privacy:** Powered by **Gemma 1.1 (2B)** running locally on your device via the MediaPipe Android SDK. Your personal messages never leave the phone.
* **Intelligent Auto-Extraction:** A custom Regex + LLM pipeline identifies important messages and extracts them into structured JSON (`what`, `when`, `who`).
* **Skeuomorphic "Desk Pad" UI:** Built entirely in Jetpack Compose, the UI uses warm paper tones, custom ink fonts, and rotated sticky notes to give a tactile, human feel.
* **Voice-First Pipeline:** Built-in support for Silero Voice Activity Detection (VAD) and Whisper STT for completely hands-free interaction.
* **Demo-Hardened Concurrency:** Built-in Mutex locks and KV Cache safeguards ensure the LLM handles rapid bursts of notifications safely without crashing.

---

## 🏗️ Architecture & Pipeline

Chitti operates on a two-track architecture designed for absolute minimum battery drain:

1. **The Notification Pipeline:**
   - **Capture:** `NotificationCaptureService` intercepts incoming messages.
   - **Filter:** `NotificationFilter` uses Regex to block 90% of noise (like "haha" or memes) and only wakes the LLM for potential tasks (e.g., "submit", "deadline", "tomorrow").
   - **Extract:** The 1.5GB `ExtractionEngine` (MediaPipe Gemma) is pre-warmed in RAM and extracts the raw message into a structured SQLite Room Database event.
   - **Display:** The Jetpack Compose UI listens to the Room DB flow and dynamically generates "Thinking..." and "Resolved" sticky notes.

2. **The Voice Pipeline:**
   - **VAD (Silero):** Continuously listens at near-zero battery cost.
   - **STT (Whisper):** Transcribes audio only when human speech is detected.
   - **LLM Intent:** Analyzes the transcribed text for intent.
   - **TTS:** Android's native TTS engine responds aloud.

---

## 🛠️ Build & Installation

### Prerequisites
* **Android Studio Hedgehog** (or newer)
* **Android SDK 34**
* A physical Android device with at least 8GB RAM (Tested on Motorola Edge 50 Pro)

### 1. Download the Gemma Model
Because the Gemma LLM weights are too large for GitHub, you must download them manually.
1. Download `gemma-1.1-2b-it-cpu-int4.bin` from Kaggle/Google.
2. Connect your device via USB with USB Debugging enabled.
3. Push the model directly to the device's local tmp folder:
   ```bash
   adb push path/to/gemma.bin /data/local/tmp/gemma.bin
   ```

### 2. Build the App
1. Clone this repository:
   ```bash
   git clone https://github.com/NandiVardhan2007/chitti.git
   ```
2. Open the project in Android Studio.
3. Sync Gradle and click **Run**.

### 3. Grant Permissions
When you launch Chitti for the first time:
1. Tap the **"Enable Notification Access"** button on the screen.
2. Toggle "Chitti" to ON in your system settings.
3. Return to the app. Send a test WhatsApp message to the phone (e.g., *"Don't forget to submit the hackathon pitch deck by 10 AM tomorrow!"*).
4. Watch the sticky note appear dynamically on your corkboard!

---

## 👨‍💻 Team
Built with passion by **Owl Coders** for the iQOO Hackathon.
