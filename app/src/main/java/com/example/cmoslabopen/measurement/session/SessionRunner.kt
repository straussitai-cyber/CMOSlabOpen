@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.cmoslabopen.measurement.session

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.os.SystemClock
import com.example.cmoslabopen.measurement.camera.CameraController
import com.example.cmoslabopen.measurement.camera.ImageProcessor
import com.example.cmoslabopen.measurement.storage.DngSaver
import com.example.cmoslabopen.measurement.storage.HistogramSaver
import com.example.cmoslabopen.measurement.storage.MetadataWriter
import com.example.cmoslabopen.measurement.thermal.ThermalMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Drives a real-time measurement session end-to-end.
 *
 * Concurrency model (strict):
 * 1. The capture orchestration coroutine runs on [Dispatchers.Default] and only ever:
 *    - suspends on `delay` for the next interval, or
 *    - suspends on the per-camera [CameraController.captureImage] (which itself
 *      offloads work to its own dedicated Camera2 HandlerThread).
 *    No file IO, no DNG encoding, no histogram math ever runs on this path.
 * 2. As soon as each [Image] is acquired, it is handed off to a dedicated
 *    processing scope backed by `Dispatchers.Default.limitedParallelism(N)`
 *    (a separate SupervisorJob). That coroutine performs the heavy work
 *    (DNG / histogram / metadata write) and is the sole owner of `image.close()`.
 * 3. The capture loop applies *backpressure* via a [Semaphore] sized to
 *    `cameraCount * 2`. The orchestration must acquire one permit per camera
 *    before issuing the next batch; each processing job releases its permit
 *    in `finally`. With `ImageReader.maxImages = 3` per controller this
 *    guarantees the camera always has at least one free slot.
 * 4. Strict timing uses absolute deadlines built from
 *    [SystemClock.elapsedRealtimeNanos] — drift never accumulates.
 * 5. [cancel] sets a flag; the loop exits at the next safe boundary, in-flight
 *    processing is allowed to drain, and the session ends with
 *    [Progress.Cancelled] (never [Progress.Failed]).
 *
 * The runner does NOT own the [CameraController]s; the caller opens them with
 * [CameraController.openCamera] / [CameraController.createCaptureSession]
 * before calling [run] and closes them after.
 */
