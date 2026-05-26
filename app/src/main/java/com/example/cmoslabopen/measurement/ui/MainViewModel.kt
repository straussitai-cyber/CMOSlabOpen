package com.example.cmoslabopen.measurement.ui

import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cmoslabopen.measurement.camera.CameraEnumerator
import com.example.cmoslabopen.measurement.camera.MultiCameraController
import com.example.cmoslabopen.measurement.session.OutputMode
import com.example.cmoslabopen.measurement.session.SessionConfig
import com.example.cmoslabopen.measurement.session.SessionConfigViewModel
import com.example.cmoslabopen.measurement.session.SessionRunner
import com.example.cmoslabopen.measurement.storage.DngSaver
import com.example.cmoslabopen.measurement.storage.HistogramSaver
import com.example.cmoslabopen.measurement.storage.MetadataWriter
import com.example.cmoslabopen.measurement.storage.StoragePaths
import com.example.cmoslabopen.measurement.thermal.ThermalMonitor
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.floor

object NavRoutes {
    const val CAMERA_SELECT = "camera_select"
    const val SESSION_CONFIG = "session_config"
    const val SESSION_ACTIVE = "session_active"
    const val SESSION_DONE = "session_done"
}

data class CameraDisplayInfo(
    val id: String,
    val supportsRaw: Boolean,
)

data class ActiveSessionUi(
    val config: SessionConfig,
    val elapsedSeconds: Int = 0,
    val totalSeconds: Int = 0,
    val capturesCompleted: Int = 0,
    val totalCaptures: Int = 0,
    val temperatureCelsius: Float? = null,
    val thermalStatus: Int = 0,
    val cameras: List<CameraDisplayInfo> = emptyList(),
)

data class DoneSessionUi(
    val sessionId: String,
    val totalCaptures: Int,
    val outputMode: String,
    val durationSeconds: Int,
    val temperatureMin: Float?,
    val temperatureMax: Float?,
    val temperatureMean: Float?,
    val sessionDir: File,
    val wasCancelled: Boolean = false,
    val errorMessage: String? = null,
)

