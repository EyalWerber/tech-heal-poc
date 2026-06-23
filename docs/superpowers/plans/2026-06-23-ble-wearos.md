# BLE + Wear OS Companion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement two real wearable data paths — a generic BLE GATT adapter (any HR monitor, with device picker) and a Pixel Watch companion app that streams HR, HRV, and skin temperature via the Wearable Data Layer.

**Architecture:** Both paths implement the existing `WearableDataSource` port unchanged. BLE uses `callbackFlow` to bridge GATT callbacks into a Flow, waiting for user device selection via a `Channel<String>`. Wear OS uses a `PassiveListenerService` on the watch that sends `MessageClient` messages to a `WearableListenerService` on the phone, bridged via a `SharedFlow` companion object.

**Tech Stack:** Android BLE GATT API, `callbackFlow`, `play-services-wearable:18.2.0`, `health-services-client:1.1.0-alpha02`, Jetpack Compose `ModalBottomSheet`

## Global Constraints

- Phone: minSdk = 26, compileSdk = 36, namespace = `com.ptsdalert`
- Wear: minSdk = 30, compileSdk = 35, namespace = `com.ptsdalert.wear`
- No DI framework — no Hilt, Koin, or Dagger
- All logging via `AppLogger.i/d/w/e(TAG, message)` — never `Log.x()` directly
- Retry pattern: `while(true) { try { ... } catch (e: CancellationException) { throw e } catch (e: Exception) { delay(1_000L) } }` — same as `TcpWearableDataSource`
- Test runner: `./gradlew :app:test` — JUnit 4, no Robolectric
- Commit after every task

---

## File Map

### New Files
| File | Purpose |
|------|---------|
| `wear/build.gradle.kts` | Wear OS module build config |
| `wear/src/main/AndroidManifest.xml` | Wear OS manifest — BODY_SENSORS, HealthDataService |
| `wear/src/main/java/com/ptsdalert/wear/WearApplication.kt` | Registers passive health listener on startup |
| `wear/src/main/java/com/ptsdalert/wear/HealthDataService.kt` | PassiveListenerService — reads sensors, sends MessageClient |
| `app/src/main/java/com/ptsdalert/infrastructure/bluetooth/BleDevice.kt` | Data class: name + address |
| `app/src/main/java/com/ptsdalert/infrastructure/bluetooth/BleScannable.kt` | Interface: `scanDevices()` + `connectToDevice()` |
| `app/src/main/java/com/ptsdalert/infrastructure/bluetooth/BleHrParser.kt` | Internal pure functions: `parseHrBytes`, `deriveHrv`, `deriveStress` |
| `app/src/main/java/com/ptsdalert/infrastructure/wearos/WearDataListenerService.kt` | WearableListenerService — receives watch messages → SharedFlow |
| `app/src/main/java/com/ptsdalert/infrastructure/wearos/WearOsWearableDataSource.kt` | WearableDataSource impl — collects SharedFlow |
| `app/src/test/java/com/ptsdalert/infrastructure/bluetooth/BleHrParserTest.kt` | Tests for parseHrBytes, deriveHrv, deriveStress |
| `app/src/test/java/com/ptsdalert/infrastructure/wearos/WearOsWearableDataSourceTest.kt` | Tests for SharedFlow forwarding |

### Modified Files
| File | Change |
|------|--------|
| `gradle/libs.versions.toml` | Add wearable + health services versions/libraries/plugin |
| `settings.gradle.kts` | Add `include(":wear")` |
| `app/build.gradle.kts` | Add `play-services-wearable` dependency |
| `app/src/main/AndroidManifest.xml` | BLE permissions + WearDataListenerService declaration |
| `app/src/main/java/com/ptsdalert/DeviceType.kt` | Add `WEAR_OS` |
| `app/src/main/java/com/ptsdalert/DeviceProvider.kt` | Add `init(context)`, `BLUETOOTH` and `WEAR_OS` cases |
| `app/src/main/java/com/ptsdalert/MainActivity.kt` | Call `DeviceProvider.init(this)`, request BLE permissions |
| `app/src/main/java/com/ptsdalert/infrastructure/bluetooth/BluetoothWearableDataSource.kt` | Full implementation (replaces stub) |
| `app/src/main/java/com/ptsdalert/presentation/MonitoringUiState.kt` | Add `bleDevices`, `bleScanning` |
| `app/src/main/java/com/ptsdalert/presentation/MonitoringViewModel.kt` | BLE scan launch + `onDeviceSelected()` |
| `app/src/main/java/com/ptsdalert/presentation/MonitoringScreen.kt` | Device picker bottom sheet |

---

