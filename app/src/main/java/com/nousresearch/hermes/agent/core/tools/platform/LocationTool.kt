package com.nousresearch.hermes.agent.core.tools.platform

import android.Manifest
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import com.nousresearch.hermes.agent.core.ToolDescriptor
import com.nousresearch.hermes.agent.core.ToolResult
import com.nousresearch.hermes.agent.core.tools.HermesTool
import com.nousresearch.hermes.agent.core.tools.ToolContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * LocationTool — Get device location and reverse-geocode addresses.
 *
 * Capabilities:
 * - get_location: Get current GPS/WiFi location (lat, lng, altitude, accuracy)
 * - reverse_geocode: Convert lat/lng coordinates to a human-readable address
 *
 * Uses FusedLocationProviderClient (Google Play Services) for location
 * and Android Geocoder for reverse geocoding.
 *
 * Permissions: ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION
 * Privacy: Location data stays on-device. No telemetry.
 */
class LocationTool : HermesTool {

    override val descriptor = ToolDescriptor(
        name = "location",
        description = "Get the device's current location and reverse-geocode coordinates to addresses. " +
            "Use get_location for current position (lat/lng/altitude/accuracy). " +
            "Use reverse_geocode to convert coordinates to a street address.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf("get_location", "reverse_geocode"),
                    "description" to "The location action to perform",
                ),
                "latitude" to mapOf(
                    "type" to "number",
                    "description" to "Latitude for reverse_geocode",
                ),
                "longitude" to mapOf(
                    "type" to "number",
                    "description" to "Longitude for reverse_geocode",
                ),
                "timeoutMs" to mapOf(
                    "type" to "integer",
                    "description" to "Timeout in milliseconds for location fix (default: 10000)",
                    "default" to 10000,
                ),
            ),
            "required" to listOf("action"),
        ),
    )

    override val requiresPermissions: List<String> get() = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.IO) {
            val action = args["action"] as? String ?: return@withContext ToolResult.Error(
                message = "Missing required parameter: action",
                recoverable = true,
            )

            try {
                when (action) {
                    "get_location" -> getLocation(context, args)
                    "reverse_geocode" -> reverseGeocode(context, args)
                    else -> ToolResult.Error(
                        message = "Unknown location action: '$action'. Valid: get_location, reverse_geocode",
                        recoverable = true,
                    )
                }
            } catch (e: SecurityException) {
                ToolResult.Error(
                    message = "Location permission denied. Grant Location access in Settings.",
                    recoverable = true,
                )
            } catch (e: Exception) {
                ToolResult.Error(
                    message = "Location operation failed: ${e.message ?: "Unknown error"}",
                    recoverable = true,
                )
            }
        }

    override fun toString(): String = "LocationTool"

    // ── Actions ──────────────────────────────────────────────────────

    private fun getLocation(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val timeoutMs = (args["timeoutMs"] as? Number)?.toLong()?.coerceIn(1000L, 30000L) ?: 10000L

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context.androidContext)

        // Request a high-accuracy current location
        val locationTask = fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            null
        )

        val location: Location = try {
            Tasks.await(locationTask, timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            // Fallback: try last known location
            val lastLocation = Tasks.await(fusedLocationClient.lastLocation)
                ?: return ToolResult.Error(
                    message = "Unable to get location. Ensure GPS is enabled and location permissions are granted.",
                    recoverable = true,
                )
            lastLocation
        }

        val result = mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "altitude" to location.altitude,
            "accuracy" to location.accuracy,
            "bearing" to location.bearing,
            "speed" to location.speed,
            "provider" to location.provider,
            "time" to location.time,
        )

        return ToolResult.Success(toJson(result))
    }

    private fun reverseGeocode(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val latitude = (args["latitude"] as? Number)?.toDouble()
            ?: return ToolResult.Error(
                message = "Missing required parameter: latitude",
                recoverable = true,
            )
        val longitude = (args["longitude"] as? Number)?.toDouble()
            ?: return ToolResult.Error(
                message = "Missing required parameter: longitude",
                recoverable = true,
            )

        if (latitude < -90 || latitude > 90) {
            return ToolResult.Error(
                message = "Latitude must be between -90 and 90",
                recoverable = true,
            )
        }
        if (longitude < -180 || longitude > 180) {
            return ToolResult.Error(
                message = "Longitude must be between -180 and 180",
                recoverable = true,
            )
        }

        val geocoder = Geocoder(context.androidContext, Locale.getDefault())

        if (!Geocoder.isPresent()) {
            return ToolResult.Error(
                message = "Geocoder is not available on this device",
                recoverable = false,
            )
        }

        val addresses: List<Address> = geocoder.getFromLocation(latitude, longitude, 1)
            ?: emptyList()

        if (addresses.isEmpty()) {
            return ToolResult.Success(
                """{"latitude": $latitude, "longitude": $longitude, "address": null, "note": "No address found for these coordinates"}"""
            )
        }

        val address = addresses[0]
        val addressLines = mutableListOf<String>()
        for (i in 0..address.maxAddressLineIndex) {
            address.getAddressLine(i)?.let { addressLines.add(it) }
        }

        val result = mapOf(
            "latitude" to latitude,
            "longitude" to longitude,
            "address" to addressLines.joinToString(", "),
            "thoroughfare" to (address.thoroughfare ?: ""),
            "subThoroughfare" to (address.subThoroughfare ?: ""),
            "locality" to (address.locality ?: ""),
            "subLocality" to (address.subLocality ?: ""),
            "adminArea" to (address.adminArea ?: ""),
            "subAdminArea" to (address.subAdminArea ?: ""),
            "postalCode" to (address.postalCode ?: ""),
            "countryName" to (address.countryName ?: ""),
            "countryCode" to (address.countryCode ?: ""),
            "featureName" to (address.featureName ?: ""),
        )

        return ToolResult.Success(toJson(result))
    }

    companion object {
        private fun toJson(map: Map<String, Any?>): String {
            val sb = StringBuilder("{")
            map.entries.forEachIndexed { i, (key, value) ->
                if (i > 0) sb.append(", ")
                sb.append("\"$key\": ")
                when (value) {
                    null -> sb.append("null")
                    is String -> sb.append("\"").append(value.replace("\\", "\\\\")
                        .replace("\"", "\\\"").replace("\n", "\\n")).append("\"")
                    is Number -> sb.append(value)
                    is Boolean -> sb.append(value)
                    else -> sb.append("\"").append(value.toString()).append("\"")
                }
            }
            sb.append("}")
            return sb.toString()
        }
    }
}
