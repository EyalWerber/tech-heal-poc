package com.ptsdalert.infrastructure.bluetooth

import kotlinx.coroutines.flow.Flow

interface BleScannable {
    fun scanDevices(): Flow<List<BleDevice>>
    fun connectToDevice(address: String)
}
