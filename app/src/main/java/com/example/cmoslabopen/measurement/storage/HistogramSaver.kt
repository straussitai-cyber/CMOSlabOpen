package com.example.cmoslabopen.measurement.storage

import com.example.cmoslabopen.measurement.camera.ImageProcessor
import java.io.File

/**
 * Persists a per-frame RAW histogram (and basic statistics) as a CSV/JSON
 * file alongside the DNG inside the session folder.
 */
class HistogramSaver(private val sessionDir: File) {

    fun save(
        histogram: ImageProcessor.RawHistogram,
        cameraId: String,
        frameIndex: Int,
    ): File {
        TODO("Phase 8: serialise bins + black/white level to sessionDir/<cameraId>_<frameIndex>_hist.csv")
    }
}
