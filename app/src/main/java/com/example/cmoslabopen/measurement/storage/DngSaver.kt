package com.example.cmoslabopen.measurement.storage

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import java.io.File

/**
 * Persists a RAW [Image] as a DNG file inside the session folder.
 * The [Image] must be closed by the caller immediately after this returns.
 */
class DngSaver(private val sessionDir: File) {

    /**
     * Writes [image] to a DNG using [android.hardware.camera2.DngCreator].
     * Returns the resulting file.
     */
    fun save(
        image: Image,
        captureResult: TotalCaptureResult,
        characteristics: CameraCharacteristics,
        cameraId: String,
        frameIndex: Int,
    ): File {
        TODO("Phase 8: DngCreator.writeImage to sessionDir/<cameraId>_<frameIndex>.dng")
    }
}
