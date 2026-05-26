package com.example.cmoslabopen.measurement.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Owns a single Camera2 [CameraDevice], [ImageReader], and [CameraCaptureSession]
 * for one camera. All automatic processing is explicitly disabled on every
 * [CaptureRequest]. No preview surface is attached.
 */
class CameraController {

    private val _error = MutableStateFlow<CameraError?>(null)
    val error: StateFlow<CameraError?> = _error.asStateFlow()

    private var cameraManager: CameraManager? = null
    private var characteristics: CameraCharacteristics? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var cameraId: String? = null

    private var outputFormat: Int = ImageFormat.YUV_420_888
    private var outputSize: Size? = null
    private var exposureNanos: Long = 0L
    private var iso: Int = 100

    private var sessionReady: CompletableDeferred<CameraCaptureSession>? = null

    /**
     * Opens [cameraId] on a dedicated [HandlerThread], wrapping
     * [CameraDevice.StateCallback] with [suspendCancellableCoroutine].
     */
    suspend fun openCamera(cameraId: String, context: Context) {
        clearError()
        closeCamera()

        val manager = context.applicationContext.getSystemService(Context.CAMERA_SERVICE)
            as CameraManager
        cameraManager = manager
        this.cameraId = cameraId
        characteristics = manager.getCameraCharacteristics(cameraId)

        val thread = HandlerThread("CameraController-$cameraId").also { it.start() }
        handlerThread = thread
        handler = Handler(thread.looper)

        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { closeCamera() }

            manager.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        cont.resume(Unit)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        postError(CameraError.Disconnected(cameraId))
                        camera.close()
                        cameraDevice = null
                        if (cont.isActive) {
                            cont.resumeWithException(
                                CameraException(CameraError.Disconnected(cameraId)),
                            )
                        }
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        val err = CameraError.DeviceError(cameraId, error)
                        postError(err)
                        camera.close()
                        cameraDevice = null
                        if (cont.isActive) {
                            cont.resumeWithException(CameraException(err))
                        }
                    }
                },
                handler,
            )
        }
    }

    /**
     * Configures an [ImageReader] and [CameraCaptureSession] for still capture at
     * [exposureNanos] / [iso]. Completes asynchronously; [captureImage] waits until
     * the session is configured.
     */
    fun createCaptureSession(
        cameraInfo: CameraEnumerator.CameraInfo,
        exposureNanos: Long,
        iso: Int,
        forceYuv: Boolean = false,
        maxSize: Size? = null,
    ) {
        clearError()
        val device = cameraDevice
            ?: run {
                postError(CameraError.NotOpen("createCaptureSession"))
                return
            }
        val chars = characteristics
            ?: run {
                postError(CameraError.NotOpen("createCaptureSession"))
                return
            }

        tearDownSession()

        this.exposureNanos = exposureNanos
        this.iso = iso

        // Concurrent multi-camera sessions must conform to the mandatory concurrent
        // stream combinations documented by Camera2 (typically YUV_420_888 at
        // <= 1280x720 per camera). Callers signal that with `forceYuv` / `maxSize`.
        val useRaw = cameraInfo.supportsRaw && !forceYuv
        val format = if (useRaw) ImageFormat.RAW_SENSOR else ImageFormat.YUV_420_888
        val candidates = if (useRaw) cameraInfo.rawSizes else cameraInfo.yuvSizes
        val size = selectOutputSize(candidates, maxSize)
        outputFormat = format
        outputSize = size

        val bgHandler = handler ?: return
        val reader = ImageReader.newInstance(size.width, size.height, format, 3)
        imageReader = reader
        val surface: Surface = reader.surface

        val ready = CompletableDeferred<CameraCaptureSession>()
        sessionReady = ready

        val outputConfig = android.hardware.camera2.params.OutputConfiguration(surface)
        val sessionConfig = android.hardware.camera2.params.SessionConfiguration(
            android.hardware.camera2.params.SessionConfiguration.SESSION_REGULAR,
            listOf(outputConfig),
            Executor { bgHandler.post(it) },
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    ready.complete(session)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    val err = CameraError.SessionConfigureFailed(cameraInfo.id)
                    postError(err)
                    ready.completeExceptionally(CameraException(err))
                }
            },
        )

        device.createCaptureSession(sessionConfig)
    }

    /**
     * Captures one still frame with manual sensor settings. The caller must call
     * [Image.close] on the returned image promptly ([ImageReader] has only 3 slots).
     */
    suspend fun captureImage(): Pair<Image, TotalCaptureResult> {
        clearError()
        val session = sessionReady?.await()
            ?: captureSession
            ?: throw CameraException(CameraError.NotOpen("captureImage"))
        val reader = imageReader
            ?: throw CameraException(CameraError.NotOpen("captureImage"))
        val chars = characteristics
            ?: throw CameraException(CameraError.NotOpen("captureImage"))
        val size = outputSize
            ?: throw CameraException(CameraError.NotOpen("captureImage"))
        val bgHandler = handler
            ?: throw CameraException(CameraError.NotOpen("captureImage"))

        return suspendCancellableCoroutine { cont ->
            val sync = CaptureSync(cont)

            reader.setOnImageAvailableListener({ r ->
                val image = r.acquireNextImage() ?: return@setOnImageAvailableListener
                sync.onImage(image)
            }, bgHandler)

            cont.invokeOnCancellation {
                // Uninstall the listener so no further ghost images can arrive after
                // cancellation, then drain any image that landed before we got here.
                runCatching { reader.setOnImageAvailableListener(null, null) }
                sync.drain()
            }

            val requestBuilder = session.device.createCaptureRequest(
                CameraDevice.TEMPLATE_STILL_CAPTURE,
            )
            applyManualSettings(
                builder = requestBuilder,
                characteristics = chars,
                format = outputFormat,
                size = size,
                exposureNanos = exposureNanos,
                iso = iso,
            )
            requestBuilder.addTarget(reader.surface)

            session.capture(
                requestBuilder.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult,
                    ) {
                        sync.onResult(result)
                    }

                    override fun onCaptureFailed(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        failure: CaptureFailure,
                    ) {
                        val err = CameraError.CaptureFailed(
                            cameraId = cameraId,
                            reason = failure.reason,
                        )
                        postError(err)
                        sync.onFailure(CameraException(err))
                    }
                },
                bgHandler,
            )
        }
    }

    /** Closes [ImageReader], [CameraCaptureSession], [CameraDevice], and the handler thread. */
    fun closeCamera() {
        sessionReady?.cancel()
        sessionReady = null
        tearDownSession()

        try {
            cameraDevice?.close()
        } catch (_: Exception) {
        }
        cameraDevice = null

        handlerThread?.quitSafely()
        handlerThread = null
        handler = null

        cameraManager = null
        characteristics = null
        cameraId = null
        outputSize = null
    }

    private fun tearDownSession() {
        sessionReady?.cancel()
        sessionReady = null

        try {
            captureSession?.abortCaptures()
        } catch (_: Exception) {
        }
        try {
            captureSession?.close()
        } catch (_: Exception) {
        }
        captureSession = null

        try {
            imageReader?.close()
        } catch (_: Exception) {
        }
        imageReader = null
    }

    private fun applyManualSettings(
        builder: CaptureRequest.Builder,
        characteristics: CameraCharacteristics,
        format: Int,
        size: Size,
        exposureNanos: Long,
        iso: Int,
    ) {
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF)
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
        builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
        builder.set(CaptureRequest.HOT_PIXEL_MODE, CaptureRequest.HOT_PIXEL_MODE_OFF)
        builder.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_OFF)
        builder.set(
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF,
        )

        val oisModes = characteristics.get(
            CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION,
        )
        if (oisModes != null && oisModes.isNotEmpty()) {
            builder.set(
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF,
            )
        }

        builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureNanos)
        builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso)

        val configMap = characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP,
        )
        val minFrameDuration = configMap
            ?.getOutputMinFrameDuration(format, size)
            ?: 0L
        val frameDuration = maxOf(minFrameDuration, exposureNanos)
        builder.set(CaptureRequest.SENSOR_FRAME_DURATION, frameDuration)
    }

    private fun postError(error: CameraError) {
        _error.value = error
    }

    private fun clearError() {
        _error.value = null
    }

    private class CaptureSync(
        private val cont: kotlinx.coroutines.CancellableContinuation<Pair<Image, TotalCaptureResult>>,
    ) {
        private val imageRef = AtomicReference<Image?>(null)
        private val resultRef = AtomicReference<TotalCaptureResult?>(null)

        fun onImage(image: Image) {
            if (!cont.isActive) {
                // Continuation is already done (failed or cancelled); this is a
                // ghost frame from the HAL — close it immediately so the ImageReader
                // slot is freed without waiting for a result that will never arrive.
                runCatching { image.close() }
                return
            }
            imageRef.set(image)
            tryComplete()
        }

        fun onResult(result: TotalCaptureResult) {
            resultRef.set(result)
            tryComplete()
        }

        fun onFailure(exception: CameraException) {
            if (cont.isActive) {
                imageRef.getAndSet(null)?.close()
                cont.resumeWithException(exception)
            }
        }

        /**
         * Closes any image sitting in [imageRef] that will never be paired with
         * a result (called from [cont.invokeOnCancellation]).
         */
        fun drain() {
            imageRef.getAndSet(null)?.runCatching { close() }
        }

        private fun tryComplete() {
            val image = imageRef.get() ?: return
            val result = resultRef.get() ?: return
            if (cont.isActive) {
                imageRef.set(null)
                resultRef.set(null)
                cont.resume(image to result)
            } else {
                // Continuation completed between onImage's isActive check and here
                // (tight race with onFailure / cancellation). Close the orphan.
                if (imageRef.compareAndSet(image, null)) {
                    runCatching { image.close() }
                }
            }
        }
    }
}

