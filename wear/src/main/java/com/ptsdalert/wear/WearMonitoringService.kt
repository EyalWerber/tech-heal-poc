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
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.sqrt

private const val TAG = "WearMonitoringService"

private val HR_SERVICE_UUID  = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
private val HR_CHAR_UUID     = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
private val HRV_SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
private val HRV_CHAR_UUID    = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
private val CCCD_UUID        = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

@SuppressLint("MissingPermission")
class WearMonitoringService : Service() {

    private val executor = Executors.newSingleThreadExecutor()
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var bluetoothManager: BluetoothManager
    private var gattServer: BluetoothGattServer? = null
    private val connectedDevices = mutableSetOf<BluetoothDevice>()

    private val rrBuffer = ArrayDeque<Double>()

    private val measureCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: androidx.health.services.client.data.DeltaDataType<*, *>, availability: Availability) {
            Log.d(TAG, "$dataType availability: $availability")
        }
        override fun onDataReceived(data: DataPointContainer) {
            val hr = data.getData(DataType.HEART_RATE_BPM).lastOrNull()?.value?.toInt() ?: return
            val hrv = computeRmssd(hr)
            lastHr = hr
            lastHrv = hrv
            Log.i(TAG, "HR=$hr HRV=${hrv?.let { "%.1f".format(it) } ?: "--"}")
            notifyHr(hr)
            hrv?.let { notifyHrv(it) }
        }
    }

    private fun computeRmssd(hr: Int): Double? {
        val rr = 60000.0 / hr
        rrBuffer.addLast(rr)
        if (rrBuffer.size > 10) rrBuffer.removeFirst()
        if (rrBuffer.size < 2) return null
        val squaredDiffs = rrBuffer.zipWithNext().map { (a, b) -> (b - a) * (b - a) }
        return sqrt(squaredDiffs.average())
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
        startBleAdvertising()

        HealthServices.getClient(this).measureClient
            .registerMeasureCallback(DataType.HEART_RATE_BPM, executor, measureCallback)

        Log.i(TAG, "Service started — measuring HR + HRV")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        bluetoothManager.adapter.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        gattServer?.close()
        HealthServices.getClient(this).measureClient
            .unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, measureCallback)
        executor.shutdown()
        wakeLock?.release()
    }

    private fun setupBleGattServer() {
        bluetoothManager = getSystemService(BluetoothManager::class.java)
        gattServer = bluetoothManager.openGattServer(this, gattServerCallback)

        // HR Service
        val hrChar = BluetoothGattCharacteristic(
            HR_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).apply {
            addDescriptor(BluetoothGattDescriptor(CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))
        }
        gattServer?.addService(
            BluetoothGattService(HR_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
                .also { it.addCharacteristic(hrChar) }
        )

        // HRV Service
        val hrvChar = BluetoothGattCharacteristic(
            HRV_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).apply {
            addDescriptor(BluetoothGattDescriptor(CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))
        }
        gattServer?.addService(
            BluetoothGattService(HRV_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
                .also { it.addCharacteristic(hrvChar) }
        )

        Log.i(TAG, "GATT server set up with HR + HRV services")
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
        val value = byteArrayOf((tenths and 0xFF).toByte(), (tenths shr 8).toByte())
        notifyChar(char, value)
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
            .setContentText("Measuring HR + HRV")
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "wear_monitoring"
        private const val NOTIFICATION_ID = 1001
        @Volatile var lastHr: Int? = null
        @Volatile var lastHrv: Double? = null
    }
}
