package com.example.cmoslabopen.measurement.storage

import android.content.Context
import java.io.File

/**
 * Canonical path helpers for session storage.
 *
 * All files live under [Context.getExternalFilesDir]. No WRITE_EXTERNAL_STORAGE
 * permission is required on API 30+ (minSdk is 30).
 *
 * Session root: <externalFilesDir>/sessions/<sessionId>/
 */
object StoragePaths {

    /**
     * Returns (and creates) the session directory:
     * `<externalFilesDir>/sessions/<sessionId>/`
     */
    fun sessionDirectory(context: Context, sessionId: String): File =
        File(context.getExternalFilesDir(null), "sessions/$sessionId")
            .also { it.mkdirs() }

    /**
     * `<sessionDir>/<cameraId>_<tsNanos>.dng`
     */
    fun dngFile(context: Context, sessionId: String, cameraId: String, tsNanos: Long): File =
        File(sessionDirectory(context, sessionId), "${cameraId}_${tsNanos}.dng")

    /**
     * `<sessionDir>/<cameraId>_<tsNanos>.yuv`
     */
    fun yuvFile(context: Context, sessionId: String, cameraId: String, tsNanos: Long): File =
        File(sessionDirectory(context, sessionId), "${cameraId}_${tsNanos}.yuv")

    fun histJsonFile(context: Context, sessionId: String, cameraId: String, tsNanos: Long): File =
        File(sessionDirectory(context, sessionId), "${cameraId}_${tsNanos}_hist.json")

    fun histBinFile(context: Context, sessionId: String, cameraId: String, tsNanos: Long): File =
        File(sessionDirectory(context, sessionId), "${cameraId}_${tsNanos}_hist.bin")
}
