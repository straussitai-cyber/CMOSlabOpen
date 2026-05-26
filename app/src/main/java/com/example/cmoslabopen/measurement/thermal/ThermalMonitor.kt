package com.example.cmoslabopen.measurement.thermal

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Monitors platform thermal status and reads a best-effort temperature in °C
 * for session metadata.
 *
 * minSdk is 30; [PowerManager.addThermalStatusListener] (API 29+) is used
 * without version guards.
 */
class ThermalMonitor(context: Context) {

    private val appContext = context.applicationContext
    private val powerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val _thermalStatus = MutableStateFlow(powerManager.currentThermalStatus)
    val thermalStatus: StateFlow<Int> = _thermalStatus.asStateFlow()

    /** Which source supplied the last [readTemperatureCelsius] value. */
    @Volatile
    var temperatureSource: String = ""
        private set

    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private var started = false

    fun start() {
        if (started) return
        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            _thermalStatus.value = status
        }
        thermalListener = listener
        powerManager.addThermalStatusListener(
            ContextCompat.getMainExecutor(appContext),
            listener,
        )
        _thermalStatus.value = powerManager.currentThermalStatus
        started = true
    }

    fun stop() {
        if (!started) return
        thermalListener?.let { powerManager.removeThermalStatusListener(it) }
        thermalListener = null
        started = false
    }

    /**
     * Reads temperature in °C using the first available source (sensor → sysfs →
     * battery). Updates [temperatureSource] with the winning source id.
     *
     * @throws IllegalStateException if no source returns a usable value.
     */
    suspend fun readTemperatureCelsius(): Float {
        readFromSensor()?.let { return it }
        readFromSysfs()?.let { return it }
        return readFromBattery()
    }

    // -------------------------------------------------------------------------
    // Source 1 — SensorManager (OEM thermal zones)
    // -------------------------------------------------------------------------

    private suspend fun readFromSensor(): Float? {
        val candidates = sensorManager.getSensorList(Sensor.TYPE_ALL)
            .filter { isThermalSensor(it) }
        for (sensor in candidates) {
            val value = readOneShotSensor(sensor) ?: continue
            temperatureSource = "SENSOR/${sensor.name}"
            return value
        }
        return null
    }

    private suspend fun readOneShotSensor(sensor: Sensor): Float? =
        withContext(Dispatchers.Main) {
            withTimeoutOrNull(SENSOR_READ_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val listener = object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent) {
                            if (event.accuracy < SensorManager.SENSOR_STATUS_UNRELIABLE) return
                            sensorManager.unregisterListener(this)
                            if (cont.isActive) {
                                cont.resume(event.values[0])
                            }
                        }

                        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                    }
                    sensorManager.registerListener(
                        listener,
                        sensor,
                        SensorManager.SENSOR_DELAY_FASTEST,
                    )
                    cont.invokeOnCancellation {
                        sensorManager.unregisterListener(listener)
                    }
                }
            }
        }

    private fun isThermalSensor(sensor: Sensor): Boolean {
        if (sensor.type == Sensor.TYPE_AMBIENT_TEMPERATURE) return true
        val name = sensor.name.lowercase()
        if (THERMAL_NAME_KEYWORDS.any { name.contains(it) }) return true
        val stringType = sensor.stringType?.lowercase() ?: return false
        return stringType.contains("temperature")
    }

    // -------------------------------------------------------------------------
    // Source 2 — sysfs thermal zones
    // -------------------------------------------------------------------------

    private suspend fun readFromSysfs(): Float? = withContext(Dispatchers.IO) {
        val thermalRoot = File("/sys/class/thermal")
        if (!thermalRoot.isDirectory) return@withContext null

        data class Zone(val type: String, val tempC: Float)

        val zones = thermalRoot.listFiles { file ->
            file.isDirectory && file.name.startsWith("thermal_zone")
        }?.mapNotNull { zoneDir ->
            val type = readSysfsLine(File(zoneDir, "type")) ?: return@mapNotNull null
            val tempMilli = readSysfsLine(File(zoneDir, "temp"))?.toLongOrNull()
                ?: return@mapNotNull null
            Zone(type.trim(), tempMilli / 1000f)
        } ?: emptyList()

        if (zones.isEmpty()) return@withContext null

        val preferred = zones.filter { zone ->
            val t = zone.type.lowercase()
            SYSFS_PREFERRED_TYPES.any { t.contains(it) }
        }

        val chosen = if (preferred.isNotEmpty()) {
            preferred.maxBy { it.tempC }
        } else {
            zones.maxBy { it.tempC }
        }

        temperatureSource = "SYSFS/${chosen.type}"
        chosen.tempC
    }

    private fun readSysfsLine(file: File): String? =
        runCatching { file.readText().trim() }.getOrNull()

    // -------------------------------------------------------------------------
    // Source 3 — BatteryManager (sticky broadcast)
    // -------------------------------------------------------------------------

    private fun readFromBattery(): Float {
        val intent = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: throw IllegalStateException("BATTERY_CHANGED sticky intent unavailable")

        val tenthsC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        if (tenthsC == Int.MIN_VALUE) {
            throw IllegalStateException("Battery EXTRA_TEMPERATURE unavailable")
        }
        temperatureSource = "BATTERY"
        return tenthsC / 10f
    }

    companion object {
        private const val SENSOR_READ_TIMEOUT_MS = 3_000L

        private val THERMAL_NAME_KEYWORDS = listOf(
            "skin", "battery", "pcb", "board", "msm", "cpu",
        )

        private val SYSFS_PREFERRED_TYPES = listOf(
            "battery", "skin", "tsens_tz_sensor",
        )
    }
}