data class MainUiState(
    val route: String = NavRoutes.CAMERA_SELECT,
    val cameras: List<CameraEnumerator.CameraInfo> = emptyList(),
    val selectedCameraIds: Set<String> = emptySet(),
    val multiCameraMode: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val activeSession: ActiveSessionUi? = null,
    val doneSession: DoneSessionUi? = null,
    val showAbortDialog: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext
    private val cameraManager =
        appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val cameraEnumerator = CameraEnumerator.from(cameraManager)
    private val multiCameraController = MultiCameraController(appContext)
    private val thermalMonitor = ThermalMonitor(appContext)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var sessionConfigViewModel: SessionConfigViewModel? = null
    private var sessionRunner: SessionRunner? = null
    private var sessionJob: Job? = null
    private var tickerJob: Job? = null
    private var sessionStartMillis: Long = 0L
    private var lastTemperatureC: Float? = null

    init {
        thermalMonitor.start()
        viewModelScope.launch {
            val cameras = cameraEnumerator.listAllCameras()
            _uiState.update { it.copy(cameras = cameras) }
        }
        viewModelScope.launch {
            thermalMonitor.thermalStatus.collect { status ->
                _uiState.update { state ->
                    val active = state.activeSession ?: return@update state
                    state.copy(activeSession = active.copy(thermalStatus = status))
                }
            }
        }
    }

    override fun onCleared() {
        thermalMonitor.stop()
        teardownSession()
        super.onCleared()
    }

    fun canOpenConcurrently(ids: Set<String>): Boolean =
        multiCameraController.canOpenConcurrently(ids)

    fun setMultiCameraMode(enabled: Boolean) {
        _uiState.update { state ->
            val selection = if (enabled) {
                state.selectedCameraIds
            } else {
                state.selectedCameraIds.take(1).toSet()
            }
            state.copy(multiCameraMode = enabled, selectedCameraIds = selection)
        }
        sessionConfigViewModel?.updateSelectedCameraIds(
            _uiState.value.selectedCameraIds.toList(),
        )
    }

    fun setSelectedCameraIds(ids: Set<String>) {
        _uiState.update { it.copy(selectedCameraIds = ids) }
        sessionConfigViewModel?.updateSelectedCameraIds(ids.toList())
    }

    fun navigateToSessionConfig() {
        val selected = _uiState.value.selectedCameraIds.toList()
        if (selected.isEmpty()) return
        sessionConfigViewModel = SessionConfigViewModel(
            cameras = _uiState.value.cameras,
            initialSelectedCameraIds = selected,
        )
        _uiState.update { it.copy(route = NavRoutes.SESSION_CONFIG, errorMessage = null) }
    }

    fun sessionConfigViewModel(): SessionConfigViewModel {
        return sessionConfigViewModel ?: SessionConfigViewModel(
            cameras = _uiState.value.cameras,
            initialSelectedCameraIds = _uiState.value.selectedCameraIds.toList(),
        ).also { sessionConfigViewModel = it }
    }

    fun startSession(config: SessionConfig) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            teardownSession()

            try {
                val cameraInfos = config.selectedCameraIds.mapNotNull { id ->
                    _uiState.value.cameras.find { it.id == id }
                }
                require(cameraInfos.isNotEmpty()) { "No valid cameras selected" }

                multiCameraController.openAll(
                    cameraInfos = cameraInfos,
                    exposureNanos = config.exposureTimeNanos,
                    iso = config.iso,
                )

                val sessionDir = StoragePaths.sessionDirectory(appContext, config.sessionId)
                val metadataWriter = MetadataWriter(sessionDir)
                metadataWriter.writeConfig(config)

                val runner = SessionRunner(
                    config = config,
                    controllers = multiCameraController.controllers,
                    characteristics = multiCameraController.characteristics,
                    thermalMonitor = thermalMonitor,
                    metadataWriter = metadataWriter,
                    dngSaver = DngSaver(appContext, config.sessionId),
                    histogramSaver = HistogramSaver(appContext, config.sessionId),
                )
                sessionRunner = runner

                val totalCaptures = floor(
                    config.measurementDurationSeconds / config.captureIntervalSeconds.toDouble(),
                ).toInt().coerceAtLeast(0)

                val cameras = cameraInfos.map { CameraDisplayInfo(it.id, it.supportsRaw) }
                sessionStartMillis = System.currentTimeMillis()
                lastTemperatureC = null

                _uiState.update {
                    it.copy(
                        route = NavRoutes.SESSION_ACTIVE,
                        isLoading = false,
                        activeSession = ActiveSessionUi(
                            config = config,
                            totalSeconds = config.measurementDurationSeconds,
                            totalCaptures = totalCaptures,
                            cameras = cameras,
                            thermalStatus = thermalMonitor.thermalStatus.value,
                        ),
                        doneSession = null,
                    )
                }

                startActiveTicker(config.measurementDurationSeconds)

                sessionJob = launch {
                    runner.progress.collect { progress ->
                        handleSessionProgress(progress, config, sessionDir)
                    }
                }
                runner.run()
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = t.message ?: "Session failed",
                    )
                }
                teardownSession()
            }
        }
    }

    fun requestAbort() {
        _uiState.update { it.copy(showAbortDialog = true) }
    }

    fun dismissAbortDialog() {
        _uiState.update { it.copy(showAbortDialog = false) }
    }

    fun confirmAbort() {
        _uiState.update { it.copy(showAbortDialog = false) }
        sessionRunner?.cancel()
    }

    fun startNewSession() {
        teardownSession()
        sessionConfigViewModel = null
        _uiState.update {
            MainUiState(
                route = NavRoutes.CAMERA_SELECT,
                cameras = it.cameras,
            )
        }
    }

    private fun startActiveTicker(totalSeconds: Int) {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            var tick = 0
            while (isActive) {
                val elapsed = ((System.currentTimeMillis() - sessionStartMillis) / 1000L)
                    .toInt()
                    .coerceAtMost(totalSeconds)

                if (tick % 5 == 0) {
                    lastTemperatureC = runCatching {
                        thermalMonitor.readTemperatureCelsius()
                    }.getOrNull()
                }

                _uiState.update { state ->
                    val active = state.activeSession ?: return@update state
                    state.copy(
                        activeSession = active.copy(
                            elapsedSeconds = elapsed,
                            temperatureCelsius = lastTemperatureC,
                        ),
                    )
                }
                tick++
                delay(1_000L)
            }
        }
    }

    private suspend fun handleSessionProgress(
        progress: SessionRunner.Progress,
        config: SessionConfig,
        sessionDir: File,
    ) {
        when (progress) {
            is SessionRunner.Progress.Started -> {
                _uiState.update { state ->
                    val active = state.activeSession ?: return@update state
                    state.copy(
                        activeSession = active.copy(totalCaptures = progress.totalFrames),
                    )
                }
            }

            is SessionRunner.Progress.Frame -> {
                val elapsedSec = (progress.elapsedNanos / 1_000_000_000L).toInt()
                _uiState.update { state ->
                    val active = state.activeSession ?: return@update state
                    state.copy(
                        activeSession = active.copy(
                            capturesCompleted = progress.frameIndex + 1,
                            elapsedSeconds = elapsedSec.coerceAtMost(active.totalSeconds),
                        ),
                    )
                }
            }

            is SessionRunner.Progress.Failed,
            SessionRunner.Progress.Cancelled,
            SessionRunner.Progress.Done,
            -> {
                tickerJob?.cancel()
                val summary = readManifestSummary(sessionDir)
                val durationSec = ((System.currentTimeMillis() - sessionStartMillis) / 1000L)
                    .toInt()
                val wasCancelled = progress is SessionRunner.Progress.Cancelled
                val failureMessage = (progress as? SessionRunner.Progress.Failed)
                    ?.cause?.message ?: (progress as? SessionRunner.Progress.Failed)
                    ?.cause?.javaClass?.simpleName

                _uiState.update {
                    it.copy(
                        route = NavRoutes.SESSION_DONE,
                        activeSession = null,
                        showAbortDialog = false,
                        doneSession = DoneSessionUi(
                            sessionId = config.sessionId,
                            totalCaptures = summary?.summary?.captureCount?.toInt()
                                ?: it.activeSession?.capturesCompleted
                                ?: 0,
                            outputMode = when (config.outputMode) {
                                OutputMode.DNG -> "DNG"
                                OutputMode.HISTOGRAM -> "HISTOGRAM"
                            },
                            durationSeconds = durationSec,
                            temperatureMin = summary?.summary?.temperatureMin,
                            temperatureMax = summary?.summary?.temperatureMax,
                            temperatureMean = summary?.summary?.temperatureMean,
                            sessionDir = sessionDir,
                            wasCancelled = wasCancelled,
                            errorMessage = failureMessage,
                        ),
                    )
                }
                teardownSession()
            }
        }
    }

    private fun teardownSession() {
        tickerJob?.cancel()
        tickerJob = null
        sessionJob?.cancel()
        sessionJob = null
        sessionRunner = null
        runCatching { multiCameraController.closeAll() }
    }

    private fun readManifestSummary(sessionDir: File): ManifestSummaryDto? {
        val file = File(sessionDir, "session_manifest.json")
        if (!file.exists()) return null
        return runCatching {
            Gson().fromJson(file.readText(), ManifestSummaryDto::class.java)
        }.getOrNull()
    }

    private data class ManifestSummaryDto(
        val summary: SummaryDto?,
    )

    private data class SummaryDto(
        val captureCount: Long,
        val temperatureMin: Float?,
        val temperatureMax: Float?,
        val temperatureMean: Float?,
    )
}
