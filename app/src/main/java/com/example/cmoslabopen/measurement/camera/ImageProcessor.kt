package com.example.cmoslabopen.measurement.camera

import android.media.Image

/**
 * Extracts pixel data and computes per-frame statistics (RAW histogram, mean,
 * std, etc.) from a Camera2 [Image]. The [Image] handed in must be closed
 * by the caller immediately after this returns; this class will not retain it.
 */
object ImageProcessor {

    data class RawHistogram(
        /** Bin counts. The bin count and value range depend on the sensor. */
        val bins: IntArray,
        val blackLevel: Int,
        val whiteLevel: Int,
    )

    /**
     * Computes a histogram over the RAW pixels in [image].
     * Normalisation divides by (whiteLevel - blackLevel); never hardcode 65535.
     */
    fun computeRawHistogram(
        image: Image,
        blackLevel: Int,
        whiteLevel: Int,
    ): RawHistogram {
        TODO("Phase 4: walk RAW plane, build histogram, then caller closes image")
    }
}
