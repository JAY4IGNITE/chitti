# Chitti — Implementation Plan

Android app that catches deadlines and commitments buried in notifications, using an on-device LLM, with full voice interaction. Built for iQOO Hackathon 2026, Hyderabad City Battle (Sept 26–27). Team: Owl Coders.

**Key dates**
- Idea submission (Phase 1): 22 Sept 2026 — team leader submits by 20 Sept
- Event: 26–27 Sept 2026
- Deck: 6 slides, PDF/PPT, max 25 MB

---

## 0. Agent rules — for any AI assistant working on this project

These apply for the whole build, every session, no exceptions.

### Accuracy & honesty
1. **Never hallucinate.** Don't invent APIs, library methods, file contents, benchmark numbers, or behavior you haven't verified. If you're not sure whether something exists or works a certain way, say so explicitly rather than guessing — and check docs/source or ask before proceeding.
2. **No invented data in pitch material.** Benchmarks, accuracy numbers, or performance claims used in the deck or demo must come from actual test runs on this project, not estimates dressed up as measurements.
3. **Cite what you verified vs. what you assumed.** When proposing a library, model, or API, distinguish "I confirmed this exists and works this way" from "this is my best understanding, unverified" — don't blur the two.

### Decision-making
4. **Ask before assuming.** If a requirement, design choice, or piece of missing context could go more than one way, stop and ask Nandu rather than picking silently.
5. **Don't silently expand or shrink scope.** If a task turns out bigger or smaller than expected once you're in it, say so and check in — don't quietly cut a feature or quietly gold-plate one.
6. **Flag risk, don't hide it.** If something is likely to fail live (permissions, battery killing the listener, model latency, a flaky voice pipeline), say so as soon as it's discovered, not after it's baked into the demo plan.

### Code quality
7. **Match the existing stack and conventions** in this plan (Kotlin/native Android, Room, MediaPipe, etc.) rather than introducing a new library or pattern without flagging why.
8. **Don't over-engineer.** This is a focused build with a real deadline pressure even outside the hackathon window — prefer the simplest thing that correctly does the job over a more "elegant" abstraction that adds risk or time.
9. **Test on real data, not synthetic examples**, wherever the plan calls for it (filter keywords, extraction accuracy, voice transcription) — synthetic test messages hide the real-world failure modes (code-mixing, slang, noisy audio) that matter most here.
10. **Leave the codebase understandable**, not just working — comments where a decision isn't obvious, especially around model choice, prompt design, and any fallback/degradation logic.

### Process
11. **Produce a `handoff.md` at the end of every session.** Overwrite or append — Nandu will say which. It must contain:
    - What was done this session (files touched, decisions made, why)
    - Current state of the build (what works, what's broken, what's untested)
    - Open questions / things that need Nandu's input before continuing
    - Exact next steps for the following session
