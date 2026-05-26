package com.example.cmoslabopen.measurement.camera

import android.content.Context
import android.media.Image

/**
 * Owns a single Camera2 [android.hardware.camera2.CameraDevice] + ImageReader
 * pipeline for one camera. All automatic processing (AE, AWB, AF, NR, hot pixel,
 * shading, OIS, video stabilisation) must be explicitly disabled on every
 * CaptureRequest emitted from here.
 *
 * No preview surface is ever attached — this is a measurement instrument.
 */
class CameraController(
    private val context: Context,
    val cameraId: String,
) {

    /** Opens the camera and prepares the RAW ImageReader. */
    suspend fun open() {
        TODO("Phase 2: open camera + create RAW ImageReader, wrap callbacks with suspendCancellableCoroutine")
    }

    /**
     * Captures a single RAW frame at the given manual settings.
     * Caller receives the [Image]; it must be processed and closed immediately.
     */
    suspend fun captureRaw(
        exposureTimeNanos: Long,
        sensitivityIso: Int,
    ): Image {
        TODO("Phase 2: build manual CaptureRequest, all 3A off, SENSOR_FRAME_DURATION = max(minFrameDuration, exposureTimeNanos)")
    }

    /** Releases the camera device and ImageReader. */
    suspend fun close() {
        TODO("Phase 2: tear down session, device, reader, handler thread")
    }
}
