package com.example.cmoslabopen.measurement.session

import java.util.UUID

/**
 * Immutable description of one measurement session. Serialised into the session
 * folder by [com.example.cmoslabopen.measurement.storage.MetadataWriter].
 */
data class SessionConfig(
    val selectedCameraIds: List<String>,
    val outputMode: OutputMode,
    val exposureTimeNanos: Long,
    val iso: Int,
    val measurementDurationSeconds: Int,
    val captureIntervalSeconds: Float,
    val sessionId: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
)

enum class OutputMode {
    DNG,
    HISTOGRAM,
}
