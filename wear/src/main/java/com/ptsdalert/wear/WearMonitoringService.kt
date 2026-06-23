package com.ptsdalert.wear

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.os.PowerManager
import android.util.Log
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private const val TAG = "WearMonitoringService"

private val HR_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
private val HR_CHAR_UUID    = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
private val CCCD_UUID       = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

@SuppressLint("MissingPermission")
class WearMonitoringService : Service() {

    private val executor = Executors.newSingleThreadExecutor()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var fakeHrJob: ScheduledFuture<*>? = null
    @Volatile private var currentFakeHr: Int? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var bluetoothManager: BluetoothManager
    private var gattServer: BluetoothGattServer? = null
    private val connectedDevices = mutableSetOf<BluetoothDevice>()

    private val exerciseCallback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            if (currentFakeHr != null) return
            val hr = update.latestMetrics.getData(DataType.HEART_RATE_BPM)
                .lastOrNull()?.value?.toInt() ?: return
            Log.d(TAG, "HR=$hr — notifying BLE clients")
            notifyHr(hr)
        }
        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {}
        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {
            Log.d(TAG, "Availability: $dataType=$availability")
        }
        override fun onRegistered() {
            val config = ExerciseConfig.builder(ExerciseType.WORKOUT)
                .setDataTypes(setOf(DataType.HEART_RATE_BPM))
                .setIsAutoPauseAndResumeEnabled(false)
                .build()
            HealthServices.getClient(applicationContext).exerciseClient
                .startExerciseAsync(config)
                .addListener({ Log.i(TAG, "Exercise started") }, executor)
        }
        override fun onRegistrationFailed(throwable: Throwable) {
            Log.e(TAG, "Exercise callback registration failed: ${throwable.message}")
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDevices.add(device)
                Log.i(TAG, "BLE client connected: ${device.address}")
            } else {
                connectedDevices.remove(device)
                Log.i(TAG, "BLE client disconnected: ${device.address}")
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i(TAG, "BLE advertising started")
        }
        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE advertising failed: $errorCode")
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "PTSD Monitoring", NotificationManager.IMPORTANCE_LOW)
        )
        startForeground(NOTIFICATION_ID, buildNotification())

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PTSDAlert:WearMonitoring")
            .also { it.acquire() }

        setupBleGattServer()
        startBleAdvertising()

        HealthServices.getClient(applicationContext).exerciseClient
            .setUpdateCallback(executor, exerciseCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fakeHr = intent?.getIntExtra(EXTRA_FAKE_HR, -1) ?: -1
        if (fakeHr > 0) toggleFakeHr(fakeHr)
        return START_STICKY
    }

    private fun toggleFakeHr(bpm: Int) {
        if (currentFakeHr == bpm) {
            currentFakeHr = null
            fakeHrJob?.cancel(false)
            fakeHrJob = null
            Log.i(TAG, "Fake HR off — resuming real HR")
        } else {
            currentFakeHr = bpm
            fakeHrJob?.cancel(false)
            fakeHrJob = scheduler.scheduleAtFixedRate({
                notifyHr(bpm)
            }, 0, 1, TimeUnit.SECONDS)
            Log.i(TAG, "Fake HR on: $bpm BPM")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        bluetoothManager.adapter.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        gattServer?.close()
        fakeHrJob?.cancel(false)
        scheduler.shutdown()
        val client = HealthServices.getClient(applicationContext).exerciseClient
        client.endExerciseAsync().addListener({}, executor)
        client.clearUpdateCallbackAsync(exerciseCallback).addListener({ executor.shutdown() }, executor)
        wakeLock?.release()
    }

    private fun setupBleGattServer() {
        bluetoothManager = getSystemService(BluetoothManager::class.java)
        gattServer = bluetoothManager.openGattServer(this, gattServerCallback)

        val hrChar = BluetoothGattCharacteristic(
            HR_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).apply {
            addDescriptor(BluetoothGattDescriptor(
                CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            ))
        }
        val hrService = BluetoothGattService(HR_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            .also { it.addCharacteristic(hrChar) }

        gattServer?.addService(hrService)
        Log.i(TAG, "GATT server set up with HR service")
    }

    private fun startBleAdvertising() {
        val advertiser = bluetoothManager.adapter.bluetoothLeAdvertiser
            ?: run { Log.e(TAG, "BLE advertising not supported"); return }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(HR_SERVICE_UUID))
            .setIncludeDeviceName(false)
            .build()

        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    private fun notifyHr(hr: Int) {
        lastHr = hr
        val char = gattServer?.getService(HR_SERVICE_UUID)?.getCharacteristic(HR_CHAR_UUID) ?: return
        val value = if (hr <= 255) byteArrayOf(0x00, hr.toByte())
                    else byteArrayOf(0x01, (hr and 0xFF).toByte(), (hr shr 8).toByte())

        connectedDevices.forEach { device ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gattServer?.notifyCharacteristicChanged(device, char, false, value)
            } else {
                @Suppress("DEPRECATION")
                char.value = value
                @Suppress("DEPRECATION")
                gattServer?.notifyCharacteristicChanged(device, char, false)
            }
        }
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("PTSD Monitor")
            .setContentText("Broadcasting HR via BLE")
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "wear_monitoring"
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_FAKE_HR = "fake_hr"
        @Volatile var lastHr: Int? = null
    }
}
