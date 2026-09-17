# Chitti - AI Notification Assistant

Chitti is a 100% on-device AI assistant built for the iQOO Hackathon. It securely captures incoming notifications (like WhatsApp messages or SMS) and uses an on-device MediaPipe Gemma LLM to instantly extract important commitments, deadlines, and events—without any cloud backend.

## Features
- **Zero Cloud, Total Privacy:** Uses MediaPipe Gemma 1.1 (2B) running entirely on your local CPU.
- **Auto-Extraction:** Uses a strict LLM extraction pipeline to catch deadlines and format them into structured JSON.
- **Skeuomorphic UI:** A beautiful, tactile "Desk Pad" design built in Jetpack Compose to display your pending tasks as sticky notes.
- **Voice Pipeline:** Interacts with the user via Silero VAD, Whisper STT, and Android TTS.

## Architecture
- **Phase 1:** NotificationCaptureService to safely intercept messages.
- **Phase 2:** Keyword & Regex Filtering to wake the AI only when needed.
- **Phase 3:** On-device Gemma inference via MediaPipe Android SDK.
- **Phase 4:** Jetpack Compose UI (Corkboard & Sticky Notes).
- **Phase 5:** Concurrency queuing via Mutex locks to prevent KV Cache overflow.

## Build Requirements
- Android Studio Hedgehog or newer
- Android SDK 34
- **Note:** The `gemma-1.1-2b-it-cpu-int4.bin` model must be pushed manually to the Android device at `/data/local/tmp/gemma.bin` for the app to function.

## Team
Built by **Owl Coders** for the iQOO Hackathon.
