package com.example.cmoslabopen.measurement.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics

/**
 * Discovers the physical/logical cameras on the device and exposes the
 * Camera2 characteristics needed to drive a measurement session
 * (RAW capability, sensor size, exposure / ISO ranges, black/white level, etc.).
 */
class CameraEnumerator(private val context: Context) {

    data class CameraInfo(
        val id: String,
        val characteristics: CameraCharacteristics,
    )

    /** Returns every camera the [CameraManager] reports on this device. */
    fun listAllCameras(): List<CameraInfo> {
        TODO("Phase 1: implement Camera2 enumeration via CameraManager.cameraIdList")
    }

    /** Subset of [listAllCameras] that advertise RAW_SENSOR capability. */
    fun listRawCapableCameras(): List<CameraInfo> {
        TODO("Phase 1: filter on REQUEST_AVAILABLE_CAPABILITIES_RAW")
    }

    /**
     * Camera ID sets that can be opened simultaneously, per
     * [android.hardware.camera2.CameraManager.getConcurrentCameraIds].
     */
    fun listConcurrentCameraSets(): Set<Set<String>> {
        TODO("Phase 1: implement using getConcurrentCameraIds()")
    }
}