12. **Keep this plan.md in sync with reality.** If testing reveals something in this plan is wrong (latency, accuracy, permission behavior, a library that doesn't work as expected), update the plan itself and note the change in that session's handoff — don't let the document quietly drift out of date.

---

## 1. Feature set

### Core (must-have for demo)
- Passive notification capture from WhatsApp, SMS, email — no manual entry
- Two-stage detection: keyword/date filter → on-device LLM extraction (what/when/who)
- Action card the moment something's caught: Add to calendar / Set reminder / Dismiss
- One-tap calendar entry + system reminder/alarm
- "Today" screen — everything Chitti caught, with status (pending/actioned/dismissed)
- Works fully offline, 100% on-device — provable live via airplane mode
- **Full open-ended voice interaction** — user can talk to Chitti naturally (ask what's pending, snooze/reschedule by voice, general follow-up questions), Chitti replies by voice. Silero VAD gates when the mic is actively listening; STT transcribes; the extraction LLM (or a lightweight intent layer on top of it) interprets the request; TTS speaks the response.

### Secondary (build if core is solid with time to spare)
- Evening digest ("3 things you promised today") — read aloud via TTS
- Draft-reply suggestion for request-type messages
- Manual correction — user edits what/when/who if extraction is wrong

### Trust & transparency (strengthens the "why on-device" pitch)
- "Why did Chitti catch this?" explainer — tap a card to see which words/patterns triggered it
- On-device activity log — "X notifications scanned, Y flagged, 0 sent anywhere," makes the privacy claim visibly provable in the demo
- Per-app toggle — choose exactly which apps Chitti listens to

### Stretch (only if everything above is done and stable)
- Priority/urgency scoring on cards
- Recurring commitment detection
- Multi-item extraction from a single message
- Cross-reference with existing calendar for conflicts
- Weekly digest, missed-deadline tracking
- Widget, Wear OS companion

### Gamification & sharing (opt-in, local-only — no backend)
- **Streaks** — "X days in a row actioning caught deadlines" — computed and stored purely on-device (Room DB), no server-side leaderboard
- **Badges/milestones** — e.g. first week used, 50 items caught, 0 missed this month — local achievement state only
- **Personal stats screen** — total caught, action rate, most common message source — same local activity log data already planned for transparency, reframed as a personal-progress view
- **Device-to-device sharing** — share a caught event as a card image/text via Android's native share sheet, Nearby Share, or QR code, so a user can hand off an item to someone else's device directly. No server relay — the phones talk to each other or the user shares to WhatsApp manually, same as sharing any other content
- **Explicitly excluded:** cloud leaderboards, friend comparisons, or any feature requiring accounts/a server — these would need a backend and would directly conflict with the "no data leaves the device" architecture decision (see §3). If a cloud-backed social layer is wanted later, treat it as a separate post-hackathon product decision, not an extension of this build.

---

## 2. Screens

| Screen | Purpose | Priority |
|---|---|---|
| Onboarding / permission | Explain why notification + mic access are needed before the system dialogs fire | Core |
| Home / "Today" | List of everything captured today, status per item | Core |
| Action card (overlay) | Appears live when something's detected — what/when/who + actions | Core |
| Event detail | Source message, editable extracted fields, action history | Core |
| Voice interaction screen/overlay | Mic state indicator (listening / thinking / speaking), transcript, response | Core — new, for open-ended conversation |
| Settings | Monitored apps, model info, voice on/off, "AI runs 100% on this device" statement | Core |
| "Why Chitti caught this" explainer | Tap-through from a card | Recommended |
| Activity/transparency log | Scanned/flagged/sent-nowhere counts | Recommended |
| Evening digest | Summary view, TTS-readable | Secondary |
| History (all past events) | Browse beyond today | Nice-to-have |
| Personal stats / streaks | Local gamification — streaks, badges, action-rate stats | Nice-to-have, opt-in |
| Share sheet (event card) | Device-to-device share of a caught event, no server relay | Nice-to-have, opt-in |

---

## 3. Architecture

### Notification pipeline
```
Notification arrives
      ↓
Quick filter (keywords + date/time patterns)   ← ~90% of messages stop here
      ↓ (only ~10% pass through)
On-device LLM (extracts what / when / who → JSON)
      ↓
Action card (Deadline detected — add to calendar?)
      ↓
Room DB → Calendar / Alarm / Draft reply / "Today" screen
```

### Voice pipeline (new)
```
Microphone stream
      ↓
Silero VAD (streaming, ~32ms chunks)   ← gates everything below; STT only runs when speech is actually detected
      ↓ (speech detected)
STT (Whisper Tiny, on-device)   → transcript
      ↓
Intent handling: match against known commands (what's pending / snooze X / mark done / ask about an event)
  → if it maps to a known action: execute directly against Room DB
  → if open-ended: pass transcript + relevant context to the on-device LLM for a natural-language response
      ↓
TTS (Android built-in TextToSpeech) → spoken response
```

**Why VAD first:** Silero VAD v5 runs via ONNX Runtime on Android, is ~2MB, processes in 32ms chunks at sub-millisecond latency per chunk. It exists purely to avoid running STT (which is far more expensive) continuously — STT only wakes up when actual speech is present. This is what keeps a full conversational loop from destroying battery life during a live demo.

**Hard fallback requirement:** the voice pipeline must degrade gracefully. If VAD/STT/TTS fails or mic permission is denied, every core feature (notification capture, action cards, calendar) must still work with taps alone. Do not let a voice failure take down the notification-catching demo — that's the actual differentiator.

**Not yet verified — resolve during Phase 3b:**
- Exact Whisper Tiny on-device latency on a mid-range/iQOO-class phone for short utterances — benchmark this yourself, don't estimate
- Whether the intent layer needs a second, separate LLM call from the extraction model, or can share the same loaded model — decide once you see real latency numbers, since loading two models simultaneously has a real memory/battery cost

### No backend — by design
Chitti has no cloud-hosted backend, server, or account system, and this is a deliberate architecture decision, not a gap to fill in later. Every feature — capture, filter, extraction, voice, gamification, sharing — runs and stores data entirely on-device. The only external network call in the whole app is the one-time model download from a public model host (e.g. Hugging Face) on first launch, which touches no user data. This is what makes the "no message ever leaves the phone" claim literally true rather than a caveat-laden simplification, and it removes an entire class of live-demo failure modes (auth, hosting, venue wifi). Any future feature proposal that implies a server (cloud sync, cloud leaderboards, remote analytics) should be treated as a separate, explicit product decision — not something to add quietly.

---

## 4. Tech stack

| Layer | Choice | Notes |
|---|---|---|
| Language/UI | Kotlin, native Android | Needs direct `NotificationListenerService` access |
| Notification capture | `NotificationListenerService` + foreground service | Foreground service prevents OEM battery-optimization kills |
| Filter | Regex/keyword pass in Kotlin | No ML here — cheap and fast, runs on every notification |
| On-device extraction LLM | Gemma 3 270M (primary candidate) or Gemma 3 1B, via MediaPipe LLM Inference | Pick after benchmarking on real code-mixed messages — see §7. Gemma 3 1B's published multilingual benchmarks are notably weaker than 4B+; do not assume it beats 270M on Telugu-English without testing both |
| Voice activity detection | Silero VAD v5 via ONNX Runtime | ~2MB, 32ms chunks, sub-ms latency — gates STT |
| Speech-to-text | Whisper Tiny (on-device) | Benchmark latency on real hardware before committing |
| Text-to-speech | Android built-in `TextToSpeech` API | No model download, no extra latency budget |
| Storage | Room (SQLite) | Local only, no server |
| Calendar/alarm | Android CalendarContract + AlarmManager | |

**No backend. No data leaves the device, ever.** This is a hard constraint, not a nice-to-have — it's the entire justification for on-device AI in the pitch, and voice makes it an even stronger demo beat (a live conversation, entirely offline).

---

## 5. Team split (3 members)

| Owner | Scope |
|---|---|
| Person A | Capture + filter: `NotificationListenerService`, foreground service, keyword/date filter |
| Person B | Models: extraction LLM integration + benchmarking, VAD + STT + TTS pipeline, intent handling |
| Person C | Screens + pitch: action card UI, voice interaction UI, "Today" screen, deck |

**Note:** Person B's scope has grown significantly with the voice pipeline (VAD + STT + TTS + intent handling on top of the original extraction model). If this proves too much for one person in 30 hours, redistribute — flag this early rather than discovering it mid-build.

---

## 6. Build phases

### Phase 1 — Skeleton (hours 0–5)
- [ ] Kotlin project scaffold, `NotificationListenerService` registered and receiving real notifications
- [ ] Foreground service + battery-optimization whitelist prompt on first launch
- [ ] Room schema: `CapturedEvent(id, sourceApp, rawText, extractedWhat, extractedWhen, extractedWho, status, timestamp)`
- [ ] **Doubt to resolve before this phase:** confirm target Android API level and min SDK against the loaner iQOO phone's OS version — do not assume, check on event day or ask Nandu if known in advance.

### Phase 2 — Filter (hours 4–9)
- [ ] Keyword list (submit, due, fee, meeting, class, deadline, tomorrow, today + Telugu/Hindi equivalents)
- [ ] Date/time pattern regex
- [ ] Unit test filter against a curated set of real (redacted) messages
- [ ] Log filter pass-through rate — target ~10%; flag it if wildly different, don't silently ship it

### Phase 3a — Extraction (hours 8–16)
- [ ] Integrate MediaPipe LLM Inference with chosen model
- [ ] Prompt returns strict JSON: `{what, when, who, confidence}`
- [ ] Benchmark actual latency on real hardware — record real numbers, do not estimate
- [ ] Test against real-message set, specifically code-mixed Telugu-English/Hinglish examples
- [ ] **Decision point — ask Nandu:** if accuracy on code-mixed text is poor at the smallest model size, confirm whether to step up model size or narrow demo scope

### Phase 3b — Voice pipeline (hours 10–20, parallel with 3a where possible)
- [ ] Integrate Silero VAD (ONNX Runtime) for streaming speech detection
- [ ] Integrate Whisper Tiny for STT, wired behind VAD
- [ ] Integrate Android TextToSpeech for responses
- [ ] Build intent layer: known-command matching + fallback to LLM for open-ended queries
- [ ] Benchmark full round-trip latency (speak → hear response) on real hardware
- [ ] Build the graceful-degradation fallback (voice off → app still fully usable by tap)
- [ ] **Decision point — ask Nandu:** if round-trip voice latency is too slow to feel "live" in a demo, decide whether to narrow the voice demo to a couple of pre-tested phrases rather than fully improvised conversation

### Phase 4 — Action card + Today screen + voice UI (hours 14–24)
- [ ] Card UI: detected event → Add to calendar / Set reminder / Dismiss
- [ ] "Today" screen listing captured events
- [ ] Voice interaction UI: listening/thinking/speaking states, transcript display
- [ ] Loading/"thinking" state on cards while model runs

### Phase 5 — Demo hardening (hours 22–28)
- [ ] Debug "replay saved notification" button — guarantees a working demo path if the listener misbehaves live
- [ ] Voice fallback tested explicitly: what happens on stage if the mic mishears or VAD doesn't trigger
- [ ] Rehearse full demo script (see below) at least 3 times, including airplane-mode proof
- [ ] Pre-warm all models at app launch to avoid cold-start delay during demo

### Phase 6 — Deck + submission (hours 24–30, parallel with Phase 5)
- [ ] 6 slides per the outline below
- [ ] Team leader submits Phase 1 form by 20 Sept
- [ ] All benchmark/accuracy numbers in the deck pulled from actual test runs, including voice round-trip latency

---

## 7. Model selection — needs real testing, not assumption

- **Gemma 3 270M**: purpose-built for entity extraction/structured JSON, official 140+ language multilingual claim, smallest and fastest. Best first candidate to benchmark.
- **Gemma 3 1B**: published multilingual benchmarks (e.g. MGSM) are notably weak at this size compared to 4B+ — don't assume it's automatically better than 270M for Telugu-English without testing.
- Test both against ~30 real messages from your own WhatsApp groups, including several genuinely code-mixed ones, before finalizing. This decision directly affects both the extraction pipeline and the voice intent layer if they end up sharing a model.

---

## 8. UI / design direction

Nandu's established preference is skeuomorphic, highly tactile UI over flat/minimal Material Design — this should carry through Chitti's design rather than defaulting to generic Android styling.

**Core metaphor: "Chitti" = a small physical note/slip.** The UI should lean into that literally, not just in branding copy.

- **Action cards** — rendered like a torn paper slip or sticky note landing on a desk: subtle paper texture, soft drop shadow, slight rotation on appear, rather than a flat Material card sliding up
- **Today screen** — corkboard / desk-pad metaphor: today's caught items laid out like pinned notes or index cards, not a flat divided list
- **Voice interaction state** — an animated presence with personality (listening / thinking / speaking states), not a generic pulsing mic icon — could reuse animation/expression approach from the desk-companion project rather than building a new visual language from scratch
- **Calendar-add confirmation** — a tactile animation, e.g. the card visibly sliding into a calendar slot, instead of a flat toast/snackbar
- **Color & material** — warm paper tones, soft shadows, subtle grain texture — deliberately avoiding cold flat blues, to reinforce "personal notes" over "corporate productivity tool"
- **Typography** — a handwritten/marker-style accent font for "Chitti" branding and headers; clean, highly legible sans-serif for actual extracted message text and dates, since readability matters most there

**Open scope question, since this is being built ahead of the hackathon rather than inside the 30-hour window:** confirm whether the full skeuomorphic treatment is in scope for the hackathon demo build itself, or whether a simpler flat UI ships for the submission/demo and the full aesthetic treatment is a post-hackathon pass. Skeuomorphic detail (textures, custom animation states, tactile transitions) takes meaningfully longer to get right than flat Material components, even with more lead time than a single 30-hour sprint.

---

## 9. Demo script (rehearse this exactly)

1. Show a noisy WhatsApp group full of jokes/forwards on the demo phone.
2. Teammate sends: "Record submission tomorrow 10 am, Lab 2."
3. Card appears: "Deadline detected — add to calendar?"
4. Tap once → open calendar → show the entry.
5. Send a code-mixed message (pre-tested, known to work) → show Chitti catches it too.
6. Ask Chitti out loud what's pending today → Chitti answers by voice, entirely on-device.
7. Switch phone to airplane mode → repeat a voice question and feed a saved test notification → prove everything, including voice, runs fully on-device.

If any step fails live, fall back to the debug replay button / pre-tested voice phrase rather than retrying — don't improvise on stage.

---

## 10. Deck outline (6 slides)

1. Problem — screenshot of a messy group chat (names hidden)
2. Solution — mock-up of the action card + voice interaction
3. Architecture — the notification pipeline and the voice pipeline
4. Why on-device — privacy, offline, battery, and how voice makes this even more compelling
5. 30-hour build plan and ownership
6. Team and why this problem matters to us

---

## 11. Open questions (resolve before/early in build — do not guess)

- [ ] Final extraction model choice (Gemma 3 270M vs 1B) — pending real benchmark on target hardware and code-mixed text
- [ ] Min SDK / target Android version, once loaner phone specs are known
- [ ] Whether the voice intent layer shares the extraction model or needs its own — pending latency/memory testing
- [ ] Realistic full-round-trip voice latency on real hardware — decides whether the demo does fully improvised conversation or a couple of pre-tested phrases
- [ ] Confirm "Owl Coders" is the correct registered team name for the submission form
- [ ] Confirm whether Person B's scope (extraction model + full voice pipeline) needs a second owner given how much it's grown
- [ ] Confirm whether the full skeuomorphic UI treatment ships for the hackathon demo itself, or a simpler flat version ships first with the full aesthetic as a post-hackathon pass

---

## 12. Session handoff protocol

At the end of every work session, produce `handoff.md` with:
```
## Session date/time
## What was done
## Current state (working / broken / untested)
## Open questions for Nandu
## Next steps
```
If anything in this plan turns out to be wrong once real testing starts (latency, accuracy, permission behavior), update this plan.md and note the change in that session's handoff — don't let the plan silently drift out of sync with reality.