## Task 1: Gradle Scaffolding, DeviceType, Permissions

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `settings.gradle.kts`
- Create: `wear/build.gradle.kts`
- Create: `wear/src/main/AndroidManifest.xml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/ptsdalert/DeviceType.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `:wear` module compiles; `DeviceType.WEAR_OS` exists; BLE permissions declared

- [ ] **Step 1: Add version catalog entries**

In `gradle/libs.versions.toml`, add to `[versions]`:
```toml
playServicesWearable = "18.2.0"
healthServicesClient = "1.1.0-alpha02"
```

Add to `[libraries]`:
```toml
play-services-wearable = { group = "com.google.android.gms", name = "play-services-wearable", version.ref = "playServicesWearable" }
health-services-client = { group = "androidx.health", name = "health-services-client", version.ref = "healthServicesClient" }
```

Add to `[plugins]`:
```toml
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

- [ ] **Step 2: Add wear module to settings**

In `settings.gradle.kts`, change the last line from:
```kotlin
include(":app")
```
to:
```kotlin
include(":app")
include(":wear")
```

- [ ] **Step 3: Create wear/build.gradle.kts**

Create `wear/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.ptsdalert.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ptsdalert.wear"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.play.services.wearable)
    implementation(libs.health.services.client)
}
```

- [ ] **Step 4: Create wear/src/main/AndroidManifest.xml**

Create directory `wear/src/main/` then create `wear/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature android:name="android.hardware.type.watch" />

    <uses-permission android:name="android.permission.BODY_SENSORS" />
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".WearApplication"
        android:label="PTSD Alert Watch"
        android:theme="@android:style/Theme.DeviceDefault">

        <service
            android:name=".HealthDataService"
            android:exported="true"
            android:permission="android.permission.BIND_JOB_SERVICE">
            <intent-filter>
                <action android:name="com.google.android.gms.wearable.DATA_CHANGED" />
            </intent-filter>
        </service>

    </application>

</manifest>
```

- [ ] **Step 5: Add wearable dependency to app**

In `app/build.gradle.kts`, add to `dependencies {}`:
```kotlin
implementation(libs.play.services.wearable)
```

- [ ] **Step 6: Add WEAR_OS to DeviceType**

Replace the entire content of `app/src/main/java/com/ptsdalert/DeviceType.kt`:
```kotlin
package com.ptsdalert

enum class DeviceType {
    SIMULATOR,
    BLUETOOTH,
    TCP,
    USB,
    GARMIN,
    POLAR,
    WEAR_OS
}
```

- [ ] **Step 7: Add BLE permissions to app manifest**

In `app/src/main/AndroidManifest.xml`, add after the existing `<uses-permission android:name="android.permission.INTERNET" />` line:
```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"
    android:maxSdkVersion="30" />
```

- [ ] **Step 8: Verify build**

