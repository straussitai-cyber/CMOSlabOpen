package com.example.cmoslabopen.measurement.thermal

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Monitors the system thermal status via [android.os.PowerManager]
 * so a long capture run can throttle, pause, or abort before the device
 * starts skipping frames or shutting cameras down.
 */
class ThermalMonitor(private val context: Context) {

    enum class Level { Nominal, Light, Moderate, Severe, Critical, Emergency, Shutdown }

    /** Hot flow of thermal level updates, starting with the current level. */
    val level: Flow<Level>
        get() = TODO("Phase 10: wrap PowerManager.addThermalStatusListener as callbackFlow")

    /** Returns the current synchronous thermal level. */
    fun currentLevel(): Level {
        TODO("Phase 10: read PowerManager.currentThermalStatus")
    }
}
