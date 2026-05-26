package com.example.cmoslabopen.measurement.storage

import com.example.cmoslabopen.measurement.session.ImageMetadata
import com.example.cmoslabopen.measurement.session.SessionConfig
import java.io.File

/**
 * Writes the session-level metadata (the [SessionConfig] used and a list of
 * per-frame [ImageMetadata] entries) as JSON via Gson into the session folder.
 */
class MetadataWriter(private val sessionDir: File) {

    /** Writes the session configuration to sessionDir/config.json. */
    fun writeConfig(config: SessionConfig): File {
        TODO("Phase 9: Gson().toJson(config) -> sessionDir/config.json")
    }

    /** Appends one frame's metadata to sessionDir/frames.json. */
    fun appendFrame(metadata: ImageMetadata) {
        TODO("Phase 9: stream frame metadata via Gson to sessionDir/frames.json")
    }

    /** Closes any open writers and finalises the metadata files. */
    fun finalise() {
        TODO("Phase 9: flush + close metadata writers")
    }
}
