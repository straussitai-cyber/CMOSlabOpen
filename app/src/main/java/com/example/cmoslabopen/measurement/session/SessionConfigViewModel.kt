package com.example.cmoslabopen.measurement.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cmoslabopen.measurement.camera.CameraEnumerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SessionConfigViewModel(
    private val cameras: List<CameraEnumerator.CameraInfo>,
    initialSelectedCameraIds: List<String> = emptyList(),
) : ViewModel() {

    private val _config = MutableStateFlow(
        SessionConfig(
            selectedCameraIds = initialSelectedCameraIds,
            outputMode = OutputMode.DNG,
            exposureTimeNanos = 10_000_000L,
            iso = 100,
            measurementDurationSeconds = 60,
            captureIntervalSeconds = 1.0f,
        ),
    )
    val config: StateFlow<SessionConfig> = _config.asStateFlow()

    private val _validationErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val validationErrors: StateFlow<Map<String, String>> = _validationErrors.asStateFlow()

    init {
        viewModelScope.launch {
            _config.collect { cfg ->
                _validationErrors.value = validate(cfg)
            }
        }
        _validationErrors.value = validate(_config.value)
    }

    fun updateSelectedCameraIds(ids: List<String>) {
        _config.update { it.copy(selectedCameraIds = ids) }
    }

    fun setOutputMode(mode: OutputMode) {
        _config.update { it.copy(outputMode = mode) }
    }

    fun setExposureMicros(micros: Long) {
        val nanos = micros.coerceAtLeast(0L) * 1_000L
        _config.update { it.copy(exposureTimeNanos = nanos) }
    }

    fun setIso(iso: Int) {
        _config.update { it.copy(iso = iso) }
    }

    fun setMeasurementDurationSeconds(seconds: Int) {
        _config.update { it.copy(measurementDurationSeconds = seconds.coerceAtLeast(0)) }
    }

    fun setCaptureIntervalSeconds(seconds: Float) {
        _config.update { it.copy(captureIntervalSeconds = seconds.coerceAtLeast(0f)) }
    }

    /** Helper text for the ISO field based on currently selected cameras. */
    fun isoRangeHelperText(): String? = buildRangeHelper(
        label = "ISO",
        ranges = selectedCameras().mapNotNull { it.isoRange },
        formatter = { "${it.lower}–${it.upper}" },
    )

    /** Helper text for the exposure field (microseconds) based on selected cameras. */
    fun exposureRangeHelperText(): String? = buildRangeHelper(
        label = "Exposure",
        ranges = selectedCameras().mapNotNull { it.exposureRangeNanos },
        formatter = { range ->
            val lowerUs = range.lower / 1_000L
            val upperUs = range.upper / 1_000L
            "$lowerUs–$upperUs µs"
        },
    )

    private fun selectedCameras(): List<CameraEnumerator.CameraInfo> {
        val ids = _config.value.selectedCameraIds
        return ids.mapNotNull { id -> cameras.find { it.id == id } }
    }

    private fun validate(config: SessionConfig): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        val selected = config.selectedCameraIds.mapNotNull { id ->
            cameras.find { it.id == id }
        }

        for (cameraInfo in selected) {
            cameraInfo.isoRange?.let { range ->
                if (config.iso < range.lower || config.iso > range.upper) {
                    errors["iso"] = "ISO out of sensor range ${range.lower}–${range.upper}"
                }
            }
            cameraInfo.exposureRangeNanos?.let { range ->
                if (config.exposureTimeNanos < range.lower || config.exposureTimeNanos > range.upper) {
                    errors["exposure"] = "Exposure out of sensor range"
                }
            }
        }

        val exposureSeconds = config.exposureTimeNanos / 1_000_000_000.0
        if (config.captureIntervalSeconds < exposureSeconds + 0.05) {
            errors["interval"] = "Interval must be > exposure + 50 ms"
        }

        if (config.measurementDurationSeconds < config.captureIntervalSeconds) {
            errors["duration"] = "Duration must be >= one interval"
        }

        return errors
    }

    private fun <T> buildRangeHelper(
        label: String,
        ranges: List<android.util.Range<T>>,
        formatter: (android.util.Range<T>) -> String,
    ): String? where T : Comparable<T> {
        if (ranges.isEmpty()) return null
        if (ranges.size == 1) {
            return "$label range: ${formatter(ranges.single())}"
        }
        val parts = ranges.map { formatter(it) }
        return "$label range (per camera): ${parts.joinToString(", ")}"
    }
}
