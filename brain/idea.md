# 🧠 Chitti — The On-Device Multimodal AI Executive Assistant

## 1. Executive Summary & Vision

**Chitti** is an ultra-fast, 100% on-device personal AI executive assistant built natively for Android. Inspired by Google Gemini's aesthetic and computational elegance, Chitti bridges the gap between privacy-preserving on-device computing and fluid, human-like voice interactivity.

Modern smartphone users suffer from extreme information fragmentation:
- Commitments are buried inside WhatsApp group messages, Telegram chats, and SMS notifications.
- Critical invoices, certificates, and research documents downloaded months or years ago are lost within nested storage directories.
- Traditional cloud assistants (Siri, Alexa, Google Assistant) suffer from round-trip network latency, subscription paywalls, and severe privacy concerns regarding confidential personal notifications.

**Chitti solves this fundamentally by running entirely on the user's silicon:**
No cloud servers. Zero data egress. Millisecond response times.

---

## 2. Problem Statement

### 2.1 The Data Fragmentation Crisis
Every day, users receive dozens of deadlines, meeting invites, payment requests, and academic notices across third-party communication apps. Manually copying these into calendar apps or note-taking tools creates friction, causing missed commitments and deadlines.

### 2.2 The "Long Ago" File Discovery Bottleneck
Standard mobile file managers are optimized for chronologically recent files (e.g. today's downloads or camera photos). Finding a specific insurance policy, fee receipt, identity document, or academic syllabus from "long ago" (6 months to 2 years old) requires tedious scrolling or exact file naming recall.

### 2.3 Cloud Assistant Latency & Privacy Violations
Streaming audio and personal notification contents to cloud APIs (OpenAI, Anthropic, Google Cloud) exposes intimate personal chats, bank transactions, and medical reminders to third-party servers. Furthermore, when network connectivity drops or throttles, cloud assistants become completely inert.

---

## 3. The Chitti Solution: Edge-First Intelligence

Chitti acts as an always-vigilant local neural copilot:

1. **Autonomous Notification Synthesis**: Continuously listens to system notifications via `NotificationListenerService`, analyzes raw messages using local LLM/regex inference, and automatically extracts structured actionable commitments (Title, Due Date, Urgency, Category).
2. **Instant Natural Voice Assistant**: High-speed on-device speech-to-text with partial transcript recognition. Users can speak commands naturally:
   - *"Open WhatsApp"*
   - *"Open YouTube"*
   - *"Find files from long ago"*
   - *"Turn on flashlight"*
   - *"What's pending today?"*
3. **Deep Historical File Finder**: A specialized storage discovery engine with dedicated time filters (`Today`, `Past Week`, `Past Month`, and `Long Ago` for files older than 6 months), instant category filtering (Documents, Images, Media, Archives), and integrated OCR scanning.
4. **Offline RAG & Generative Dialogue**: Powered by MediaPipe LLM Inference Engine with quantized Gemma 2B weights, enabling contextual retrieval over personal memory without leaking a single byte over the internet.

---

## 4. Key Value Propositions & Target Users

| Dimension | Cloud Assistants (Gemini Cloud, Siri) | Chitti (On-Device) |
| :--- | :--- | :--- |
| **Privacy & Security** | Data uploaded to remote server farms | **100% On-Device** (Data never leaves RAM/local Room DB) |
| **Latency** | 1,500ms – 4,000ms network round-trip | **< 200ms** for app launches and quick intents |
| **Offline Capability** | Fails completely without internet | **Full feature parity without SIM or Wi-Fi** |
| **Cost** | API tokens / monthly subscriptions | **Zero recurring operational costs** |
| **System Control** | Generic web links or restricted actions | **Native Android intents, camera, torch, and file system** |

### Target Personas:
- **Hackathon Judges & Technologists**: Demonstrating cutting-edge on-device GenAI (Gemma 2B INT4, ONNX runtime, Jetpack Compose).
- **Students & Researchers**: Instant retrieval of past term papers, fee receipts, and assignment deadlines scattered across messaging apps.
- **Privacy-Conscious Professionals**: Executives and developers who cannot risk enterprise communications syncing to public clouds.
