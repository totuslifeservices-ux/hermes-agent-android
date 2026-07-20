package com.nousresearch.hermes.agent.core.tools.platform

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.nousresearch.hermes.agent.core.ToolDescriptor
import com.nousresearch.hermes.agent.core.ToolResult
import com.nousresearch.hermes.agent.core.tools.HermesTool
import com.nousresearch.hermes.agent.core.tools.ToolContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * SensorTool — Read data from hardware sensors.
 *
 * Capabilities:
 * - get_sensor_data: Read the latest values from one or more sensors
 *
 * Supported sensors:
 * - accelerometer: Linear acceleration (m/s²) in X, Y, Z
 * - gyroscope: Rotation rate (rad/s) around X, Y, Z
 * - magnetometer: Magnetic field (μT) in X, Y, Z
 * - light: Ambient light level (lux)
 * - barometer: Atmospheric pressure (hPa)
 * - proximity: Proximity (cm, typically 0 = near)
 * - humidity: Ambient relative humidity (%)
 * - temperature: Ambient temperature (°C) if available
 *
 * Uses SensorManager system service. No runtime permissions required
 * for most sensors (no personal data collection).
 *
 * Privacy: Sensor data is only read on explicit request. No telemetry.
 */
class SensorTool : HermesTool {

    override val descriptor = ToolDescriptor(
        name = "sensor",
        description = "Read data from device hardware sensors. " +
            "Use get_sensor_data to read current values from one or more sensors. " +
            "Supported sensors: accelerometer, gyroscope, magnetometer, light, barometer, " +
            "proximity, humidity, temperature. Returns the latest available reading for each sensor.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf("get_sensor_data"),
                    "description" to "The sensor action to perform",
                ),
                "sensors" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "string",
                        "enum" to listOf(
                            "accelerometer", "gyroscope", "magnetometer",
                            "light", "barometer", "proximity", "humidity", "temperature", "all"
                        ),
                    ),
                    "description" to "List of sensors to read. Use ['all'] to read all available sensors.",
                ),
                "timeoutMs" to mapOf(
                    "type" to "integer",
                    "description" to "Max time to wait for sensor readings (default: 2000ms)",
                    "default" to 2000,
                ),
            ),
            "required" to listOf("action"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.Main) {
            val action = args["action"] as? String ?: return@withContext ToolResult.Error(
                message = "Missing required parameter: action",
                recoverable = true,
            )

            try {
                when (action) {
                    "get_sensor_data" -> getSensorData(context, args)
                    else -> ToolResult.Error(
                        message = "Unknown sensor action: '$action'. Valid: get_sensor_data",
                        recoverable = true,
                    )
                }
            } catch (e: Exception) {
                ToolResult.Error(
                    message = "Sensor operation failed: ${e.message ?: "Unknown error"}",
                    recoverable = true,
                )
            }
        }

    override fun toString(): String = "SensorTool"

    // ── Actions ──────────────────────────────────────────────────────

    private suspend fun getSensorData(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val rawSensors = args["sensors"] as? List<*>
        val requestedSensors = rawSensors?.filterIsInstance<String>()
            ?: listOf("all")
        val timeoutMs = (args["timeoutMs"] as? Number)?.toLong()?.coerceIn(500L, 10000L) ?: 2000L

        val sensorManager = context.androidContext
            .getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return ToolResult.Error(
                message = "Sensor service not available on this device",
                recoverable = false,
            )

        val wantAll = "all" in requestedSensors || requestedSensors.isEmpty()
        val sensorTypes = if (wantAll) {
            listOf(
                Sensor.TYPE_ACCELEROMETER to "accelerometer",
                Sensor.TYPE_GYROSCOPE to "gyroscope",
                Sensor.TYPE_MAGNETIC_FIELD to "magnetometer",
                Sensor.TYPE_LIGHT to "light",
                Sensor.TYPE_PRESSURE to "barometer",
                Sensor.TYPE_PROXIMITY to "proximity",
                Sensor.TYPE_RELATIVE_HUMIDITY to "humidity",
                Sensor.TYPE_AMBIENT_TEMPERATURE to "temperature",
            )
        } else {
            requestedSensors.mapNotNull { name ->
                sensorTypeFromName(name)?.let { it to name }
            }
        }

        val results = mutableMapOf<String, Any?>()
        val available = mutableListOf<String>()
        val unavailable = mutableListOf<String>()

        // Check availability first
        for ((type, name) in sensorTypes) {
            if (sensorManager.getDefaultSensor(type) != null) {
                available.add(name)
            } else {
                unavailable.add(name)
            }
        }

        if (available.isEmpty()) {
            return ToolResult.Success(
                """{"available": false, "note": "No requested sensors available on this device", "unavailable": ${toJsonArray(unavailable)}}"""
            )
        }

        // Read each available sensor
        for (sensorName in available) {
            val type = sensorTypeFromName(sensorName)
                ?: continue
            val androidSensor = sensorManager.getDefaultSensor(type)
                ?: continue

            try {
                val reading = readSensor(sensorManager, androidSensor, timeoutMs)
                results[sensorName] = reading
            } catch (e: Exception) {
                results[sensorName] = mapOf("error" to (e.message ?: "Read failed"))
            }
        }

        results["available"] = true
        if (unavailable.isNotEmpty()) {
            results["unavailable"] = unavailable
        }

        return ToolResult.Success(toJson(results))
    }

    // ── Sensor Reading ───────────────────────────────────────────────

    private suspend fun readSensor(
        sensorManager: SensorManager,
        sensor: Sensor,
        timeoutMs: Long,
    ): Map<String, Any?> = suspendCancellableCoroutine { continuation ->
        var latestValues: FloatArray? = null
        var latestTimestamp: Long = 0L
        val startTime = System.currentTimeMillis()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                latestValues = event.values.clone()
                latestTimestamp = event.timestamp
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

        sensorManager.registerListener(
            listener, sensor,
            SensorManager.SENSOR_DELAY_NORMAL,
        )

        // Wait briefly to get a reading
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            sensorManager.unregisterListener(listener)

            val values = latestValues
            if (values != null) {
                val result = mutableMapOf<String, Any?>(
                    "name" to sensor.name,
                    "vendor" to sensor.vendor,
                    "version" to sensor.version,
                    "type" to sensor.stringType,
                    "resolution" to sensor.resolution,
                    "maximumRange" to sensor.maximumRange,
                    "power" to sensor.power,
                    "minDelay" to sensor.minDelay,
                    "timestamp" to latestTimestamp,
                    "accuracy" to sensor.maximumRange,  // placeholder
                )

                // Add values with context-dependent labels
                when (sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        result["values"] = mapOf(
                            "x" to values[0],
                            "y" to values[1],
                            "z" to values[2],
                            "unit" to "m/s²",
                        )
                    }
                    Sensor.TYPE_GYROSCOPE -> {
                        result["values"] = mapOf(
                            "x" to values[0],
                            "y" to values[1],
                            "z" to values[2],
                            "unit" to "rad/s",
                        )
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        result["values"] = mapOf(
                            "x" to values[0],
                            "y" to values[1],
                            "z" to values[2],
                            "unit" to "μT",
                        )
                    }
                    Sensor.TYPE_LIGHT -> {
                        result["values"] = mapOf(
                            "lux" to values[0],
                            "unit" to "lux",
                        )
                    }
                    Sensor.TYPE_PRESSURE -> {
                        result["values"] = mapOf(
                            "pressure" to values[0],
                            "unit" to "hPa",
                        )
                    }
                    Sensor.TYPE_PROXIMITY -> {
                        result["values"] = mapOf(
                            "distance" to values[0],
                            "unit" to "cm",
                            "isNear" to (values[0] < sensor.maximumRange),
                        )
                    }
                    Sensor.TYPE_RELATIVE_HUMIDITY -> {
                        result["values"] = mapOf(
                            "humidity" to values[0],
                            "unit" to "%",
                        )
                    }
                    Sensor.TYPE_AMBIENT_TEMPERATURE -> {
                        result["values"] = mapOf(
                            "temperature" to values[0],
                            "unit" to "°C",
                        )
                    }
                    else -> {
                        result["values"] = values.toList()
                    }
                }

                if (continuation.isActive) {
                    continuation.resume(result)
                }
            } else {
                if (continuation.isActive) {
                    continuation.resume(
                        mapOf(
                            "error" to "No reading available",
                            "name" to sensor.name,
                        )
                    )
                }
            }
        }, minOf(timeoutMs, 1500L)) // Max 1.5 seconds per sensor scan
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun sensorTypeFromName(name: String): Int? = when (name.lowercase()) {
        "accelerometer" -> Sensor.TYPE_ACCELEROMETER
        "gyroscope" -> Sensor.TYPE_GYROSCOPE
        "magnetometer", "magnetic_field" -> Sensor.TYPE_MAGNETIC_FIELD
        "light" -> Sensor.TYPE_LIGHT
        "barometer", "pressure" -> Sensor.TYPE_PRESSURE
        "proximity" -> Sensor.TYPE_PROXIMITY
        "humidity", "relative_humidity" -> Sensor.TYPE_RELATIVE_HUMIDITY
        "temperature", "ambient_temperature" -> Sensor.TYPE_AMBIENT_TEMPERATURE
        else -> null
    }

    companion object {
        private fun toJson(map: Map<String, Any?>): String {
            val sb = StringBuilder("{")
            map.entries.forEachIndexed { i, (key, value) ->
                if (i > 0) sb.append(", ")
                sb.append("\"$key\": ")
                appendValue(sb, value)
            }
            sb.append("}")
            return sb.toString()
        }

        private fun toJsonArray(list: List<String>): String {
            return "[" + list.joinToString(", ") { "\"${it.replace("\"", "\\\"")}\"" } + "]"
        }

        private fun appendValue(sb: StringBuilder, value: Any?) {
            when (value) {
                null -> sb.append("null")
                is String -> sb.append("\"").append(value.replace("\\", "\\\\")
                    .replace("\"", "\\\"").replace("\n", "\\n")).append("\"")
                is Number -> sb.append(value)
                is Boolean -> sb.append(value)
                is List<*> -> {
                    sb.append("[")
                    value.forEachIndexed { i, v ->
                        if (i > 0) sb.append(", ")
                        appendValue(sb, v)
                    }
                    sb.append("]")
                }
                is Map<*, *> -> {
                    sb.append("{")
                    @Suppress("UNCHECKED_CAST")
                    val map = value as Map<String, Any?>
                    map.entries.forEachIndexed { i, (k, v) ->
                        if (i > 0) sb.append(", ")
                        sb.append("\"$k\": ")
                        appendValue(sb, v)
                    }
                    sb.append("}")
                }
                else -> sb.append("\"").append(value.toString()).append("\"")
            }
        }
    }
}
