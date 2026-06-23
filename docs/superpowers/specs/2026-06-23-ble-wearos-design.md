# BLE + Wear OS Companion — Design Spec
**Feature:** THPA-6 — Real BLE Adapter Implementation
**Date:** 2026-06-23
**Status:** Approved

---

## Overview

Two new wearable data paths, both implementing the existing `WearableDataSource` port:

1. **BLUETOOTH** — generic BLE GATT adapter for any standard Heart Rate monitor (0x180D service). Streams HR + HRV (derived from RR intervals). User picks device from a scan list.
2. **WEAR_OS** — Pixel Watch companion. A Wear OS module reads all available sensors via Health Services API and streams to the phone via Wearable Data Layer (`MessageClient`). Provides HR, HRV, skin temperature, and derived stress score.

No changes to domain layer, detection engine, or ViewModel logic beyond adding scan/picker state.

---

## Architecture

```
[Pixel Watch — wear module]
  HealthDataService (PassiveListenerService)
    reads: HEART_RATE_BPM, SKIN_TEMPERATURE via PassiveMonitoringClient
    derives: HRV and stress score from HR
    sends: JSON @ ~1Hz via MessageClient → path: /physiological-sample

[Phone — WearDataListenerService]
  receives message → parses → SharedFlow<PhysiologicalSample>

  WearOsWearableDataSource (implements WearableDataSource)
    collects SharedFlow → Flow<PhysiologicalSample>

[Any BLE HR Monitor]
  BluetoothWearableDataSource (implements WearableDataSource)
    scanDevices() → Flow<List<BleDevice>>
    connect(address) → GATT subscribe → Flow<PhysiologicalSample>
    skinTemperature / stressScore = null for generic BLE devices
```

---

## Components

### `wear/` module (new)

| File | Purpose |
|------|---------|
| `wear/build.gradle.kts` | Wear OS target, Health Services + Wearable dependencies |
| `wear/AndroidManifest.xml` | BODY_SENSORS permission, HealthDataService declaration |
| `wear/…/HealthDataService.kt` | PassiveListenerService — reads sensors, sends MessageClient messages |

`HealthDataService` registers for `HEART_RATE_BPM` and `SKIN_TEMPERATURE` via `PassiveMonitoringClient.setPassiveListenerServiceAsync()` on first startup. Each Health Services callback packages available data into a JSON object and sends it to the connected phone node via `MessageClient`.

HRV: derived from HR using the same formula as `fake_ble_server.py` (RR intervals not exposed by the passive API).
Stress: derived from HR using the same formula as `fake_ble_server.py`.

### Phone — Wear OS adapter (new)

| File | Purpose |
|------|---------|
| `infrastructure/wearos/WearDataListenerService.kt` | WearableListenerService — receives messages, emits to SharedFlow |
| `infrastructure/wearos/WearOsWearableDataSource.kt` | WearableDataSource impl — collects SharedFlow |

`WearDataListenerService` holds a `MutableSharedFlow<PhysiologicalSample>` in its companion object. On message received at `/physiological-sample`, parses JSON → `PhysiologicalSample` → emits. `WearOsWearableDataSource.streamSamples()` returns that flow. Suspends silently when watch is not connected.

### Phone — BLE GATT adapter (replaces stub)

| File | Purpose |
|------|---------|
| `infrastructure/bluetooth/BleDevice.kt` | `data class BleDevice(val name: String, val address: String)` |
| `infrastructure/bluetooth/BluetoothWearableDataSource.kt` | Scan, connect, stream |

**Scan phase:** `scanDevices(): Flow<List<BleDevice>>` uses `BluetoothLeScanner` filtered to service UUID `0x180D`. Updates the list incrementally as devices are found. Stops when `connect()` is called.

**Connect + stream:** `connect(address: String)` connects via `BluetoothGatt`, discovers services, enables notifications on characteristic `0x2A37`. `streamSamples()` emits each `onCharacteristicChanged` callback as a `PhysiologicalSample`. On disconnect, retries with 1s delay (same pattern as `TcpWearableDataSource`).

**HRV:** derived from HR (RR intervals available in characteristic but parsing adds complexity — deferred to a follow-up).
**skinTemperature / stressScore:** `null`.

### Presentation changes

`MonitoringViewModel` gains:
- `bleDevices: List<BleDevice>` in UI state (populated when `BLUETOOTH` mode + scanning)
- `onDeviceSelected(address: String)` — stops scan, calls `adapter.connect()`

`MonitoringScreen` gains a bottom sheet device picker shown when `bleDevices` is non-empty and no device is connected yet.

### DeviceType + DeviceProvider

```kotlin
// DeviceType.kt
WEAR_OS  // ← new entry

// DeviceProvider.kt
DeviceType.WEAR_OS -> WearOsWearableDataSource()
```

---

## Permissions

### `app/AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<service android:name=".infrastructure.wearos.WearDataListenerService"
    android:exported="true">
    <intent-filter>
        <action android:name="com.google.android.gms.wearable.MESSAGE_RECEIVED" />
        <data android:scheme="wear" android:host="*"
              android:pathPrefix="/physiological-sample" />
    </intent-filter>
</service>
```

`BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` runtime-requested in `MainActivity` when `activeDevice == BLUETOOTH`.

### `wear/AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.BODY_SENSORS" />
<service android:name=".HealthDataService" android:exported="true">
    <intent-filter>
        <action android:name="com.google.android.gms.wearable.DATA_CHANGED" />
    </intent-filter>
</service>
```

`BODY_SENSORS` prompted by Wear OS when `PassiveMonitoringClient` registers for health data.

---

## Data Flow — Wear OS Path

```
PassiveMonitoringClient callback
  → build JSON: {timestamp, heart_rate, hrv, skin_temperature, stress_score}
  → MessageClient.sendMessage(nodeId, "/physiological-sample", json.toByteArray())
  → WearDataListenerService.onMessageReceived()
  → JSONObject.parse() → PhysiologicalSample
  → SharedFlow.emit()
  → WearOsWearableDataSource.streamSamples() collects
  → MonitoringViewModel processes
```

## Data Flow — BLE Path

```
BluetoothLeScanner.startScan(HR service UUID filter)
  → ScanCallback → bleDevices list updates → picker shown
User selects device
  → BluetoothDevice.connectGatt()
  → onServicesDiscovered → find 0x2A37 characteristic
  → setCharacteristicNotification(true)
  → onCharacteristicChanged → parse HR bytes → PhysiologicalSample
  → flow emit → MonitoringViewModel processes
```

---

## Error Handling

- **Watch not connected:** `WearOsWearableDataSource` flow suspends silently — no crash, no alerts.
- **BLE scan: no devices found:** picker shows empty state with "Scanning…" message.
- **GATT disconnect:** retry loop with 1s delay, same as TCP adapter.
- **Missing permissions:** `MainActivity` requests at runtime; if denied, device picker / scan never starts.

---

## Out of Scope

- RR interval parsing from BLE HR characteristic (deferred — HRV derived for now)
- Stress score from Wear OS (not in public Health Services API — derived from HR)
- Multiple simultaneous connected devices
- Polar, Garmin, USB adapters (separate tickets)