```bash
./gradlew :app:assembleDebug :wear:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Fix any sync errors before continuing.

- [ ] **Step 9: Commit**

```bash
git add gradle/libs.versions.toml settings.gradle.kts wear/ app/build.gradle.kts app/src/main/java/com/ptsdalert/DeviceType.kt app/src/main/AndroidManifest.xml
git commit -m "feat(THPA-6): scaffold wear module, add WEAR_OS device type, BLE permissions"
```

---

## Task 2: BleHrParser — Pure Functions (TDD)

**Files:**
- Create: `app/src/main/java/com/ptsdalert/infrastructure/bluetooth/BleHrParser.kt`
- Create: `app/src/test/java/com/ptsdalert/infrastructure/bluetooth/BleHrParserTest.kt`

**Interfaces:**
- Produces:
  - `internal fun parseHrBytes(bytes: ByteArray): Int?` — parses BLE HR characteristic bytes
  - `internal fun deriveHrv(hr: Int): Double` — derives HRV from HR (same formula as fake_ble_server.py)
  - `internal fun deriveStress(hr: Int): Int` — derives stress from HR (same formula)

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/ptsdalert/infrastructure/bluetooth/BleHrParserTest.kt`:
```kotlin
package com.ptsdalert.infrastructure.bluetooth

import org.junit.Assert.*
import org.junit.Test

class BleHrParserTest {

    // parseHrBytes: Flags byte bit 0 = 0 → UINT8, bit 0 = 1 → UINT16 (little-endian)

    @Test fun `parseHrBytes UINT8 returns correct HR`() {
        // flags=0x00 (UINT8), value=75
        val bytes = byteArrayOf(0x00, 75)
        assertEquals(75, parseHrBytes(bytes))
    }

    @Test fun `parseHrBytes UINT16 returns correct HR`() {
        // flags=0x01 (UINT16), value=180 = 0xB4 0x00 little-endian
        val bytes = byteArrayOf(0x01, 0xB4.toByte(), 0x00)
        assertEquals(180, parseHrBytes(bytes))
    }

    @Test fun `parseHrBytes UINT8 high value 200`() {
        val bytes = byteArrayOf(0x00, 200.toByte())
        assertEquals(200, parseHrBytes(bytes))
    }

    @Test fun `parseHrBytes empty returns null`() {
        assertNull(parseHrBytes(byteArrayOf()))
    }

    @Test fun `parseHrBytes UINT8 too short returns null`() {
        assertNull(parseHrBytes(byteArrayOf(0x00)))
    }

    @Test fun `parseHrBytes UINT16 too short returns null`() {
        assertNull(parseHrBytes(byteArrayOf(0x01, 0xB4.toByte())))
    }

    // deriveHrv: same formula as fake_ble_server.py

    @Test fun `deriveHrv normal range HR=72`() {
        // normal: 20.0 + (100 - 72) * 0.5 = 34.0
        assertEquals(34.0, deriveHrv(72), 0.01)
    }

    @Test fun `deriveHrv hyperarousal HR=120`() {
        // hyper: max(5.0, 80.0 - (120 - 100) * 0.8) = max(5.0, 64.0) = 64.0
        assertEquals(64.0, deriveHrv(120), 0.01)
    }

    @Test fun `deriveHrv hypoarousal HR=40`() {
        // hypo: 80.0 + (50 - 40) * 0.5 = 85.0
        assertEquals(85.0, deriveHrv(40), 0.01)
    }

    // deriveStress: same formula as fake_ble_server.py

    @Test fun `deriveStress normal range HR=72`() {
        // normal: (72 - 50) * 0.4 = 8
        assertEquals(8, deriveStress(72))
    }

    @Test fun `deriveStress hyperarousal HR=120`() {
        // hyper: min(100, (120-100)*1.5 + 40) = min(100, 70) = 70
        assertEquals(70, deriveStress(120))
    }

    @Test fun `deriveStress hypoarousal HR=40`() {
        // hypo: max(0, 30 - (50-40)) = max(0, 20) = 20
        assertEquals(20, deriveStress(40))
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./gradlew :app:test --tests "com.ptsdalert.infrastructure.bluetooth.BleHrParserTest"
```

Expected: FAILED — `parseHrBytes`, `deriveHrv`, `deriveStress` not found.

- [ ] **Step 3: Create BleHrParser.kt**

Create `app/src/main/java/com/ptsdalert/infrastructure/bluetooth/BleHrParser.kt`:
```kotlin
package com.ptsdalert.infrastructure.bluetooth

internal fun parseHrBytes(bytes: ByteArray): Int? {
    if (bytes.isEmpty()) return null
    val flags = bytes[0].toInt() and 0xFF
    val isUint16 = flags and 0x01 != 0
    return if (isUint16) {
        if (bytes.size < 3) null
        else ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
    } else {
        if (bytes.size < 2) null
        else bytes[1].toInt() and 0xFF
    }
}

internal fun deriveHrv(hr: Int): Double = when {
    hr > 100 -> maxOf(5.0, 80.0 - (hr - 100) * 0.8)
    hr < 50  -> 80.0 + (50 - hr) * 0.5
    else     -> 20.0 + (100 - hr) * 0.5
}

internal fun deriveStress(hr: Int): Int = when {
    hr > 100 -> minOf(100, ((hr - 100) * 1.5 + 40).toInt())
    hr < 50  -> maxOf(0, 30 - (50 - hr))
    else     -> ((hr - 50) * 0.4).toInt()
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./gradlew :app:test --tests "com.ptsdalert.infrastructure.bluetooth.BleHrParserTest"
```

