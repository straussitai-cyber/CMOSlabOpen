package com.example.cmoslabopen.measurement.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.util.Size
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates a set of independent [CameraController] instances — one per
 * physical camera — so a single measurement session can capture from multiple
 * sensors in lock-step.
 *
 * Each [CameraController] opens its camera on its own dedicated [HandlerThread]
 * (see [CameraController.openCamera]). This class does NOT use a logical
 * multi-camera device; every physical camera is opened individually via
 * [CameraManager.openCamera].
 *
 * Concurrency: minSdk is 30, so [CameraManager.getConcurrentCameraIds] and the
 * rest of the concurrent-camera API surface are available unconditionally. No
 * `Build.VERSION.SDK_INT` guards are present.
 *
 * Synchronisation caveat: Android does not guarantee hardware-level shutter
 * synchronisation between independently opened camera devices. The HAL's
 * declared sync class is [CameraCharacteristics.SYNC_MAX_LATENCY] — if it
 * equals [CameraCharacteristics.SYNC_MAX_LATENCY_PER_FRAME_CONTROL] the camera
 * claims zero-latency sync; otherwise timestamps will differ. Callers should
 * always read each frame's `SENSOR_TIMESTAMP` from the [TotalCaptureResult]
 * returned by [captureAll] to compute the actual inter-sensor delta.
 */
class MultiCameraController(private val context: Context) {

    private val cameraManager: CameraManager =
        context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val _controllers = linkedMapOf<String, CameraController>()
    private val _characteristics = linkedMapOf<String, CameraCharacteristics>()

    /** Currently opened controllers, keyed by camera id. */
    val controllers: Map<String, CameraController> get() = _controllers

    /** Cached [CameraCharacteristics] for every opened camera. */
    val characteristics: Map<String, CameraCharacteristics> get() = _characteristics

    /**
     * Whether the HAL allows [ids] to be opened simultaneously.
     *
     * Implements the literal contract from the spec: returns true iff [ids] is
     * a subset of at least one set in [CameraManager.getConcurrentCameraIds].
     */
    fun canOpenConcurrently(ids: Set<String>): Boolean {
        if (ids.isEmpty()) return false
        return cameraManager.concurrentCameraIds.any { set -> set.containsAll(ids) }
    }

    /**
     * Opens every camera in [cameraInfos] in parallel and configures each
     * controller's capture session for the given exposure/ISO.
     *
     * If any camera fails to open, every already-opened controller is closed
     * and the original exception is re-thrown.
     */
    suspend fun openAll(
        cameraInfos: List<CameraEnumerator.CameraInfo>,
        exposureNanos: Long,
        iso: Int,
    ) {
        require(cameraInfos.isNotEmpty()) { "openAll requires at least one camera" }
        closeAll()

        // Concurrent camera operation is restricted to the documented mandatory
        // concurrent stream combinations: YUV_420_888 at <= 1280x720 per camera.
        // Using full-resolution RAW or YUV on multiple cameras causes capture
        // to fail at runtime with CaptureFailure.REASON_ERROR even when session
        // configuration succeeds. Force-clamp here when more than one camera is
        // opened together.
        val isConcurrent = cameraInfos.size > 1
        val concurrentMaxSize = if (isConcurrent) CONCURRENT_MAX_SIZE else null

        val opened = ConcurrentHashMap<String, CameraController>()
        try {
            coroutineScope {
                cameraInfos.forEach { info ->
                    launch {
                        val controller = CameraController()
                        try {
                            controller.openCamera(info.id, context)
                            controller.createCaptureSession(
                                cameraInfo = info,
                                exposureNanos = exposureNanos,
                                iso = iso,
                                forceYuv = isConcurrent,
                                maxSize = concurrentMaxSize,
                            )
                            opened[info.id] = controller
                        } catch (t: Throwable) {
                            runCatching { controller.closeCamera() }
                            throw t
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            opened.values.forEach { runCatching { it.closeCamera() } }
            throw t
        }

        cameraInfos.forEach { info ->
            opened[info.id]?.let { ctrl ->
                _controllers[info.id] = ctrl
                _characteristics[info.id] = cameraManager.getCameraCharacteristics(info.id)
            }
        }
    }

    /**
     * Captures one frame from every opened controller in parallel.
     *
     * Hardware shutter synchronisation is not guaranteed across independent
     * camera devices. Each entry's [TotalCaptureResult] carries the
     * `SENSOR_TIMESTAMP` so the caller can compute inter-sensor offsets.
     *
     * Caller owns each [Image] and must call [Image.close] promptly to avoid
     * stalling the 3-slot [android.media.ImageReader] backing each controller.
     */
    suspend fun captureAll(): Map<String, Pair<Image, TotalCaptureResult>> = coroutineScope {
        val deferreds = _controllers.entries.map { (id, ctrl) ->
            async { id to ctrl.captureImage() }
        }
        val results = try {
            deferreds.awaitAll()
        } catch (t: Throwable) {
            deferreds.forEach { d ->
                if (d.isCompleted && !d.isCancelled) {
                    runCatching { d.getCompleted().second.first.close() }
                }
            }
            throw t
        }
        results.toMap()
    }

    /** Closes every underlying controller (each tears down its own HandlerThread). */
    fun closeAll() {
        _controllers.values.forEach { runCatching { it.closeCamera() } }
        _controllers.clear()
        _characteristics.clear()
    }

    companion object {
        /**
         * Per-camera stream cap for concurrent operation, per the Camera2
         * mandatory concurrent stream combinations table. 720p YUV is the
         * universally supported safe size.
         */
        private val CONCURRENT_MAX_SIZE = Size(1280, 720)
    }
}
