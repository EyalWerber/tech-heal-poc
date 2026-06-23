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