Expected: `BUILD SUCCESSFUL`, 10 tests passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ptsdalert/infrastructure/bluetooth/BleHrParser.kt app/src/test/java/com/ptsdalert/infrastructure/bluetooth/BleHrParserTest.kt
git commit -m "feat(THPA-6): BleHrParser — HR byte parsing + HRV/stress derivation"
```

---

## Task 3: BleDevice, BleScannable, BluetoothWearableDataSource

**Files:**
- Create: `app/src/main/java/com/ptsdalert/infrastructure/bluetooth/BleDevice.kt`
- Create: `app/src/main/java/com/ptsdalert/infrastructure/bluetooth/BleScannable.kt`
- Modify: `app/src/main/java/com/ptsdalert/infrastructure/bluetooth/BluetoothWearableDataSource.kt`
- Modify: `app/src/main/java/com/ptsdalert/DeviceProvider.kt`
- Modify: `app/src/main/java/com/ptsdalert/MainActivity.kt`

**Interfaces:**
- Consumes: `parseHrBytes`, `deriveHrv`, `deriveStress` from Task 2
- Produces:
  - `data class BleDevice(val name: String, val address: String)`
  - `interface BleScannable { fun scanDevices(): Flow<List<BleDevice>>; fun connectToDevice(address: String) }`
  - `BluetoothWearableDataSource : WearableDataSource, BleScannable`
  - `DeviceProvider.init(context: Context)` — must be called from `MainActivity.onCreate()` before `DeviceProvider.create()`

- [ ] **Step 1: Create BleDevice.kt**

Create `app/src/main/java/com/ptsdalert/infrastructure/bluetooth/BleDevice.kt`:
```kotlin
package com.ptsdalert.infrastructure.bluetooth

data class BleDevice(val name: String, val address: String)
```

- [ ] **Step 2: Create BleScannable.kt**

Create `app/src/main/java/com/ptsdalert/infrastructure/bluetooth/BleScannable.kt`:
```kotlin
package com.ptsdalert.infrastructure.bluetooth

import kotlinx.coroutines.flow.Flow

interface BleScannable {
    fun scanDevices(): Flow<List<BleDevice>>
    fun connectToDevice(address: String)
}
```

- [ ] **Step 3: Implement BluetoothWearableDataSource**

Replace the entire content of `app/src/main/java/com/ptsdalert/infrastructure/bluetooth/BluetoothWearableDataSource.kt`:
```kotlin
package com.ptsdalert.infrastructure.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.ptsdalert.domain.model.PhysiologicalSample
import com.ptsdalert.domain.ports.WearableDataSource
import com.ptsdalert.infrastructure.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.UUID

private const val TAG = "BluetoothWearableDataSource"

private val HR_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
private val HR_CHAR_UUID    = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
private val CCCD_UUID       = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

class BluetoothWearableDataSource(private val context: Context) : WearableDataSource, BleScannable {

    override val deviceLabel: String = "Bluetooth HR Monitor"

    private val bluetoothAdapter by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private val deviceAddressChannel = Channel<String>(Channel.CONFLATED)

    override fun scanDevices(): Flow<List<BleDevice>> = callbackFlow {
        val found = mutableListOf<BleDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: return
                val addr = result.device.address
                if (found.none { it.address == addr }) {
                    found.add(BleDevice(name, addr))
                    trySend(found.toList())
                }
            }
            override fun onScanFailed(errorCode: Int) {
                AppLogger.e(TAG, "BLE scan failed: error $errorCode")
                close(Exception("BLE scan failed: $errorCode"))
            }
        }
        val scanner = bluetoothAdapter.bluetoothLeScanner
            ?: run { close(Exception("Bluetooth unavailable or disabled")); return@callbackFlow }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(HR_SERVICE_UUID)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        AppLogger.i(TAG, "Starting BLE scan")
        scanner.startScan(listOf(filter), settings, callback)
        trySend(emptyList())
        awaitClose {
            AppLogger.i(TAG, "Stopping BLE scan")
            scanner.stopScan(callback)
        }
    }.flowOn(Dispatchers.Main)

    override fun connectToDevice(address: String) {
        AppLogger.i(TAG, "Device selected: $address")
        deviceAddressChannel.trySend(address)
    }

    override fun streamSamples(): Flow<PhysiologicalSample> = flow {
        AppLogger.i(TAG, "Waiting for device selection...")
        val address = deviceAddressChannel.receive()
        AppLogger.i(TAG, "Connecting to $address")
        while (true) {
            try {
                emitAll(gattStream(address))
                AppLogger.w(TAG, "GATT disconnected — retrying in 1s")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "GATT error: ${e.message} — retrying in 1s")
            }
            delay(1_000L)
        }
    }.flowOn(Dispatchers.IO)

    private fun gattStream(address: String): Flow<PhysiologicalSample> = callbackFlow {
        val device = bluetoothAdapter.getRemoteDevice(address)
        var gatt: BluetoothGatt? = null

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        AppLogger.i(TAG, "GATT connected — discovering services")
                        g.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        AppLogger.w(TAG, "GATT disconnected from $address")
                        close()
                    }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    close(Exception("Service discovery failed: $status")); return
                }
                val char = g.getService(HR_SERVICE_UUID)?.getCharacteristic(HR_CHAR_UUID)
                    ?: run { close(Exception("HR characteristic not found")); return }
                g.setCharacteristicNotification(char, true)
                val descriptor = char.getDescriptor(CCCD_UUID)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(descriptor)
                }
                AppLogger.i(TAG, "HR notifications enabled on $address")
            }

            // API 33+ callback
            override fun onCharacteristicChanged(g: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray) {
                parseSample(value)?.let { trySend(it) }
            }

            // Pre-API 33 callback
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(g: BluetoothGatt, char: BluetoothGattCharacteristic) {
                parseSample(char.value)?.let { trySend(it) }
            }
        }

        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        awaitClose {
            AppLogger.i(TAG, "Closing GATT to $address")
            gatt?.disconnect()
            gatt?.close()
        }
    }

    private fun parseSample(bytes: ByteArray): PhysiologicalSample? {
        val hr = parseHrBytes(bytes) ?: return null
        return PhysiologicalSample(
            timestamp       = System.currentTimeMillis(),
            heartRate       = hr,
            hrv             = deriveHrv(hr),
            skinTemperature = null,
            stressScore     = deriveStress(hr)
        )
    }
}
```

- [ ] **Step 4: Add DeviceProvider.init and BLUETOOTH case**

Replace the entire content of `app/src/main/java/com/ptsdalert/DeviceProvider.kt`:
```kotlin
package com.ptsdalert

