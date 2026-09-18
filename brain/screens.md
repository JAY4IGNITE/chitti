# 📱 Screens & UI/UX Design System

Chitti is designed around a modern **Gemini Cosmic Dark** visual theme, utilizing deep obsidian backgrounds (`#0B0E14`), elevated translucent glassmorphism surfaces (`#161B26`), iridescent linear borders (`#2A3245`), and dynamic Google Gemini accents:
- **Gemini Cyan**: `#22D3EE`
- **Gemini Blue**: `#3B82F6`
- **Gemini Purple**: `#A855F7`
- **Gemini Pink**: `#EC4899`
- **Gemini Amber**: `#F59E0B`
- **Gemini Green**: `#10B981`

---

## 1. Screen Architecture & Navigation

The application uses Jetpack Compose Navigation (`NavHost`) with 5 primary bottom-navigation destinations, supplemented by a global **Voice Overlay**:

| Route | Composable Screen | Purpose |
| :--- | :--- | :--- |
| `Screen.Home.route` | `TodayScreen` | Daily briefing, quick actions, and active commitments. |
| `Screen.Files.route` | `FilesScreen` | Deep historical file & document finder with time & type filters. |
| `Screen.Chat.route` | `ChatBotScreen` | Interactive text & speech dialogue with on-device AI. |
| `Screen.AiLab.route` | `AiLabScreen` | Live developer testing playground for notification simulation. |
| `Screen.Dashboard.route` | `DashboardScreen` | Analytics on commitments, categories, and urgency distribution. |
| `Screen.Settings.route` | `SettingsScreen` | Hardware status, model diagnostic info, and permission management. |
| Global Overlay | `GeminiVoiceOverlay` | Modal assistant overlay invoked from any screen via central mic FAB. |

---

## 2. Screen Deep Dive

### 2.1 Today Screen (`TodayScreen.kt`)
- **Ambient Header**: Breathing cosmic avatar with radial glow animation matching the time of day ("Good morning", "Good afternoon", "Good evening").
- **Quick Action Chips**:
  - `Open WhatsApp`: Green pill triggering instant WhatsApp launch.
  - `Open YouTube`: Pink pill triggering instant YouTube launch.
  - `Find Files`: Cyan pill navigating to File Finder.
  - `Toggle Flashlight`: Amber pill toggling camera torch.
  - `What's Pending?`: Blue pill synthesizing daily agenda.
- **Commitment Cards (`ChittiCard.kt`)**:
  - Color-coded urgency badges (High = Red, Medium = Amber, Low = Green).
  - Category indicator (Work, Academic, Personal).
  - Due date & extracted task description.
  - Swipe-to-delete gesture with animated card collapse.

### 2.2 File Finder Screen (`FilesScreen.kt`)
- **Search Header**: Modern outlined search bar with instant clear and search leading icon.
- **Time Filter Carousel**:
  - Filter chips: `All Time`, `Today`, `Past Week`, `Past Month`, and `Long Ago ⏳`.
  - Selecting `Long Ago` targets files older than 6 months (180 days) across device storage.
- **Category Filter Chips**:
  - Filter by `All`, `Documents`, `Images`, `Media`, and `Archives`.
- **Action Toolbar**:
  - `Pick Any File`: Opens system Storage Access Framework picker.
  - `Scan Doc`: Launches camera preview with automatic permission checks to extract text via OCR.
- **File List Items**:
  - File extension icon (PDF, DOC, ZIP, Image).
  - File name, formatted file size (KB / MB), and last modified date.
  - Tap opens the file with the system intent handler.

### 2.3 ChatBot Screen (`ChatBotScreen.kt`)
- **Gemini Chat Bubbles**:
  - User messages: Right-aligned with vibrant cyan/blue gradients.
  - Assistant messages: Left-aligned with elevated dark card containers.
  - Integrated `Listen` icon on assistant messages to trigger TTS speech on demand.
  - Action tags (e.g. `[App]`, `[Flashlight]`, `[Files]`) displaying resolved intent.
- **Thinking State**:
  - Uses `GeminiCircularProgressIndicator` with smooth cosmic sweep animation.
- **Input Bar**:
  - Rounded text field with send button and quick voice toggle.

### 2.4 AI Lab Screen (`AiLabScreen.kt`)
- **Notification Simulator**:
  - Test input box to paste raw notifications (e.g. *"Assignment due tomorrow at 5pm on Canvas"*).
  - Run button with `GeminiCircularProgressIndicator` during processing.
  - Inspection cards displaying extracted JSON entities (`what`, `when`, `who`, `urgency`, `category`, `confidence`).
  - Latency benchmark card (e.g. `14ms · Rule-based Regex` or `420ms · Gemma 2B`).

### 2.5 Voice Overlay (`VoiceOverlay.kt`)
- **Backdrop Scrim**: Semi-transparent dark blur dismissing on background touch.
- **Drag Handle**: Visual indicator for modal bottom sheet.
- **Waveform Visualizer**: 7 multi-colored pulsing bars reacting dynamically to audio volume.
- **Interactive Pulsing Orb**:
  - Glowing multi-layered radial rings with breathing animation.
  - Tap to start/stop listening.
- **Iridescent Bottom Lightbar**: Glowing animated bar replicating Google Gemini's signature mobile interface.
