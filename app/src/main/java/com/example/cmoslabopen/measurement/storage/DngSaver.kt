package com.example.cmoslabopen.measurement.storage

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Persists a single captured [Image] to the session directory.
 *
 * - RAW_SENSOR images are written as DNG via [DngCreator].
 * - YUV_420_888 images (non-RAW cameras operating in DNG mode) are written as
 *   raw bytes with a 32-byte binary header; see [YUV_HEADER_SPEC].
 *
 * **Image lifecycle:** this class never calls [Image.close].
 * The caller ([com.example.cmoslabopen.measurement.session.SessionRunner]) owns
 * the image and must close it promptly after this function returns.
 *
 * All I/O runs on [Dispatchers.IO].
 */
class DngSaver(
    private val context: Context,
    private val sessionId: String,
) {

    companion object {
        /**
         * Binary header prepended to every `.yuv` fallback file.
         *
         * ```
         * Bytes  0– 7  magic "RAWMEAS\0"  (8 ASCII bytes, null-terminated)
         * Bytes  8–11  width               Int32, big-endian
         * Bytes 12–15  height              Int32, big-endian
         * Bytes 16–19  format              Int32, big-endian (ImageFormat constant)
         * Bytes 20–27  timestampNanos      Int64, big-endian
         * Bytes 28–31  padding             four zero bytes
         * ```
         * Total: 32 bytes.
         * Following the header: Y plane bytes, then U plane bytes, then V plane bytes,
         * each copied respecting its plane's rowStride.
         */
        const val YUV_HEADER_SPEC: String =
            "Bytes 0-7: magic RAWMEAS\\0 | " +
            "Bytes 8-11: width Int32 BE | " +
            "Bytes 12-15: height Int32 BE | " +
            "Bytes 16-19: format Int32 BE (ImageFormat) | " +
            "Bytes 20-27: timestampNanos Int64 BE | " +
            "Bytes 28-31: padding zeros"

        private val MAGIC = "RAWMEAS\u0000".toByteArray(Charsets.US_ASCII)
        private const val HEADER_SIZE = 32
    }

    /**
     * Saves a RAW_SENSOR [image] as a DNG file, or a YUV_420_888 [image] as a
     * binary `.yuv` file with the [YUV_HEADER_SPEC] header.
     *
     * The timestamp embedded in the filename and header is taken from the image's
     * timestamp plane (or [Image.getTimestamp] for YUV), so filenames are
     * monotonically ordered and match the metadata JSON.
     *
     * @return [Result.success] with the written [File], or [Result.failure] if
     * any I/O or encoding step throws.
     */
    suspend fun saveDng(
        image: Image,
        characteristics: CameraCharacteristics,
        result: TotalCaptureResult,
        cameraId: String,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            when (image.format) {
                ImageFormat.RAW_SENSOR -> saveDngInternal(image, characteristics, result, cameraId)
                else -> saveYuvFallback(image, cameraId)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun saveDngInternal(
        image: Image,
        characteristics: CameraCharacteristics,
        result: TotalCaptureResult,
        cameraId: String,
    ): File {
        val tsNanos = image.timestamp
        val file = StoragePaths.dngFile(context, sessionId, cameraId, tsNanos)
        DngCreator(characteristics, result).use { creator ->
            FileOutputStream(file).use { fos ->
                creator.writeImage(fos, image)
            }
        }
        return file
    }

    private fun saveYuvFallback(image: Image, cameraId: String): File {
        val tsNanos = image.timestamp
        val file = StoragePaths.yuvFile(context, sessionId, cameraId, tsNanos)

        FileOutputStream(file).use { fos ->
            fos.write(buildYuvHeader(image))

            // Write each plane, respecting rowStride so the reader can reconstruct
            // the original layout without any rowStride knowledge baked into filenames.
            for (plane in image.planes) {
                writePlane(fos, plane)
            }
        }
        return file
    }

    private fun buildYuvHeader(image: Image): ByteArray {
        val buf = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        buf.put(MAGIC)                      // bytes  0– 7
        buf.putInt(image.width)             // bytes  8–11
        buf.putInt(image.height)            // bytes 12–15
        buf.putInt(image.format)            // bytes 16–19
        buf.putLong(image.timestamp)        // bytes 20–27
        buf.putInt(0)                       // bytes 28–31 padding
        return buf.array()
    }

    private fun writePlane(fos: FileOutputStream, plane: Image.Plane) {
        val buffer: ByteBuffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val remaining = buffer.remaining()

        // Fast path: buffer is already contiguous — write in one shot.
        if (buffer.hasArray()) {
            fos.write(buffer.array(), buffer.arrayOffset() + buffer.position(), remaining)
            return
        }

        // Slow path: direct ByteBuffer — copy row by row to avoid allocating the
        // entire plane at once (can be several MB for a large sensor).
        val rowBytes = ByteArray(rowStride)
        var bytesWritten = 0
        while (bytesWritten < remaining) {
            val toRead = minOf(rowStride, remaining - bytesWritten)
            buffer.get(rowBytes, 0, toRead)
            fos.write(rowBytes, 0, toRead)
            bytesWritten += toRead
        }
    }
}
