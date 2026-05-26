package com.example.cmoslabopen.measurement.session

/**
 * Immutable description of one measurement session: which cameras to use,
 * what manual exposure / ISO settings to step through, how many frames per
 * setting, etc. Serialised into the session folder by [MetadataWriter].
 */
data class SessionConfig(
    val sessionId: String,
    val cameraIds: List<String>,
    val exposureTimesNanos: List<Long>,
    val sensitivitiesIso: List<Int>,
    val framesPerSetting: Int,
    val saveDng: Boolean,
    val saveHistogram: Boolean,
) {
    companion object {
        fun default(): SessionConfig {
            TODO("Phase 5: provide sensible default configuration")
        }
    }
}
