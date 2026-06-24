package com.ptsdalert.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptsdalert.domain.detection.DetectionEngine
import com.ptsdalert.domain.detection.HrvCalculator
import com.ptsdalert.domain.model.ArousalState
import com.ptsdalert.domain.model.DetectionConfig
import com.ptsdalert.domain.ports.SimulatorControls
import com.ptsdalert.domain.ports.WearableDataSource
import com.ptsdalert.infrastructure.bluetooth.BleScannable
import com.ptsdalert.infrastructure.alert.AlertManager
import com.ptsdalert.infrastructure.alert.DismissSignal
import com.ptsdalert.infrastructure.logging.AppLogger
import com.ptsdalert.infrastructure.settings.SettingsRepository
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
    private val alertManager: AlertManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val simulatorControls: SimulatorControls? = wearableDataSource as? SimulatorControls
    private val bleScanner: BleScannable? = wearableDataSource as? BleScannable

    private var detectionConfig = buildConfig(settingsRepository.getBaselineHrv())

    private val _uiState = MutableStateFlow(
        MonitoringUiState(
            deviceLabel = wearableDataSource.deviceLabel,
            isSimulator = simulatorControls != null,
            baselineHrv = detectionConfig.baselineHrv,
            hrvHyperThreshold = detectionConfig.hrvHyperThreshold,
            hrvHypoThreshold = detectionConfig.hrvHypoThreshold
        )
    )
    val uiState: StateFlow<MonitoringUiState> = _uiState.asStateFlow()

    private val hrvCalculator = HrvCalculator()
    private var alertingState: ArousalState? = null
    private var debounceJob: Job? = null
    private var pendingState: ArousalState? = null

    init {
        AppLogger.i(TAG, "ViewModel created — source: ${wearableDataSource.deviceLabel}")
        logConfig()

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
                val state = DetectionEngine.classify(sample, detectionConfig)
                val hrv = sample.heartRate?.let { hrvCalculator.addSample(it) }
                _uiState.update { current ->
                    current.copy(heartRate = sample.heartRate, estimatedHrv = hrv, arousalState = state)
                }
                handleStateTransition(state)
            }
        }

        bleScanner?.let { scanner ->
            _uiState.update { it.copy(bleScanning = true) }
            viewModelScope.launch {
                scanner.scanDevices().collect { devices ->
                    _uiState.update { it.copy(bleDevices = devices, bleScanning = true) }
                }
            }
        }
    }

    fun setBaselineHrv(hrv: Double?) {
        settingsRepository.setBaselineHrv(hrv)
        detectionConfig = buildConfig(hrv)
        _uiState.update { it.copy(
            baselineHrv = hrv,
            hrvHyperThreshold = detectionConfig.hrvHyperThreshold,
            hrvHypoThreshold = detectionConfig.hrvHypoThreshold
        )}
        logConfig()
    }

    private fun logConfig() {
        val baseline = detectionConfig.baselineHrv
        if (baseline != null) {
            AppLogger.i(TAG, "HRV baseline: ${baseline}ms → hyper<${detectionConfig.hrvHyperThreshold}ms hypo>${detectionConfig.hrvHypoThreshold}ms")
        } else {
            AppLogger.i(TAG, "HRV thresholds: default (hyper<20ms hypo>80ms)")
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

    fun onDeviceSelected(address: String) {
        bleScanner?.connectToDevice(address)
        _uiState.update { it.copy(bleDevices = emptyList(), bleScanning = false) }
    }

    override fun onCleared() {
        AppLogger.i(TAG, "ViewModel cleared")
        super.onCleared()
        alertManager.stopAlerts()
    }

    private companion object {
        fun buildConfig(baseline: Double?) = DetectionConfig(baselineHrv = baseline)
    }
}
