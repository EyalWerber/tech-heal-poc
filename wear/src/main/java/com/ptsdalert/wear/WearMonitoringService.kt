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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.os.PowerManager
import android.util.Log
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.sqrt

private const val TAG = "WearMonitoringService"

private val HR_SERVICE_UUID        = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
private val HR_CHAR_UUID           = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
private val HRV_SERVICE_UUID       = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
private val HRV_CHAR_UUID          = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
private val BREATHING_SERVICE_UUID = UUID.fromString("0000ffb0-0000-1000-8000-00805f9b34fb")
private val BREATHING_CHAR_UUID    = UUID.fromString("0000ffb1-0000-1000-8000-00805f9b34fb")
private val CCCD_UUID              = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

@SuppressLint("MissingPermission")
class WearMonitoringService : Service() {

    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var fakeHrJob: java.util.concurrent.ScheduledFuture<*>? = null
    @Volatile private var currentFakeHr: Int? = null
    @Volatile private var currentFakeHrv: Double? = null
    @Volatile private var currentFakeBreathing: BreathingMetrics? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var bluetoothManager: BluetoothManager
    private var gattServer: BluetoothGattServer? = null
    private val connectedDevices = mutableSetOf<BluetoothDevice>()

    private val rrBuffer = ArrayDeque<Double>()
    private var sensorManager: SensorManager? = null
    private val breathingCalculator = BreathingCalculator()

    // SensorManager TYPE_HEART_RATE bypasses Health Services and works with screen off
    // as long as PARTIAL_WAKE_LOCK keeps the CPU alive.
    private val hrSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (currentFakeHr != null) return
            val hr = event.values[0].toInt().takeIf { it > 0 } ?: return
            val hrv = currentFakeHrv ?: computeRmssd(hr)
            lastHr = hr
            lastHrv = hrv
            Log.i(TAG, "HR=$hr HRV=${hrv?.let { "%.1f".format(it) } ?: "--"}")
            notifyHr(hr)
            hrv?.let { notifyHrv(it) }
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    // TYPE_LINEAR_ACCELERATION at 10 Hz for chest-mounted breathing detection
    private val imuSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (currentFakeBreathing != null) return
            val metrics = breathingCalculator.addSample(
                event.values[0], event.values[1], event.values[2], System.currentTimeMillis()
            )
            if (metrics != null) {
                lastBreathingRate   = metrics.breathingRate
                lastBreathingDepth  = metrics.breathingDepth
                lastBreathingLength = metrics.breathingLength
                Log.i(TAG, "Breathing: BR=%.1f BD=%.3f BL=%.1f".format(
                    metrics.breathingRate, metrics.breathingDepth, metrics.breathingLength))
                notifyBreathing(metrics)
            }
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    private fun computeRmssd(hr: Int): Double? {
        if (hr <= 0) return null
        val rr = 60000.0 / hr
        rrBuffer.addLast(rr)
        if (rrBuffer.size > 10) rrBuffer.removeFirst()
        if (rrBuffer.size < 2) return null
        val squaredDiffs = rrBuffer.zipWithNext().map { (a, b) -> (b - a) * (b - a) }
        val result = sqrt(squaredDiffs.average())
        return if (result.isFinite()) result else null
    }