import android.content.Context
import com.ptsdalert.domain.ports.WearableDataSource
import com.ptsdalert.infrastructure.bluetooth.BluetoothWearableDataSource
import com.ptsdalert.infrastructure.garmin.GarminWearableDataSource
import com.ptsdalert.infrastructure.polar.PolarWearableDataSource
import com.ptsdalert.infrastructure.simulator.SimulatorWearableDataSource
import com.ptsdalert.infrastructure.tcp.TcpWearableDataSource
import com.ptsdalert.infrastructure.usb.UsbWearableDataSource
import com.ptsdalert.infrastructure.wearos.WearOsWearableDataSource

object DeviceProvider {

    // ← CHANGE THIS LINE to switch wearable adapters.
    val activeDevice: DeviceType = DeviceType.BLUETOOTH

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun create(): WearableDataSource = when (activeDevice) {
        DeviceType.SIMULATOR  -> SimulatorWearableDataSource()
        DeviceType.BLUETOOTH  -> BluetoothWearableDataSource(appContext)
        DeviceType.TCP        -> TcpWearableDataSource()
        DeviceType.USB        -> UsbWearableDataSource()
        DeviceType.GARMIN     -> GarminWearableDataSource()
        DeviceType.POLAR      -> PolarWearableDataSource()
        DeviceType.WEAR_OS    -> WearOsWearableDataSource()
    }
}
```

- [ ] **Step 5: Call DeviceProvider.init in MainActivity**

In `app/src/main/java/com/ptsdalert/MainActivity.kt`, in `onCreate()`, add `DeviceProvider.init(this)` as the first line after `super.onCreate(savedInstanceState)`:
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    DeviceProvider.init(this)   // ← add this line
    AppLogger.init(SqliteLogRepository(applicationContext))
    // ... rest unchanged
```

Also add the BLE permission request call. After the existing permission-request calls in `onCreate`, add:
```kotlin
requestBlePermissionsIfNeeded()
```

Then add this function to `MainActivity`:
```kotlin
private fun requestBlePermissionsIfNeeded() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val missing = listOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT
        ).filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1001)
        }
    }
}
```

Add import at the top: `import android.os.Build`

- [ ] **Step 6: Build check**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. The `WearOsWearableDataSource` import in `DeviceProvider` will fail until Task 5 — temporarily comment out the `WEAR_OS` case and import if needed, or stub `WearOsWearableDataSource` now:

Create a minimal stub `app/src/main/java/com/ptsdalert/infrastructure/wearos/WearOsWearableDataSource.kt` if not yet implemented:
```kotlin
package com.ptsdalert.infrastructure.wearos

import com.ptsdalert.domain.model.PhysiologicalSample
import com.ptsdalert.domain.ports.WearableDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class WearOsWearableDataSource : WearableDataSource {
    override val deviceLabel = "Pixel Watch"
    override fun streamSamples(): Flow<PhysiologicalSample> = emptyFlow()
}
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/ptsdalert/infrastructure/bluetooth/ app/src/main/java/com/ptsdalert/DeviceProvider.kt app/src/main/java/com/ptsdalert/MainActivity.kt
git commit -m "feat(THPA-6): BleDevice, BleScannable, BluetoothWearableDataSource — scan + GATT streaming"
```

---

## Task 4: BLE Device Picker UI

