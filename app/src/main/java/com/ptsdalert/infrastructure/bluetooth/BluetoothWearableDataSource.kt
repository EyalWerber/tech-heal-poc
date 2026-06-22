package com.ptsdalert.infrastructure.bluetooth

import com.ptsdalert.domain.model.PhysiologicalSample
import com.ptsdalert.domain.ports.WearableDataSource
import kotlinx.coroutines.flow.Flow

// ADAPTER for future Bluetooth Low Energy (BLE) devices.
// BLE wearables use GATT (Generic Attribute Profile) services.
// A heart-rate BLE device exposes:
//   Service UUID: 0x180D (Heart Rate)
//   Characteristic UUID: 0x2A37 (Heart Rate Measurement)
//
// TO INTEGRATE A BLE DEVICE:
//   1. Add permission to AndroidManifest.xml:
//      <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
//      <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
//   2. Use Android's BluetoothLeScanner to find the device by service UUID.
//   3. Connect via BluetoothGatt and register a BluetoothGattCallback.
//   4. In onCharacteristicChanged(), parse the HR byte and emit to a Flow.
//   5. Replace the TODO() in streamSamples() with that Flow.
//
// Python analogy: this is like a stub class waiting for its bleak/pygatt implementation.
class BluetoothWearableDataSource : WearableDataSource {

    override val deviceLabel: String = "Bluetooth"

    override fun streamSamples(): Flow<PhysiologicalSample> {
        // TODO: Implement BLE GATT scanning and characteristic notification streaming.
        // See class-level comment for integration steps.
        TODO("BLE integration not yet implemented")
    }
}
