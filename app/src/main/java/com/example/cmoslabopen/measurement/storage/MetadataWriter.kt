package com.example.cmoslabopen.measurement.storage

import android.os.Build
import com.example.cmoslabopen.measurement.session.ImageMetadata
import com.example.cmoslabopen.measurement.session.SessionConfig
import com.google.gson.Gson
import com.google.gson.stream.JsonWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Streams `session_manifest.json` without blocking the capture pipeline.
 *
 * Architecture:
 * - [writeConfig] opens the manifest file and starts a single dedicated writer
 *   coroutine on [Dispatchers.IO] that serially drains an internal
 *   [Channel.UNLIMITED] channel.
 * - [appendFrame] is a **non-suspending, fire-and-forget** function. It enqueues
 *   the metadata into the channel and returns in nanoseconds. The caller's
 *   coroutine — and the [android.media.Image] buffer — are never held for disk IO.
 * - [finalise] closes the channel, waits for the writer coroutine to drain all
 *   queued entries, then appends the end time + summary and closes the file.
 *
 * Thread safety:
 * - The channel is the sole synchronisation point between producers (many
 *   parallel processing jobs) and the single consumer (the writer coroutine).
 *   No locks are needed; summary stats are accumulated exclusively by the
 *   writer coroutine.
 */
class MetadataWriter(private val sessionDir: File) {

    private val gson = Gson()
    private val manifestFile = File(sessionDir, "session_manifest.json")

    // Created fresh each writeConfig(); closed by finalise().
    @Volatile private var channel: Channel<ImageMetadata>? = null
    private var writerJob: Job? = null
    private var writerScope: CoroutineScope? = null
    private var jsonWriter: JsonWriter? = null

    // Accumulated by the writer coroutine only (single consumer) — no synchronisation needed.
    private var captureCount = 0
    private var temperatureMin: Float? = null
    private var temperatureMax: Float? = null
    private var temperatureSum = 0.0
    // Counts only frames where a valid (non-NaN) temperature was recorded.
    private var temperatureReadingCount = 0

    /**
     * Opens `session_manifest.json`, writes the session header, and starts the
     * writer coroutine. Safe to call again to restart a session.
     */
    suspend fun writeConfig(config: SessionConfig): File = withContext(Dispatchers.IO) {
        tearDownQuietly()

        sessionDir.mkdirs()
        captureCount = 0
        temperatureMin = null
        temperatureMax = null
        temperatureSum = 0.0
        temperatureReadingCount = 0

        val jw = JsonWriter(
            OutputStreamWriter(manifestFile.outputStream(), StandardCharsets.UTF_8),
        ).apply {
            setIndent("  ")
            beginObject()
            name("sessionId").value(config.sessionId)
            name("config")
            gson.toJson(config, SessionConfig::class.java, this)
            name("deviceModel").value(Build.MODEL)
            name("androidVersion").value(Build.VERSION.SDK_INT.toLong())
            name("startTimeMillis").value(System.currentTimeMillis())
            name("images")
            beginArray()
            flush()
        }
        jsonWriter = jw

        val ch = Channel<ImageMetadata>(capacity = Channel.UNLIMITED)
        channel = ch

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        writerScope = scope
        writerJob = scope.launch {
            // Single consumer — serialises all disk writes.
            ch.consumeEach { metadata ->
                gson.toJson(metadata, ImageMetadata::class.java, jw)
                jw.flush()
                // Only this coroutine mutates stats; no synchronisation needed.
                captureCount++
                // NaN means the sensor read failed for this frame; skip it so a
                // single bad reading does not bias min/max/mean statistics.
                if (!metadata.temperatureCelsius.isNaN()) {
                    temperatureMin = minOfNullable(temperatureMin, metadata.temperatureCelsius)
                    temperatureMax = maxOfNullable(temperatureMax, metadata.temperatureCelsius)
                    temperatureSum += metadata.temperatureCelsius
                    temperatureReadingCount++
                }
            }
        }

        manifestFile
    }

    /**
     * Enqueues [metadata] for writing and returns **instantly** — no suspension,
     * no disk IO on the caller's thread.
     *
     * With [Channel.UNLIMITED], [Channel.trySend] always succeeds while the
     * channel is open. If the channel is closed (session already finalised), the
     * entry is silently dropped.
     */
    fun appendFrame(metadata: ImageMetadata) {
        channel?.trySend(metadata)
    }

    /**
     * Closes the queue, waits for every enqueued entry to be written, then
     * appends the end-time and summary and closes the file.
     */
    suspend fun finalise() {
        channel?.close()
        writerJob?.join() // drain: waits until consumeEach finishes

        withContext(Dispatchers.IO) {
            val jw = jsonWriter ?: return@withContext
            val endTime = System.currentTimeMillis()

            jw.endArray()
            jw.name("endTimeMillis").value(endTime)
            jw.name("summary")
            jw.beginObject()
            jw.name("captureCount").value(captureCount.toLong())
            writeNullableFloat(jw, "temperatureMin", temperatureMin)
            writeNullableFloat(jw, "temperatureMax", temperatureMax)
            writeNullableFloat(
                jw,
                "temperatureMean",
                if (temperatureReadingCount > 0) (temperatureSum / temperatureReadingCount).toFloat() else null,
            )
            jw.endObject()
            jw.endObject()
            jw.close()
            jsonWriter = null
        }

        writerScope?.cancel()
        writerScope = null
        writerJob = null
        channel = null
    }

    /** Relative path of [file] from the session directory, for metadata records. */
    fun relativePath(file: File?): String? {
        if (file == null) return null
        return runCatching { file.relativeTo(sessionDir).invariantSeparatorsPath }
            .getOrElse { file.path }
    }

    private suspend fun tearDownQuietly() {
        runCatching { channel?.close() }
        runCatching { writerJob?.join() }
        withContext(Dispatchers.IO) { runCatching { jsonWriter?.close() } }
        runCatching { writerScope?.cancel() }
        channel = null
        jsonWriter = null
        writerScope = null
        writerJob = null
    }

    private fun writeNullableFloat(jw: JsonWriter, name: String, value: Float?) {
        jw.name(name)
        if (value == null || value.isNaN()) jw.nullValue() else jw.value(value.toDouble())
    }

    private fun minOfNullable(a: Float?, b: Float): Float = if (a == null || b < a) b else a
    private fun maxOfNullable(a: Float?, b: Float): Float = if (a == null || b > a) b else a
}