**Files:**
- Modify: `app/src/main/java/com/ptsdalert/presentation/MonitoringUiState.kt`
- Modify: `app/src/main/java/com/ptsdalert/presentation/MonitoringViewModel.kt`
- Modify: `app/src/main/java/com/ptsdalert/presentation/MonitoringScreen.kt`

**Interfaces:**
- Consumes: `BleScannable` interface from Task 3 (`scanDevices()`, `connectToDevice()`)
- Produces: Bottom sheet picker shown when `uiState.bleDevices` is non-empty and no stream active

- [ ] **Step 1: Add BLE state to MonitoringUiState**

Replace content of `app/src/main/java/com/ptsdalert/presentation/MonitoringUiState.kt`:
```kotlin
package com.ptsdalert.presentation

import com.ptsdalert.domain.model.ArousalState
import com.ptsdalert.domain.model.LogEntry
import com.ptsdalert.infrastructure.bluetooth.BleDevice

data class MonitoringUiState(
    val heartRate: Int? = null,
    val arousalState: ArousalState = ArousalState.NORMAL,
    val deviceLabel: String = "",
    val isSimulator: Boolean = false,
    val recentLogs: List<LogEntry> = emptyList(),
    val bleDevices: List<BleDevice> = emptyList(),
    val bleScanning: Boolean = false
)
```

- [ ] **Step 2: Add BLE scan handling to MonitoringViewModel**

In `app/src/main/java/com/ptsdalert/presentation/MonitoringViewModel.kt`:

Add import at the top:
```kotlin
import com.ptsdalert.infrastructure.bluetooth.BleScannable
```

Add after the `simulatorControls` property:
```kotlin
private val bleScanner: BleScannable? = wearableDataSource as? BleScannable
```

Add at the end of `init {}` block (after existing `viewModelScope.launch` calls):
```kotlin
bleScanner?.let { scanner ->
    _uiState.update { it.copy(bleScanning = true) }
    viewModelScope.launch {
        scanner.scanDevices().collect { devices ->
            _uiState.update { it.copy(bleDevices = devices, bleScanning = true) }
        }
    }
}
```

Add new function after `setSimulatorMode`:
```kotlin
fun onDeviceSelected(address: String) {
    bleScanner?.connectToDevice(address)
    _uiState.update { it.copy(bleDevices = emptyList(), bleScanning = false) }
}
```

- [ ] **Step 3: Add device picker to MonitoringScreen**

In `app/src/main/java/com/ptsdalert/presentation/MonitoringScreen.kt`:

Add these imports:
```kotlin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextOverflow
```

Add `@OptIn(ExperimentalMaterial3Api::class)` annotation before `fun MonitoringScreen`.

Inside `MonitoringScreen`, after the `val uiState by ...` line, add:
```kotlin
val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
```

At the very end of the `Column(modifier = Modifier.fillMaxSize())` block (after the log panel), add:
```kotlin
if (uiState.bleDevices.isNotEmpty()) {
    ModalBottomSheet(
        onDismissRequest = {},
        sheetState = sheetState
    ) {
        BleDevicePicker(
            devices = uiState.bleDevices,
            scanning = uiState.bleScanning,
            onDeviceSelected = { viewModel.onDeviceSelected(it) }
        )
    }
}
```

Add this composable at the bottom of the file (after the `LogLine` composable):
```kotlin
@Composable
private fun BleDevicePicker(
    devices: List<com.ptsdalert.infrastructure.bluetooth.BleDevice>,
    scanning: Boolean,
    onDeviceSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = if (scanning) "Select your HR monitor (scanning…)" else "Select your HR monitor",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        if (devices.isEmpty()) {
            Text(
                text = "No devices found yet. Make sure your device is nearby and on.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            devices.forEach { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDeviceSelected(device.address) }
                        .padding(vertical = 12.dp)
                ) {
                    Column {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = device.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
```

- [ ] **Step 4: Build check**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ptsdalert/presentation/
git commit -m "feat(THPA-6): BLE device picker UI — scan list bottom sheet"
```

---

## Task 5: Wear OS HealthDataService

**Files:**
- Create: `wear/src/main/java/com/ptsdalert/wear/WearApplication.kt`
- Create: `wear/src/main/java/com/ptsdalert/wear/HealthDataService.kt`

**Interfaces:**
- Produces: Wear OS app that continuously streams `PhysiologicalSample` JSON to the phone via `MessageClient` on path `/physiological-sample`

- [ ] **Step 1: Create WearApplication.kt**

Create `wear/src/main/java/com/ptsdalert/wear/WearApplication.kt`:
```kotlin
package com.ptsdalert.wear

import android.app.Application
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveListenerConfig
import androidx.health.services.client.data.DataType
import com.google.android.gms.tasks.Tasks

class WearApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        registerPassiveListener()
    }

    private fun registerPassiveListener() {
        val client = HealthServices.getClient(this).passiveMonitoringClient
        val config = PassiveListenerConfig.builder()
            .setDataTypes(setOf(DataType.HEART_RATE_BPM, DataType.SKIN_TEMPERATURE))
            .build()
        client.setPassiveListenerServiceAsync(HealthDataService::class.java, config)
            .addOnFailureListener { e ->
                android.util.Log.e("WearApplication", "Failed to register passive listener: ${e.message}")
            }
    }
}
```

- [ ] **Step 2: Create HealthDataService.kt**

Create `wear/src/main/java/com/ptsdalert/wear/HealthDataService.kt`:
```kotlin
package com.ptsdalert.wear

import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.SampleDataPoint
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject

private const val MESSAGE_PATH = "/physiological-sample"

class HealthDataService : PassiveListenerService() {

    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val hrPoints = dataPoints.getData(DataType.HEART_RATE_BPM)
        val tempPoints = dataPoints.getData(DataType.SKIN_TEMPERATURE)

        val hr = hrPoints.lastOrNull()?.value?.toInt() ?: return
        val skinTemp = tempPoints.lastOrNull()?.value

        val json = buildJson(hr, skinTemp)
        sendToPhone(json)
    }

    private fun buildJson(hr: Int, skinTemp: Double?): String {
        val hrv = deriveHrv(hr)
        val stress = deriveStress(hr)
        return JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("heart_rate", hr)
            put("hrv", hrv)
            if (skinTemp != null) put("skin_temperature", skinTemp)
            put("stress_score", stress)
        }.toString()
    }

    private fun sendToPhone(json: String) {
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                val payload = json.toByteArray(Charsets.UTF_8)
                nodes.forEach { node ->
                    Wearable.getMessageClient(this)
                        .sendMessage(node.id, MESSAGE_PATH, payload)
                }
            }
    }

    private fun deriveHrv(hr: Int): Double = when {
        hr > 100 -> maxOf(5.0, 80.0 - (hr - 100) * 0.8)
        hr < 50  -> 80.0 + (50 - hr) * 0.5
        else     -> 20.0 + (100 - hr) * 0.5
    }

    private fun deriveStress(hr: Int): Int = when {
        hr > 100 -> minOf(100, ((hr - 100) * 1.5 + 40).toInt())
        hr < 50  -> maxOf(0, 30 - (50 - hr))
        else     -> ((hr - 50) * 0.4).toInt()
    }
}
```

- [ ] **Step 3: Build check**

```bash
./gradlew :wear:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add wear/src/
git commit -m "feat(THPA-6): Wear OS HealthDataService — passive HR + skin temp → MessageClient"
```

---

## Task 6: Phone-side Wear OS Adapter + DeviceProvider Wiring

**Files:**
- Modify: `app/src/main/java/com/ptsdalert/infrastructure/wearos/WearOsWearableDataSource.kt` (replace stub from Task 3)
- Create: `app/src/main/java/com/ptsdalert/infrastructure/wearos/WearDataListenerService.kt`
- Create: `app/src/test/java/com/ptsdalert/infrastructure/wearos/WearOsWearableDataSourceTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `MESSAGE_PATH = "/physiological-sample"` from Task 5 convention
- Produces: `WearDataListenerService.sampleFlow: SharedFlow<PhysiologicalSample>` readable by `WearOsWearableDataSource`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/ptsdalert/infrastructure/wearos/WearOsWearableDataSourceTest.kt`:
```kotlin
package com.ptsdalert.infrastructure.wearos

