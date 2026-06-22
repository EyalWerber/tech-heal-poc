package com.ptsdalert.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptsdalert.domain.detection.DetectionEngine
import com.ptsdalert.domain.model.ArousalState
import com.ptsdalert.domain.ports.SimulatorControls
import com.ptsdalert.domain.ports.WearableDataSource
import com.ptsdalert.infrastructure.alert.AlertManager
import com.ptsdalert.infrastructure.alert.DismissSignal
import com.ptsdalert.infrastructure.logging.AppLogger
import com.ptsdalert.infrastructure.simulator.SimulatorMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STATE_DEBOUNCE_MS = 5_000L
private const val TAG = "MonitoringViewModel"

class MonitoringViewModel(
    private val wearableDataSource: WearableDataSource,
    private val alertManager: AlertManager
) : ViewModel() {

    private val simulatorControls: SimulatorControls? = wearableDataSource as? SimulatorControls

    private val _uiState = MutableStateFlow(
        MonitoringUiState(
            deviceLabel = wearableDataSource.deviceLabel,
            isSimulator = simulatorControls != null
        )
    )
    val uiState: StateFlow<MonitoringUiState> = _uiState.asStateFlow()

    private var alertingState: ArousalState? = null
    private var debounceJob: Job? = null
    private var pendingState: ArousalState? = null

    init {
        AppLogger.i(TAG, "ViewModel created — source: ${wearableDataSource.deviceLabel}")

        viewModelScope.launch {
            AppLogger.observeRecent(50).collect { logs ->
                _uiState.update { it.copy(recentLogs = logs) }
            }
        }

        viewModelScope.launch {
            DismissSignal.flow.collect {
                AppLogger.i(TAG, "Dismiss received — stopping alerts for this episode")
                alertManager.stopAlerts()
            }
        }

        viewModelScope.launch {
            wearableDataSource.streamSamples().collect { sample ->
                val state = DetectionEngine.classify(sample)
                _uiState.update { current ->
                    current.copy(heartRate = sample.heartRate, arousalState = state)
                }
                handleStateTransition(state)
            }
        }
    }

    private fun handleStateTransition(newState: ArousalState) {
        if (newState == alertingState) {
            debounceJob?.cancel()
            pendingState = null
            return
        }
        if (newState == pendingState) return

        debounceJob?.cancel()
        pendingState = newState
        AppLogger.d(TAG, "State candidate: $newState — debouncing ${STATE_DEBOUNCE_MS}ms")

        debounceJob = viewModelScope.launch {
            delay(STATE_DEBOUNCE_MS)
            commitTransition(newState)
        }
    }

    private fun commitTransition(state: ArousalState) {
        AppLogger.i(TAG, "State committed: $state (held for ${STATE_DEBOUNCE_MS}ms)")
        alertingState = state
        pendingState = null
        when (state) {
            ArousalState.NORMAL -> alertManager.stopAlerts()
            else -> alertManager.startAlerts(state, viewModelScope)
        }
    }

    fun setSimulatorMode(mode: SimulatorMode) {
        simulatorControls?.setMode(mode)
    }

    override fun onCleared() {
        AppLogger.i(TAG, "ViewModel cleared")
        super.onCleared()
        alertManager.stopAlerts()
    }
}
