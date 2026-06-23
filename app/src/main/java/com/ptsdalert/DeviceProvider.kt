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
