package com.ptsdalert.presentation

import com.ptsdalert.domain.model.ArousalState
import com.ptsdalert.domain.model.LogEntry

data class MonitoringUiState(
    val heartRate: Int? = null,
    val estimatedHrv: Double? = null,
    val arousalState: ArousalState = ArousalState.NORMAL,
    val deviceLabel: String = "",
    val isSimulator: Boolean = false,
    val recentLogs: List<LogEntry> = emptyList()
)
