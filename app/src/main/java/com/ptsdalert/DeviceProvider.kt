package com.ptsdalert

import com.ptsdalert.domain.ports.WearableDataSource
import com.ptsdalert.infrastructure.bluetooth.BluetoothWearableDataSource
import com.ptsdalert.infrastructure.garmin.GarminWearableDataSource
import com.ptsdalert.infrastructure.polar.PolarWearableDataSource
import com.ptsdalert.infrastructure.simulator.SimulatorWearableDataSource
import com.ptsdalert.infrastructure.tcp.TcpWearableDataSource
import com.ptsdalert.infrastructure.usb.UsbWearableDataSource

// FACTORY — the ONE place in the entire app that knows about concrete adapters.
// Change `activeDevice` to swap the hardware source. Nothing else needs to change.
//
// `object` = singleton in Kotlin. Python equivalent:
//   class DeviceProvider:
//       active_device = DeviceType.SIMULATOR
//       @staticmethod
//       def create(): ...
//
// WHY a factory instead of constructing the adapter directly in MainActivity?
// Centralizing the decision here means searching "DeviceProvider" shows you
// exactly where hardware selection happens. It's also the seam where a
// config file or build flavor could override the choice in the future.
object DeviceProvider {

    // ← CHANGE THIS LINE to switch wearable adapters.
    val activeDevice: DeviceType = DeviceType.TCP

    fun create(): WearableDataSource = when (activeDevice) {
        DeviceType.SIMULATOR  -> SimulatorWearableDataSource()
        DeviceType.BLUETOOTH  -> BluetoothWearableDataSource()
        DeviceType.TCP        -> TcpWearableDataSource()
        DeviceType.USB        -> UsbWearableDataSource()
        DeviceType.GARMIN     -> GarminWearableDataSource()
        DeviceType.POLAR      -> PolarWearableDataSource()
    }
}
