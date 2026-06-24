# PTSDAlertPOC — Claude Context

## What This Is

Android POC app (Kotlin + Jetpack Compose) that detects PTSD arousal events from physiological wearable data. The goal is to validate the detection pipeline before committing to full hardware integration.

Tech: Kotlin · Jetpack Compose · Material 3 · Kotlin Coroutines & Flow · JUnit 4  
minSdk = 26, compileSdk = 36. No DI frameworks (no Hilt/Koin/Dagger).

---

## Architecture: Hexagonal (Ports & Adapters)

The detection core (domain) knows nothing about hardware. Swapping a wearable = swapping which class implements `WearableDataSource`. Nothing else changes.

```
domain/          ← pure business logic, zero Android imports
  model/         ← PhysiologicalSample, ArousalState, LogEntry
  ports/         ← WearableDataSource, SimulatorControls, LogRepository (interfaces)
  detection/     ← DetectionEngine.classify(sample) → ArousalState

infrastructure/  ← adapters (implement domain ports)
  simulator/     ← SimulatorWearableDataSource (Flow-based, mode-switchable)
  tcp/           ← TcpWearableDataSource (connects to fake_ble_server.py on port 9999)
  bluetooth/     ← placeholder
  usb/           ← placeholder
  garmin/        ← placeholder
  polar/         ← placeholder
  alert/         ← AlertManager (vibration + sound), AlertService (foreground service), DismissReceiver
  logging/       ← AppLogger singleton, InMemoryLogRepository, SqliteLogRepository

presentation/    ← MonitoringViewModel, MonitoringUiState, MonitoringScreen (Compose)

DeviceProvider   ← factory singleton; change activeDevice here to swap adapters
DeviceType       ← enum: SIMULATOR, BLUETOOTH, USB, GARMIN, POLAR
MainActivity     ← entry point, sets content to MonitoringScreen()
```

### Detection Rules (DetectionEngine)

- HR > 100 → HYPERAROUSAL
- HR < 50  → HYPOAROUSAL
- else     → NORMAL
- null HR  → NORMAL

---

## Switching the Active Device

Edit one line in [DeviceProvider.kt](app/src/main/java/com/ptsdalert/DeviceProvider.kt):

```kotlin
val activeDevice: DeviceType = DeviceType.SIMULATOR  // ← change this
```

Options: `SIMULATOR`, `TCP`, `BLUETOOTH`, `USB`, `GARMIN`, `POLAR`  
(BLE, USB, Garmin, Polar adapters are stubs — they throw `NotImplementedError` at runtime.)

---

## Development Workflow: TCP + Fake Server

For testing with realistic data without real hardware:

1. Set `DeviceType.TCP` in DeviceProvider
2. Run the fake server: `python scripts/fake_ble_server.py`
3. For emulator: change `TcpWearableDataSource` host to `10.0.2.2`
4. For physical device: `adb reverse tcp:9999 tcp:9999`, keep host as `127.0.0.1`

**Fake server controls:** type a number (20–220) + Enter to change HR, `q` to quit.  
The server derives realistic HRV, skin temperature, and stress score from the HR value.

---

## Alert System (THPA-22 — merged to master 2026-06-23)

`AlertManager` triggers on non-NORMAL arousal state:
- Repeating vibration pattern (500ms on / 3500ms off) + alarm sound loop (every 8s, bypasses silent mode via `TYPE_ALARM`)
- `AlertService` foreground service starts at **app launch** (not reactively) with `PARTIAL_WAKE_LOCK` — keeps process alive through Doze so the detection coroutine never freezes
- Notification channel uses `USAGE_ALARM` AudioAttributes — bypasses DND
- Alert fires as a **separate notification (ID 1002)** with `setFullScreenIntent` pointing to `LockScreenAlertActivity`
- `LockScreenAlertActivity` shows full-screen coloured overlay on the lock screen (red=hyper, blue=hypo) with a **10-second hold-to-confirm** dismiss button (prevents accidental dismissal)
- Dismiss labels are state-specific: "I'm working out" (HYPERAROUSAL), "I'm ok" (HYPOAROUSAL)
- `DismissReceiver` + `DismissSignal` shared flow propagates dismiss back to ViewModel
- Android 14+: `USE_FULL_SCREEN_INTENT` permission requested on first launch via settings intent

---

## Logging

`AppLogger` is a global singleton backed by a `LogRepository` (swappable: in-memory default, SQLite available). Use `AppLogger.i/d/w/e(TAG, message)` everywhere — never `Log.x()` directly.

---

## Tests

```bash
./gradlew :app:test                                    # all unit tests
./gradlew :app:test --tests "com.ptsdalert.domain.*"  # domain only
./gradlew :app:assembleDebug                           # full build check
```

Test files live in `app/src/test/java/com/ptsdalert/` mirroring the main source tree.

---

## Project Management

I use the **atlassian-cli** skill to manage this project's tasks. It's a custom CLI tool I built that connects Claude to Jira and Confluence.

- **Jira project:** `THPA` at https://eyal-werber.atlassian.net
- **Confluence space:** `THPADEV`

When I need to create issues, track work, file bugs, or plan features — use the `atlassian-cli` skill rather than doing it ad-hoc.

**Every Jira issue must include:**
- A **description** — what it is, why it matters, acceptance criteria
- A **PRD reference** — link or mention of the relevant Confluence page in the `THPADEV` space (use "No PRD yet — see THPADEV space" if none exists)

---

## Where We Stopped (2026-06-23)

THPA-1 through THPA-8 audit complete. Jira updated.

**Jira status after audit:**
- THPA-1 Hexagonal Architecture — Done
- THPA-2 Simulator Wearable Data Source — Done
- THPA-3 TCP Fake BLE Data Source — Done
- THPA-4 Android Emulator Setup — Done
- THPA-5 Push Notification and Vibration and Sound Alerts — Done (delivered by THPA-22)
- THPA-6 Real BLE Adapter Implementation — To Do (stub only)
- THPA-7 Bluetooth Integration — To Do (stub only)
- THPA-8 IMU Data Reception / Breathing Rhythm — To Do (stub only)

**What's Next**
- THPA-43: Emergency contact alert on dismiss timeout
- Real adapter implementations (BLE GATT, Polar BLE SDK, Garmin Mobile SDK)
- Detection algorithm tuning (currently HR-only; HRV and stress score are captured but unused)
