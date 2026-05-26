package com.example.cmoslabopen.measurement.session

/**
 * Per-frame metadata captured from the Camera2 [android.hardware.camera2.TotalCaptureResult]
 * and packaged for serialisation into the session folder.
 */
data class ImageMetadata(
    val cameraId: String,
    val frameIndex: Int,
    val timestampNanos: Long,
    val exposureTimeNanos: Long,
    val sensitivityIso: Int,
    val frameDurationNanos: Long,
    val sensorTemperatureC: Float?,
    val blackLevel: IntArray,
    val whiteLevel: Int,
) {
    companion object {
        fun fromCaptureResult(
            result: android.hardware.camera2.TotalCaptureResult,
            cameraId: String,
            frameIndex: Int,
        ): ImageMetadata {
            TODO("Phase 7: extract fields from TotalCaptureResult")
        }
    }
}
