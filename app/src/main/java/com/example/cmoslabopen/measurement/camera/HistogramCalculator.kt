package com.example.cmoslabopen.measurement.camera

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import kotlin.math.sqrt

/**
 * Per-frame histogram and basic statistics for RAW_SENSOR and YUV_420_888 images.
 *
 * The [Image] is read synchronously; the caller must not close it until this
 * returns (typically the processing coroutine in SessionRunner).
 */
object HistogramCalculator {

    const val RAW_BIN_COUNT = 1024
    const val YUV_BIN_COUNT = 256

    data class ImageHistogram(
        val bins: IntArray,
        val numBins: Int,
        val mean: Float,
        val stddev: Float,
        val min: Float,
        val max: Float,
        val blackLevelApplied: Float,
        val whiteLevelApplied: Int,
        val sourceFormat: Int,
    )

    fun compute(
        image: Image,
        characteristics: CameraCharacteristics,
        result: TotalCaptureResult,
    ): ImageHistogram = when (image.format) {
        ImageFormat.RAW_SENSOR -> computeRaw(image, characteristics, result)
        ImageFormat.YUV_420_888 -> computeYuv(image)
        else -> throw IllegalArgumentException("Unsupported image format: ${image.format}")
    }

    private fun computeRaw(
        image: Image,
        characteristics: CameraCharacteristics,
        result: TotalCaptureResult,
    ): ImageHistogram {
        val blackLevel = resolveBlackLevel(characteristics, result)
        val whiteLevel = characteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL)
            ?: throw IllegalStateException("SENSOR_INFO_WHITE_LEVEL unavailable")
        val range = (whiteLevel - blackLevel).coerceAtLeast(1f)

        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val width = image.width
        val height = image.height

        val bins = IntArray(RAW_BIN_COUNT)
        var count = 0L
        var sum = 0.0
        var sumSq = 0.0
        var minNorm = Float.POSITIVE_INFINITY
        var maxNorm = Float.NEGATIVE_INFINITY

        val buffer = plane.buffer.duplicate()
        buffer.order(java.nio.ByteOrder.nativeOrder())

        when (pixelStride) {
            2 -> {
                for (y in 0 until height) {
                    val rowStart = y * rowStride
                    for (x in 0 until width) {
                        buffer.position(rowStart + x * pixelStride)
                        val raw = buffer.short.toInt() and 0xFFFF
                        val norm = normaliseRaw(raw, blackLevel, range)
                        bins[binIndex(norm, RAW_BIN_COUNT)]++
                        count++
                        sum += norm
                        sumSq += norm * norm
                        if (norm < minNorm) minNorm = norm
                        if (norm > maxNorm) maxNorm = norm
                    }
                }
            }
            1 -> {
                for (y in 0 until height) {
                    val rowStart = y * rowStride
                    for (x in 0 until width) {
                        buffer.position(rowStart + x * pixelStride)
                        val raw = buffer.get().toInt() and 0xFF
                        val norm = normaliseRaw(raw, blackLevel, range)
                        bins[binIndex(norm, RAW_BIN_COUNT)]++
                        count++
                        sum += norm
                        sumSq += norm * norm
                        if (norm < minNorm) minNorm = norm
                        if (norm > maxNorm) maxNorm = norm
                    }
                }
            }
            else -> throw IllegalArgumentException("Unsupported RAW pixelStride: $pixelStride")
        }

        val mean = if (count > 0) (sum / count).toFloat() else 0f
        val variance = if (count > 1) (sumSq / count) - (mean * mean) else 0.0
        val stddev = sqrt(variance.coerceAtLeast(0.0)).toFloat()

        return ImageHistogram(
            bins = bins,
            numBins = RAW_BIN_COUNT,
            mean = mean,
            stddev = stddev,
            min = if (count > 0) minNorm else 0f,
            max = if (count > 0) maxNorm else 0f,
            blackLevelApplied = blackLevel,
            whiteLevelApplied = whiteLevel,
            sourceFormat = ImageFormat.RAW_SENSOR,
        )
    }

    private fun computeYuv(image: Image): ImageHistogram {
        val plane = image.planes[0]
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height

        val bins = IntArray(YUV_BIN_COUNT)
        var count = 0L
        var sum = 0.0
        var sumSq = 0.0
        var minY = 255
        var maxY = 0

        val buffer = plane.buffer.duplicate()

        for (y in 0 until height) {
            val rowStart = y * rowStride
            for (x in 0 until width) {
                buffer.position(rowStart + x * pixelStride)
                val yVal = buffer.get().toInt() and 0xFF
                bins[yVal]++
                count++
                sum += yVal
                sumSq += yVal.toDouble() * yVal
                if (yVal < minY) minY = yVal
                if (yVal > maxY) maxY = yVal
            }
        }

        val mean = if (count > 0) (sum / count).toFloat() else 0f
        val variance = if (count > 1) (sumSq / count) - (mean * mean) else 0.0
        val stddev = sqrt(variance.coerceAtLeast(0.0)).toFloat()

        return ImageHistogram(
            bins = bins,
            numBins = YUV_BIN_COUNT,
            mean = mean,
            stddev = stddev,
            min = if (count > 0) minY.toFloat() else 0f,
            max = if (count > 0) maxY.toFloat() else 0f,
            blackLevelApplied = 0f,
            whiteLevelApplied = 255,
            sourceFormat = ImageFormat.YUV_420_888,
        )
    }

    private fun resolveBlackLevel(
        characteristics: CameraCharacteristics,
        result: TotalCaptureResult,
    ): Float {
        val dynamic = result.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL)
        if (dynamic != null && dynamic.size >= 4) {
            return dynamic.take(4).average().toFloat()
        }
        val pattern = characteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)
        if (pattern != null) {
            val c00 = pattern.getOffsetForIndex(0, 0)
            val c10 = pattern.getOffsetForIndex(1, 0)
            val c01 = pattern.getOffsetForIndex(0, 1)
            val c11 = pattern.getOffsetForIndex(1, 1)
            return (c00 + c10 + c01 + c11) / 4f
        }
        return 0f
    }

    private fun normaliseRaw(raw: Int, blackLevel: Float, range: Float): Float {
        val corrected = (raw - blackLevel).coerceAtLeast(0f)
        return (corrected / range).coerceIn(0f, 1f)
    }

    private fun binIndex(normalised: Float, numBins: Int): Int {
        val idx = (normalised * numBins).toInt()
        return when {
            idx >= numBins -> numBins - 1
            idx < 0 -> 0
            else -> idx
        }
    }
}
