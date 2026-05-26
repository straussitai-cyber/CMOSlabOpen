package com.example.cmoslabopen.measurement.session

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import com.example.cmoslabopen.measurement.camera.HistogramCalculator

data class ImageMetadata(
    val sessionId: String,
    val cameraId: String,
    val captureIndex: Int,
    val timestampNanos: Long,
    val systemTimeMillis: Long,
    val exposureTimeNanos: Long,
    val iso: Int,
    val frameDurationNanos: Long,
    val frameNumber: Long,
    val rollingShutterSkewNanos: Long?,
    val temperatureCelsius: Float,
    val thermalStatus: Int,
    val temperatureSource: String,
    val outputMode: String,
    val outputFilePath: String?,
    val imageWidth: Int,
    val imageHeight: Int,
    val imageFormat: Int,
    val blackLevel: Float?,
    val whiteLevel: Int?,
    val apertureF: Float?,
    val focalLengthMm: Float?,
    val focusDistance: Float?,
) {
    companion object {
        fun fromCaptureResult(
            sessionId: String,
            cameraId: String,
            captureIndex: Int,
            result: TotalCaptureResult,
            image: Image,
            characteristics: CameraCharacteristics,
            systemTimeMillis: Long,
            temperatureCelsius: Float,
            thermalStatus: Int,
            temperatureSource: String,
            outputMode: String,
            outputFilePath: String?,
            histogram: HistogramCalculator.ImageHistogram? = null,
        ): ImageMetadata {
            val whiteLevel = characteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL)
            val blackLevel = histogram?.blackLevelApplied ?: averageStaticBlackLevel(characteristics)

            return ImageMetadata(
                sessionId = sessionId,
                cameraId = cameraId,
                captureIndex = captureIndex,
                timestampNanos = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: image.timestamp,
                systemTimeMillis = systemTimeMillis,
                exposureTimeNanos = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L,
                iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: 0,
                frameDurationNanos = result.get(CaptureResult.SENSOR_FRAME_DURATION) ?: 0L,
                frameNumber = result.frameNumber,
                rollingShutterSkewNanos = result.get(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW),
                temperatureCelsius = temperatureCelsius,
                thermalStatus = thermalStatus,
                temperatureSource = temperatureSource,
                outputMode = outputMode,
                outputFilePath = outputFilePath,
                imageWidth = image.width,
                imageHeight = image.height,
                imageFormat = image.format,
                blackLevel = blackLevel,
                whiteLevel = histogram?.whiteLevelApplied ?: whiteLevel,
                apertureF = result.get(CaptureResult.LENS_APERTURE),
                focalLengthMm = result.get(CaptureResult.LENS_FOCAL_LENGTH),
                focusDistance = result.get(CaptureResult.LENS_FOCUS_DISTANCE),
            )
        }

        private fun averageStaticBlackLevel(characteristics: CameraCharacteristics): Float? {
            val pattern = characteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)
                ?: return null
            val c00 = pattern.getOffsetForIndex(0, 0)
            val c10 = pattern.getOffsetForIndex(1, 0)
            val c01 = pattern.getOffsetForIndex(0, 1)
            val c11 = pattern.getOffsetForIndex(1, 1)
            return (c00 + c10 + c01 + c11) / 4f
        }
    }
}

data class SessionManifest(
    val sessionId: String,
    val config: SessionConfig,
    val deviceModel: String,
    val androidVersion: Int,
    val startTimeMillis: Long,
    val endTimeMillis: Long?,
    val images: List<ImageMetadata>,
)