import com.ptsdalert.domain.model.PhysiologicalSample
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WearOsWearableDataSourceTest {

    @Test fun `streamSamples emits sample forwarded from WearDataListenerService`() = runTest {
        val source = WearOsWearableDataSource()
        val sample = PhysiologicalSample(
            timestamp = 1000L,
            heartRate = 80,
            hrv = 34.0,
            skinTemperature = 36.6,
            stressScore = 12
        )
        val results = mutableListOf<PhysiologicalSample>()
        val job = launch { source.streamSamples().take(1).collect { results.add(it) } }

        WearDataListenerService.sampleFlow.emit(sample)
        job.join()

        assertEquals(1, results.size)
        assertEquals(80, results[0].heartRate)
        assertEquals(36.6, results[0].skinTemperature)
    }

    @Test fun `deviceLabel is Pixel Watch`() {
        assertEquals("Pixel Watch", WearOsWearableDataSource().deviceLabel)
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
./gradlew :app:test --tests "com.ptsdalert.infrastructure.wearos.WearOsWearableDataSourceTest"
```

Expected: FAILED — `WearDataListenerService` not found.

- [ ] **Step 3: Create WearDataListenerService.kt**

Create `app/src/main/java/com/ptsdalert/infrastructure/wearos/WearDataListenerService.kt`:
```kotlin
package com.ptsdalert.infrastructure.wearos

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.ptsdalert.domain.model.PhysiologicalSample
import com.ptsdalert.infrastructure.logging.AppLogger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.json.JSONObject

private const val TAG = "WearDataListenerService"

class WearDataListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != MESSAGE_PATH) return
        val json = String(event.data, Charsets.UTF_8)
        val sample = parseSample(json) ?: return
        sampleFlow.tryEmit(sample)
        AppLogger.d(TAG, "Sample received from watch: HR=${sample.heartRate}")
    }

    companion object {
        const val MESSAGE_PATH = "/physiological-sample"

        val sampleFlow = MutableSharedFlow<PhysiologicalSample>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

        fun parseSample(json: String): PhysiologicalSample? = try {
            val obj = JSONObject(json)
            PhysiologicalSample(
                timestamp       = obj.optLong("timestamp", System.currentTimeMillis()),
                heartRate       = obj.optInt("heart_rate").takeIf { obj.has("heart_rate") },
                hrv             = obj.optDouble("hrv").takeIf { obj.has("hrv") },
                skinTemperature = obj.optDouble("skin_temperature").takeIf { obj.has("skin_temperature") },
                stressScore     = obj.optInt("stress_score").takeIf { obj.has("stress_score") }
            )
        } catch (e: Exception) {
            null
        }
    }
}
```

- [ ] **Step 4: Replace WearOsWearableDataSource stub**

Replace the entire content of `app/src/main/java/com/ptsdalert/infrastructure/wearos/WearOsWearableDataSource.kt`:
```kotlin
package com.ptsdalert.infrastructure.wearos

import com.ptsdalert.domain.model.PhysiologicalSample
import com.ptsdalert.domain.ports.WearableDataSource
import kotlinx.coroutines.flow.Flow

class WearOsWearableDataSource : WearableDataSource {

    override val deviceLabel: String = "Pixel Watch"

    override fun streamSamples(): Flow<PhysiologicalSample> =
        WearDataListenerService.sampleFlow
}
```

- [ ] **Step 5: Run tests to confirm they pass**

```bash
./gradlew :app:test --tests "com.ptsdalert.infrastructure.wearos.WearOsWearableDataSourceTest"
```

Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Step 6: Add WearDataListenerService to app manifest**

In `app/src/main/AndroidManifest.xml`, inside `<application>` (after the existing `<receiver>` block), add:
```xml
<service
    android:name=".infrastructure.wearos.WearDataListenerService"
    android:exported="true">
    <intent-filter>
        <action android:name="com.google.android.gms.wearable.MESSAGE_RECEIVED" />
        <data
            android:scheme="wear"
            android:host="*"
            android:pathPrefix="/physiological-sample" />
    </intent-filter>
</service>
```

- [ ] **Step 7: Final build check — all tests**

```bash
./gradlew :app:test :app:assembleDebug :wear:assembleDebug
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/ptsdalert/infrastructure/wearos/ app/src/test/java/com/ptsdalert/infrastructure/wearos/ app/src/main/AndroidManifest.xml
git commit -m "feat(THPA-6): WearDataListenerService + WearOsWearableDataSource — phone-side Wear OS adapter"
```

---

## Testing the Full Feature

### BLE path (generic HR monitor)
1. Set `activeDevice = DeviceType.BLUETOOTH` in `DeviceProvider.kt`
2. Build and install: `./gradlew :app:installDebug`
3. Grant BLE permissions when prompted
4. Bottom sheet appears scanning for HR devices
5. Power on a BLE heart rate monitor — it appears in the list
6. Tap the device — picker closes, HR readings appear on screen
7. Trigger HYPERAROUSAL: exercise until HR > 100 — alert fires

### Wear OS path (Pixel Watch)
1. Set `activeDevice = DeviceType.WEAR_OS` in `DeviceProvider.kt`
2. Install phone app: `./gradlew :app:installDebug`
3. Install wear app on watch: `./gradlew :wear:installDebug` (or sideload via `adb -s <watch_serial> install wear/build/outputs/apk/debug/wear-debug.apk`)
4. Grant BODY_SENSORS on watch when prompted
5. HR and skin temperature appear on phone screen within ~5 seconds
6. Confirm `skinTemperature` is non-null in logs: `adb logcat | grep WearData`
