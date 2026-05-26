package com.example.cmoslabopen.measurement.camera

import android.content.Context
import android.media.Image

/**
 * Coordinates a set of [CameraController] instances that
 * [android.hardware.camera2.CameraManager.getConcurrentCameraIds] reported as
 * simultaneously openable, so a session can capture from multiple sensors
 * in lock-step.
 */
class MultiCameraController(
    private val context: Context,
    val cameraIds: List<String>,
) {

    /** Opens every camera in [cameraIds] in parallel. */
    suspend fun openAll() {
        TODO("Phase 3: open each CameraController concurrently")
    }

    /**
     * Captures one RAW frame from every camera at the same manual settings.
     * Returns the resulting images keyed by camera id; each must be closed
     * by the caller immediately after processing.
     */
    suspend fun captureRawFromAll(
        exposureTimeNanos: Long,
        sensitivityIso: Int,
    ): Map<String, Image> {
        TODO("Phase 3: dispatch synchronized captures across all controllers")
    }

    /** Closes every underlying controller. */
    suspend fun closeAll() {
        TODO("Phase 3: tear down all controllers")
    }
}
