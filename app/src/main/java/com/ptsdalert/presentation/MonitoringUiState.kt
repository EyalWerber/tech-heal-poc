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
    val bleScanning: Boolean = false,
    // Breathing metrics from IMU
    val breathingRate: Float? = null,
    val breathingDepth: Float? = null,
    val breathingLength: Float? = null,
    // Metric visibility toggles (all shown by default)
    val showHr: Boolean  = true,
    val showHrv: Boolean = true,
    val showBd: Boolean  = true,
    val showBr: Boolean  = true,
    val showBl: Boolean  = true
)
