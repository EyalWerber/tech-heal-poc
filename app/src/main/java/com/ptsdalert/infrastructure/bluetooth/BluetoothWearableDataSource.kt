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
import android.content.pm.PackageManager
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

private val HR_SERVICE_UUID        = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
private val HR_CHAR_UUID           = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
private val HRV_SERVICE_UUID       = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
private val HRV_CHAR_UUID          = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
private val BREATHING_SERVICE_UUID = UUID.fromString("0000ffb0-0000-1000-8000-00805f9b34fb")
private val BREATHING_CHAR_UUID    = UUID.fromString("0000ffb1-0000-1000-8000-00805f9b34fb")
private val CCCD_UUID              = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
        ) {
            AppLogger.w(TAG, "BLUETOOTH_SCAN not granted — scan skipped")
            close()
            return@callbackFlow
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

    private fun bondedWatch(): BluetoothDevice? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return null
        return bluetoothAdapter.bondedDevices
            .firstOrNull { it.name?.contains("Pixel Watch", ignoreCase = true) == true }
    }

    override fun streamSamples(): Flow<PhysiologicalSample> = flow {
        bondedWatch()?.let {
            AppLogger.i(TAG, "Auto-connecting to bonded watch: ${it.name} (${it.address})")
            deviceAddressChannel.trySend(it.address)
        } ?: AppLogger.i(TAG, "No bonded Pixel Watch found — waiting for scan selection")
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
        var latestHrv: Double? = null
        var latestBr: Float?   = null
        var latestBd: Float?   = null
        var latestBl: Float?   = null

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
                enableNotifications(g, HR_SERVICE_UUID, HR_CHAR_UUID)
            }

            // Sequential notification chain: HR → HRV → BREATHING
            override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                when (descriptor.characteristic.uuid) {
                    HR_CHAR_UUID  -> enableNotifications(g, HRV_SERVICE_UUID, HRV_CHAR_UUID)
                    HRV_CHAR_UUID -> enableNotifications(g, BREATHING_SERVICE_UUID, BREATHING_CHAR_UUID)
                }
            }

            // Pixel Watch restarts its GATT server when the service changes.
            override fun onServiceChanged(g: BluetoothGatt) {
                AppLogger.w(TAG, "GATT service changed — re-discovering services")
                g.discoverServices()
            }

            override fun onCharacteristicChanged(g: BluetoothGatt, char: BluetoothGattCharacteristic, value: ByteArray) {
                handleChar(char.uuid, value)
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(g: BluetoothGatt, char: BluetoothGattCharacteristic) {
                handleChar(char.uuid, char.value)
            }

            private fun handleChar(uuid: UUID, value: ByteArray) {
                when (uuid) {
                    HR_CHAR_UUID -> {
                        val hr = parseHrBytes(value) ?: return
                        val hrv = latestHrv ?: deriveHrv(hr)
                        trySend(PhysiologicalSample(
                            timestamp        = System.currentTimeMillis(),
                            heartRate        = hr,
                            hrv              = hrv,
                            skinTemperature  = null,
                            stressScore      = deriveStress(hr),
                            breathingRate    = latestBr,
                            breathingDepth   = latestBd,
                            breathingLength  = latestBl
                        ))
                    }
                    HRV_CHAR_UUID -> {
                        if (value.size >= 2) {
                            val tenths = ((value[1].toInt() and 0xFF) shl 8) or (value[0].toInt() and 0xFF)
                            latestHrv = tenths / 10.0
                            AppLogger.d(TAG, "HRV from watch: $latestHrv ms")
                        }
                    }
                    BREATHING_CHAR_UUID -> {
                        if (value.size >= 6) {
                            val brTenths  = ((value[1].toInt() and 0xFF) shl 8) or (value[0].toInt() and 0xFF)
                            val bdThou    = ((value[3].toInt() and 0xFF) shl 8) or (value[2].toInt() and 0xFF)
                            val blTenths  = ((value[5].toInt() and 0xFF) shl 8) or (value[4].toInt() and 0xFF)
                            latestBr = brTenths / 10f
                            latestBd = bdThou   / 1000f
                            latestBl = blTenths / 10f
                            AppLogger.d(TAG, "Breathing: BR=$latestBr BD=$latestBd BL=$latestBl")
                        }
                    }
                }
            }

            private fun enableNotifications(g: BluetoothGatt, serviceUuid: UUID, charUuid: UUID) {
                val char = g.getService(serviceUuid)?.getCharacteristic(charUuid)
                if (char == null) {
                    AppLogger.w(TAG, "Characteristic $charUuid not found — skipping")
                    return
                }
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
                AppLogger.i(TAG, "Notifications enabled for $charUuid")
            }
        }

        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        awaitClose {
            AppLogger.i(TAG, "Closing GATT to $address")
            gatt?.disconnect()
            gatt?.close()
        }
    }
}