class SessionRunner(
    private val config: SessionConfig,
    private val controllers: Map<String, CameraController>,
    private val characteristics: Map<String, CameraCharacteristics>,
    private val thermalMonitor: ThermalMonitor,
    private val metadataWriter: MetadataWriter,
    private val dngSaver: DngSaver,
    private val histogramSaver: HistogramSaver,
) {

    sealed interface Progress {
        data class Started(val totalFrames: Int) : Progress
        data class Frame(
            val frameIndex: Int,
            val totalFrames: Int,
            val elapsedNanos: Long,
        ) : Progress

        data class Failed(val cause: Throwable) : Progress
        data object Cancelled : Progress
        data object Done : Progress
    }

    private val _progress = MutableSharedFlow<Progress>(
        replay = 1,
        extraBufferCapacity = 64,
    )
    val progress: SharedFlow<Progress> = _progress.asSharedFlow()

    @Volatile
    private var stopRequested: Boolean = false

    /**
     * Runs the configured measurement session to completion (or cancellation
     * via [cancel]). Suspends until processing has fully drained and the
     * metadata writer has been finalised.
     */
    suspend fun run() {
        require(controllers.isNotEmpty()) {
            "SessionRunner requires at least one CameraController"
        }
        stopRequested = false

        val cameraCount = controllers.size
        val processingParallelism = (cameraCount * 2).coerceAtLeast(2)

        val processingScope = CoroutineScope(
            SupervisorJob() +
                Dispatchers.Default.limitedParallelism(processingParallelism),
        )
        val processingSlots = Semaphore(permits = processingParallelism)

        var failure: Throwable? = null

        try {
            metadataWriter.writeConfig(config)
            captureLoop(processingScope, processingSlots)
        } catch (ce: CancellationException) {
            if (!stopRequested) throw ce
        } catch (t: Throwable) {
            failure = t
        } finally {
            withContext(NonCancellable) {
                processingScope.coroutineContext.job
                    .children
                    .toList()
                    .joinAll()
                runCatching { metadataWriter.finalise() }
                processingScope.cancel()
            }
        }

        val terminal = when {
            failure != null -> Progress.Failed(failure)
            stopRequested -> Progress.Cancelled
            else -> Progress.Done
        }
        _progress.tryEmit(terminal)
    }

    /**
     * Signals the session to stop launching new captures. In-flight captures
     * and processing jobs are allowed to complete; [run] resumes with
     * [Progress.Cancelled] once everything drains.
     */
    fun cancel() {
        stopRequested = true
    }

    private suspend fun captureLoop(
        processingScope: CoroutineScope,
        processingSlots: Semaphore,
    ) = withContext(Dispatchers.Default) {
        val durationSec = config.measurementDurationSeconds.toDouble()
        val intervalSec = config.captureIntervalSeconds.toDouble()
        require(intervalSec > 0.0) { "captureIntervalSeconds must be > 0" }

        val totalCaptures = floor(durationSec / intervalSec).toInt().coerceAtLeast(0)
        val intervalNanos = (intervalSec * 1_000_000_000.0).roundToLong()

        _progress.tryEmit(Progress.Started(totalCaptures))

        val startNs = SystemClock.elapsedRealtimeNanos()

        for (i in 0 until totalCaptures) {
            if (stopRequested) break

            // Absolute deadline — drift never accumulates.
            val targetNs = startNs + i.toLong() * intervalNanos
            val delayMs = (targetNs - SystemClock.elapsedRealtimeNanos()) / 1_000_000L
            if (delayMs > 0) delay(delayMs)

            if (stopRequested) break

            // Backpressure: acquire one permit per camera before issuing the
            // batch. Each successful dispatch transfers permit ownership to a
            // processing job; permits not transferred are released below.
            repeat(controllers.size) { processingSlots.acquire() }
            var unconsumedPermits = controllers.size

            try {
                if (stopRequested) break

                val temperatureC = runCatching {
                    thermalMonitor.readTemperatureCelsius()
                }.getOrNull()

                val deferreds = controllers.entries.map { (cameraId, controller) ->
                    async {
                        val pair = controller.captureImage()
                        cameraId to pair
                    }
                }

                val results: List<Pair<String, Pair<Image, TotalCaptureResult>>> =
                    try {
                        deferreds.awaitAll()
                    } catch (t: Throwable) {
                        // Close any Images that arrived before the failure so
                        // the ImageReader doesn't keep their slots forever.
                        deferreds.forEach { d ->
                            if (d.isCompleted && !d.isCancelled) {
                                runCatching {
                                    d.getCompleted().second.first.close()
                                }
                            }
                        }
                        throw t
                    }

                for ((cameraId, pair) in results) {
                    val (image, captureResult) = pair
                    dispatchProcessing(
                        scope = processingScope,
                        slots = processingSlots,
                        cameraId = cameraId,
                        image = image,
                        captureResult = captureResult,
                        frameIndex = i,
                        sensorTemperatureC = temperatureC,
                    )
                    unconsumedPermits--
                }

                _progress.tryEmit(
                    Progress.Frame(
                        frameIndex = i,
                        totalFrames = totalCaptures,
                        elapsedNanos = SystemClock.elapsedRealtimeNanos() - startNs,
                    ),
                )
            } finally {
                // Release permits whose Images were never dispatched
                // (e.g. capture batch threw or stop was requested).
                repeat(unconsumedPermits) { processingSlots.release() }
            }
        }
    }

    /**
     * Hands a single (image, result) pair off to the processing scope.
     * Returns immediately; the caller never blocks on processing.
     * The processing job is the sole owner of [Image.close] for this image.
     */
    private fun dispatchProcessing(
        scope: CoroutineScope,
        slots: Semaphore,
        cameraId: String,
        image: Image,
        captureResult: TotalCaptureResult,
        frameIndex: Int,
        sensorTemperatureC: Float?,
    ) {
        scope.launch {
            try {
                when (config.outputMode) {
                    OutputMode.DNG -> {
                        val chars = characteristics[cameraId]
                            ?: error("Missing CameraCharacteristics for $cameraId")
                        dngSaver.save(
                            image = image,
                            captureResult = captureResult,
                            characteristics = chars,
                            cameraId = cameraId,
                            frameIndex = frameIndex,
                        )
                    }

                    OutputMode.HISTOGRAM -> {
                        val (blackLevel, whiteLevel) = readBlackWhite(cameraId)
                        val histogram = ImageProcessor.computeRawHistogram(
                            image = image,
                            blackLevel = blackLevel,
                            whiteLevel = whiteLevel,
                        )
                        histogramSaver.save(
                            histogram = histogram,
                            cameraId = cameraId,
                            frameIndex = frameIndex,
                        )
                    }
                }

                val base = ImageMetadata.fromCaptureResult(
                    captureResult,
                    cameraId,
                    frameIndex,
                )
                val metadata = if (sensorTemperatureC != null) {
                    base.copy(sensorTemperatureC = sensorTemperatureC)
                } else {
                    base
                }
                metadataWriter.appendFrame(metadata)
            } catch (ce: CancellationException) {
                // Cancellation never surfaces as a failure; image still closed below.
            } catch (t: Throwable) {
                _progress.tryEmit(Progress.Failed(t))
            } finally {
                runCatching { image.close() }
                slots.release()
            }
        }
    }

    private fun readBlackWhite(cameraId: String): Pair<Int, Int> {
        val chars = characteristics[cameraId]
        val whiteLevel = chars
            ?.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL)
            ?: 0
        val pattern = chars
            ?.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)
        val blackLevel = pattern?.getOffsetForIndex(0, 0) ?: 0
        return blackLevel to whiteLevel
    }
}