    // Sequential GATT chain: HR added → HRV added → BREATHING added (avoids race conditions)
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            when {
                service.uuid == HR_SERVICE_UUID && status == BluetoothGatt.GATT_SUCCESS ->
                    gattServer?.addService(buildNotifyService(HRV_SERVICE_UUID, HRV_CHAR_UUID))
                service.uuid == HRV_SERVICE_UUID && status == BluetoothGatt.GATT_SUCCESS ->
                    gattServer?.addService(buildNotifyService(BREATHING_SERVICE_UUID, BREATHING_CHAR_UUID))
            }
        }
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
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) { Log.i(TAG, "BLE advertising started") }
        override fun onStartFailure(errorCode: Int) { Log.e(TAG, "BLE advertising failed: $errorCode") }
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

        sensorManager = getSystemService(SensorManager::class.java)
        sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)?.let { sensor ->
            sensorManager?.registerListener(hrSensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.i(TAG, "HR sensor registered: ${sensor.name}")
        } ?: Log.e(TAG, "No TYPE_HEART_RATE sensor found")

        sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.let { sensor ->
            sensorManager?.registerListener(imuSensorListener, sensor, 100_000) // 10 Hz
            Log.i(TAG, "IMU sensor registered: ${sensor.name}")
        } ?: Log.e(TAG, "No TYPE_LINEAR_ACCELERATION sensor found")

        Log.i(TAG, "Service started — measuring HR + HRV + Breathing")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_BROADCAST -> startBleAdvertising()
            ACTION_STOP_BROADCAST  -> stopBleAdvertising()
            ACTION_FAKE_HR -> {
                val bpm = intent.getIntExtra(EXTRA_FAKE_HR, 0)
                if (bpm > 0) setFakeHr(bpm) else clearFakeHr()
            }
            ACTION_FAKE_HRV -> {
                val hrv = intent.getDoubleExtra(EXTRA_FAKE_HRV, -1.0)
                if (hrv >= 0) setFakeHrv(hrv) else clearFakeHrv()
            }
            ACTION_FAKE_BREATHING -> {
                val br = intent.getFloatExtra(EXTRA_FAKE_BR, -1f)
                if (br >= 0) setFakeBreathing(br) else clearFakeBreathing()
            }
        }
        return START_STICKY
    }

    private fun setFakeHr(bpm: Int) {
        fakeHrJob?.cancel(false)
        currentFakeHr = bpm
        rrBuffer.clear()
        fakeHrJob = scheduler.scheduleAtFixedRate({
            notifyHr(bpm)
            lastHr = bpm
            lastHrv = null
        }, 0, 1, java.util.concurrent.TimeUnit.SECONDS)
        Log.i(TAG, "Fake HR: $bpm BPM")
    }

    private fun clearFakeHr() {
        fakeHrJob?.cancel(false)
        fakeHrJob = null
        currentFakeHr = null
        rrBuffer.clear()
        Log.i(TAG, "Fake HR cleared — back to real HR")
    }

    private fun setFakeHrv(hrv: Double) {
        currentFakeHrv = hrv
        lastHrv = hrv
        notifyHrv(hrv)
        Log.i(TAG, "Fake HRV: $hrv ms")
    }

    private fun clearFakeHrv() {
        currentFakeHrv = null
        Log.i(TAG, "Fake HRV cleared")
    }

    private fun setFakeBreathing(br: Float) {
        val bl = if (br > 0) 60f / br else 4f
        val metrics = BreathingMetrics(br, 0.12f, bl)
        currentFakeBreathing = metrics
        lastBreathingRate   = metrics.breathingRate
        lastBreathingDepth  = metrics.breathingDepth
        lastBreathingLength = metrics.breathingLength
        notifyBreathing(metrics)
        Log.i(TAG, "Fake Breathing: BR=$br BPM")
    }

    private fun clearFakeBreathing() {
        currentFakeBreathing = null
        breathingCalculator.reset()
        Log.i(TAG, "Fake Breathing cleared")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopBleAdvertising()
        gattServer?.close()
        sensorManager?.unregisterListener(hrSensorListener)
        sensorManager?.unregisterListener(imuSensorListener)
        fakeHrJob?.cancel(false)
        scheduler.shutdown()
        wakeLock?.release()
    }

    private fun setupBleGattServer() {
        bluetoothManager = getSystemService(BluetoothManager::class.java)
        gattServer = bluetoothManager.openGattServer(this, gattServerCallback)
        // Chain starts here: HR → (onServiceAdded) → HRV → (onServiceAdded) → BREATHING
        gattServer?.addService(buildNotifyService(HR_SERVICE_UUID, HR_CHAR_UUID))
        Log.i(TAG, "GATT server set up — starting HR→HRV→BREATHING service chain")
    }

    private fun buildNotifyService(serviceUuid: UUID, charUuid: UUID): BluetoothGattService =
        BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY).also { svc ->
            svc.addCharacteristic(
                BluetoothGattCharacteristic(
                    charUuid,
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_READ
                ).apply {
                    addDescriptor(BluetoothGattDescriptor(CCCD_UUID,
                        BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))
                }
            )
        }

    private fun startBleAdvertising() {
        val advertiser = bluetoothManager.adapter.bluetoothLeAdvertiser
            ?: run { Log.e(TAG, "BLE advertising not supported"); return }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true).setTimeout(0).build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(HR_SERVICE_UUID))
            .setIncludeDeviceName(false).build()
        advertiser.startAdvertising(settings, data, advertiseCallback)
        Log.i(TAG, "Broadcasting started")
    }

    private fun stopBleAdvertising() {
        bluetoothManager.adapter.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        connectedDevices.clear()
        Log.i(TAG, "Broadcasting stopped")
    }

    private fun notifyHr(hr: Int) {
        val char = gattServer?.getService(HR_SERVICE_UUID)?.getCharacteristic(HR_CHAR_UUID) ?: return
        val value = if (hr <= 255) byteArrayOf(0x00, hr.toByte())
                    else byteArrayOf(0x01, (hr and 0xFF).toByte(), (hr shr 8).toByte())
        notifyChar(char, value)
    }

    private fun notifyHrv(hrv: Double) {
        val char = gattServer?.getService(HRV_SERVICE_UUID)?.getCharacteristic(HRV_CHAR_UUID) ?: return
        val tenths = (hrv * 10).toInt().coerceIn(0, 65535)
        notifyChar(char, byteArrayOf((tenths and 0xFF).toByte(), (tenths shr 8).toByte()))
    }

    private fun notifyBreathing(metrics: BreathingMetrics) {
        val char = gattServer?.getService(BREATHING_SERVICE_UUID)?.getCharacteristic(BREATHING_CHAR_UUID) ?: return
        // 6-byte packet: BR×10, BD×1000, BL×10 — each uint16 little-endian
        val brTenths = (metrics.breathingRate   * 10).toInt().coerceIn(0, 65535)
        val bdThou   = (metrics.breathingDepth  * 1000).toInt().coerceIn(0, 65535)
        val blTenths = (metrics.breathingLength * 10).toInt().coerceIn(0, 65535)
        notifyChar(char, byteArrayOf(
            (brTenths and 0xFF).toByte(), (brTenths shr 8).toByte(),
            (bdThou   and 0xFF).toByte(), (bdThou   shr 8).toByte(),
            (blTenths and 0xFF).toByte(), (blTenths shr 8).toByte()
        ))
    }

    private fun notifyChar(char: BluetoothGattCharacteristic, value: ByteArray) {
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
            .setContentText("Measuring HR + HRV + Breathing")
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL_ID      = "wear_monitoring"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START_BROADCAST  = "com.ptsdalert.wear.START_BROADCAST"
        const val ACTION_STOP_BROADCAST   = "com.ptsdalert.wear.STOP_BROADCAST"
        const val ACTION_FAKE_HR          = "com.ptsdalert.wear.FAKE_HR"
        const val ACTION_FAKE_HRV         = "com.ptsdalert.wear.FAKE_HRV"
        const val ACTION_FAKE_BREATHING   = "com.ptsdalert.wear.FAKE_BREATHING"
        const val EXTRA_FAKE_HR           = "fake_hr"
        const val EXTRA_FAKE_HRV          = "fake_hrv"
        const val EXTRA_FAKE_BR           = "fake_br"
        @Volatile var lastHr: Int?             = null
        @Volatile var lastHrv: Double?          = null
        @Volatile var lastBreathingRate: Float?  = null
        @Volatile var lastBreathingDepth: Float? = null
        @Volatile var lastBreathingLength: Float? = null
    }
}
