package com.example.cmoslabopen.measurement.camera

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.util.Range
import android.util.Size
import android.util.SizeF

/**
 * Discovers cameras via [CameraManager] and extracts the characteristics needed
 * to drive a measurement session. Does not retain an Android [android.content.Context].
 */
class CameraEnumerator(private val cameraManager: CameraManager) {

    data class CameraInfo(
        val id: String,
        val facing: Int,
        val supportsRaw: Boolean,
        val rawSizes: List<Size>,
        val yuvSizes: List<Size>,
        val physicalSizeMm: SizeF?,
        val pixelArraySize: Size?,
        val isoRange: Range<Int>?,
        val exposureRangeNanos: Range<Long>?,
    )

    /** Returns every camera [CameraManager] reports on this device. */
    fun listAllCameras(): List<CameraInfo> =
        cameraManager.cameraIdList.map { id -> buildCameraInfo(id) }

    /** Subset of [listAllCameras] where [CameraInfo.supportsRaw] is true. */
    fun listRawCapableCameras(): List<CameraInfo> =
        listAllCameras().filter { it.supportsRaw }

    /**
     * Camera ID sets that the HAL allows to be opened simultaneously
     * ([CameraManager.getConcurrentCameraIds]).
     */
    fun concurrentSets(): Set<Set<String>> =
        cameraManager.concurrentCameraIds

    /**
     * Whether [ids] can be opened at the same time. A single valid camera ID
     * is always allowed; for two or more IDs, every id must appear together in
     * at least one entry from [concurrentSets].
     */
    fun canOpenConcurrently(ids: Set<String>): Boolean {
        if (ids.isEmpty()) return false
        val knownIds = cameraManager.cameraIdList.toSet()
        if (!knownIds.containsAll(ids)) return false
        if (ids.size == 1) return true
        return concurrentSets().any { concurrent -> concurrent.containsAll(ids) }
    }

    private fun buildCameraInfo(id: String): CameraInfo {
        val characteristics = cameraManager.getCameraCharacteristics(id)
        val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?: intArrayOf()
        val hasRawCapability = capabilities.contains(
            CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW,
        )
        val outputFormats = configMap?.outputFormats ?: intArrayOf()
        val hasRawSensorFormat = outputFormats.contains(ImageFormat.RAW_SENSOR)
        val supportsRaw = hasRawCapability && hasRawSensorFormat

        val rawSizes = if (supportsRaw && configMap != null) {
            configMap.getOutputSizes(ImageFormat.RAW_SENSOR)?.toList() ?: emptyList()
        } else {
            emptyList()
        }

        val yuvSizes = configMap
            ?.getOutputSizes(ImageFormat.YUV_420_888)
            ?.toList()
            ?: emptyList()

        return CameraInfo(
            id = id,
            facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                ?: CameraCharacteristics.LENS_FACING_EXTERNAL,
            supportsRaw = supportsRaw,
            rawSizes = rawSizes,
            yuvSizes = yuvSizes,
            physicalSizeMm = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE),
            pixelArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE),
            isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE),
            exposureRangeNanos = characteristics.get(
                CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE,
            ),
        )
    }

    companion object {
        fun from(cameraManager: CameraManager): CameraEnumerator =
            CameraEnumerator(cameraManager)
    }
}