/** Reported via [CameraController.error]. */
sealed class CameraError {
    abstract val message: String

    data class NotOpen(val operation: String) : CameraError() {
        override val message: String = "Camera not open for $operation"
    }

    data class Disconnected(val cameraId: String) : CameraError() {
        override val message: String = "Camera $cameraId disconnected"
    }

    data class DeviceError(val cameraId: String, val errorCode: Int) : CameraError() {
        override val message: String = "Camera $cameraId error $errorCode"
    }

    data class SessionConfigureFailed(val cameraId: String) : CameraError() {
        override val message: String = "Capture session configure failed for $cameraId"
    }

    data class CaptureFailed(val cameraId: String?, val reason: Int) : CameraError() {
        override val message: String =
            "Capture failed on camera ${cameraId ?: "unknown"} (reason=$reason)"
    }
}

class CameraException(val cameraError: CameraError) : Exception(cameraError.message)

private fun List<Size>.maxByArea(): Size =
    maxBy { it.width.toLong() * it.height }

/**
 * Picks the largest size in [candidates] whose width AND height fit within
 * [maxSize]. Falls back to the overall largest if no candidate satisfies the
 * constraint (e.g. all sensor RAW sizes exceed the concurrent cap).
 */
private fun selectOutputSize(candidates: List<Size>, maxSize: Size?): Size {
    require(candidates.isNotEmpty()) { "No output sizes available" }
    if (maxSize == null) return candidates.maxByArea()
    val filtered = candidates.filter {
        it.width <= maxSize.width && it.height <= maxSize.height
    }
    return if (filtered.isNotEmpty()) filtered.maxByArea() else candidates.maxByArea()
}
