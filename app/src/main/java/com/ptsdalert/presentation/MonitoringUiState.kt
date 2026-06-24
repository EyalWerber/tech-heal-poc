package com.ptsdalert.presentation

import com.ptsdalert.domain.model.ArousalState
import com.ptsdalert.domain.model.LogEntry
import com.ptsdalert.infrastructure.bluetooth.BleDevice

data class MonitoringUiState(
    val heartRate: Int? = null,
    val estimatedHrv: Double? = null,
    val arousalState: ArousalState = ArousalState.NORMAL,
    val deviceLabel: String = "",
    val isSimulator: Boolean = false,
    val recentLogs: List<LogEntry> = emptyList(),
    val baselineHrv: Double? = null,
    val hrvHyperThreshold: Double = 20.0,
    val hrvHypoThreshold: Double = 80.0,
    val bleDevices: List<BleDevice> = emptyList(),
    val bleScanning: Boolean = false
)
