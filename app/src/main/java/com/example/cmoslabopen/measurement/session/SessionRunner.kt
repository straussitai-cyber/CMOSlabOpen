package com.example.cmoslabopen.measurement.session

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Drives a measurement session end-to-end: opens the selected cameras,
 * sweeps through exposure / ISO settings, captures the configured number
 * of frames, hands each [android.media.Image] to the processors and
 * storage layer, and emits progress via [progress].
 */
class SessionRunner(
    private val context: Context,
    private val config: SessionConfig,
) {

    sealed interface Progress {
        data class Started(val totalFrames: Int) : Progress
        data class Frame(val frameIndex: Int, val totalFrames: Int) : Progress
        data class Failed(val cause: Throwable) : Progress
        data object Done : Progress
    }

    /** Hot flow of per-frame progress updates. */
    val progress: Flow<Progress>
        get() = TODO("Phase 6: expose progress as a MutableSharedFlow")

    /** Runs the full session; suspends until completion or failure. */
    suspend fun run() {
        TODO("Phase 6: orchestrate cameras + storage + thermal monitoring")
    }

    /** Cancels an in-flight session and tears down all resources. */
    suspend fun cancel() {
        TODO("Phase 6: cancel coroutines and close cameras")
    }
}
