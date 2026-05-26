package com.example.cmoslabopen.measurement.storage

import android.content.Context
import com.example.cmoslabopen.measurement.camera.HistogramCalculator
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Persists a per-frame [HistogramCalculator.ImageHistogram] as JSON and binary
 * files in the session directory.
 *
 * All I/O runs on [Dispatchers.IO].
 */
class HistogramSaver(
    private val context: Context,
    private val sessionId: String,
) {

    private val gson = Gson()

    /**
     * Writes `<cameraId>_<tsNanos>_hist.json` and `<cameraId>_<tsNanos>_hist.bin`.
     *
     * @return [Result.success] with the JSON and binary [File] pair, or
     * [Result.failure] if any write step throws.
     */
    suspend fun saveHistogram(
        histogram: HistogramCalculator.ImageHistogram,
        cameraId: String,
        tsNanos: Long,
    ): Result<Pair<File, File>> = withContext(Dispatchers.IO) {
        runCatching {
            val jsonFile = StoragePaths.histJsonFile(context, sessionId, cameraId, tsNanos)
            val binFile = StoragePaths.histBinFile(context, sessionId, cameraId, tsNanos)

            jsonFile.writeText(gson.toJson(HistogramJson.from(histogram)))
            writeBinary(binFile, histogram)

            jsonFile to binFile
        }
    }

    private fun writeBinary(file: File, histogram: HistogramCalculator.ImageHistogram) {
        FileOutputStream(file).use { fos ->
            DataOutputStream(fos).use { out ->
                out.writeBytes("HIST")
                out.writeInt(histogram.numBins)
                for (i in 0 until histogram.numBins) {
                    out.writeFloat(histogram.bins[i].toFloat())
                }
                out.writeFloat(histogram.mean)
                out.writeFloat(histogram.stddev)
                out.writeFloat(histogram.min)
                out.writeFloat(histogram.max)
            }
        }
    }

    /** Gson-friendly snapshot of [HistogramCalculator.ImageHistogram]. */
    private data class HistogramJson(
        val bins: List<Int>,
        val numBins: Int,
        val mean: Float,
        val stddev: Float,
        val min: Float,
        val max: Float,
        val blackLevelApplied: Float,
        val whiteLevelApplied: Int,
        val sourceFormat: Int,
    ) {
        companion object {
            fun from(h: HistogramCalculator.ImageHistogram) = HistogramJson(
                bins = h.bins.toList(),
                numBins = h.numBins,
                mean = h.mean,
                stddev = h.stddev,
                min = h.min,
                max = h.max,
                blackLevelApplied = h.blackLevelApplied,
                whiteLevelApplied = h.whiteLevelApplied,
                sourceFormat = h.sourceFormat,
            )
        }
    }
}
