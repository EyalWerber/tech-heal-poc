package com.ptsdalert.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptsdalert.domain.detection.DetectionEngine
import com.ptsdalert.domain.model.ArousalState
import com.ptsdalert.domain.ports.SimulatorControls
import com.ptsdalert.domain.ports.WearableDataSource
import com.ptsdalert.infrastructure.alert.AlertManager
import com.ptsdalert.infrastructure.alert.DismissSignal
import com.ptsdalert.infrastructure.simulator.SimulatorMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// How long a new state must hold before we fire an alert.
// Prevents alert spam when HR fluctuates on the threshold boundary.
private const val STATE_DEBOUNCE_MS = 5_000L

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
        viewModelScope.launch {
            DismissSignal.flow.collect {
                alertManager.stopAlerts()
                // Keep alertingState so the same episode doesn't re-fire.
                // A genuine new transition will start a fresh debounce.
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
        // Already committed to this state — nothing to do.
        if (newState == alertingState) {
            debounceJob?.cancel()
            pendingState = null
            return
        }

        // Already waiting on this same pending transition — let the timer run.
        if (newState == pendingState) return

        // New candidate state: restart the debounce timer.
        debounceJob?.cancel()
        pendingState = newState

        debounceJob = viewModelScope.launch {
            delay(STATE_DEBOUNCE_MS)
            commitTransition(newState)
        }
    }

    private fun commitTransition(state: ArousalState) {
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
        super.onCleared()
        alertManager.stopAlerts()
    }
}
