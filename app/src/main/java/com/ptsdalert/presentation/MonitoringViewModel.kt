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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    // Tracks which state last triggered an alert (null = none active).
    // Only transitions fire alerts; persistent same-state samples are ignored.
    private var alertingState: ArousalState? = null
    // True after the user taps "I'm working out" for the current episode.
    private var dismissedForEpisode: Boolean = false

    init {
        // Listen for "I'm working out" dismissal from the notification.
        viewModelScope.launch {
            DismissSignal.flow.collect {
                alertManager.stopAlerts()
                dismissedForEpisode = true
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
        if (newState == alertingState) return  // same episode, no change

        // New episode — reset per-episode dismissal regardless of direction.
        dismissedForEpisode = false
        alertingState = newState

        when (newState) {
            ArousalState.NORMAL -> alertManager.stopAlerts()
            else -> alertManager.startAlerts(newState, viewModelScope)
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
