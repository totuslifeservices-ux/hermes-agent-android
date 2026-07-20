package com.nousresearch.hermes.agent.core.tools.platform

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import com.nousresearch.hermes.agent.core.ToolDescriptor
import com.nousresearch.hermes.agent.core.ToolResult
import com.nousresearch.hermes.agent.core.tools.HermesTool
import com.nousresearch.hermes.agent.core.tools.ToolContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * NetworkTool — Check Wi-Fi status, cellular info, and connectivity.
 *
 * Capabilities:
 * - wifi_status: Get current Wi-Fi SSID, signal strength, and IP info
 * - cellular_info: Get mobile network type, signal strength, operator
 * - connectivity_check: Test internet reachability to a host
 *
 * Uses ConnectivityManager, WifiManager, and direct socket connections.
 *
 * Permissions: ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE
 * Privacy: No telemetry. Network info stays on-device.
 */
class NetworkTool : HermesTool {

    override val descriptor = ToolDescriptor(
        name = "network",
        description = "Check Wi-Fi status, cellular network info, and internet connectivity. " +
            "Use wifi_status to get current Wi-Fi SSID, signal strength, and IP. " +
            "Use cellular_info to get mobile network type, operator, and signal. " +
            "Use connectivity_check to test if a specific host is reachable.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf("wifi_status", "cellular_info", "connectivity_check"),
                    "description" to "The network action to perform",
                ),
                "host" to mapOf(
                    "type" to "string",
                    "description" to "Host to ping for connectivity_check (default: '8.8.8.8')",
                ),
                "port" to mapOf(
                    "type" to "integer",
                    "description" to "Port to test for connectivity_check (default: 53, DNS)",
                    "default" to 53,
                ),
                "timeout" to mapOf(
                    "type" to "integer",
                    "description" to "Timeout in seconds for connectivity_check (default: 5)",
                    "default" to 5,
                ),
            ),
            "required" to listOf("action"),
        ),
    )

    override val requiresPermissions: List<String> get() = listOf(
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_WIFI_STATE,
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.IO) {
            val action = args["action"] as? String ?: return@withContext ToolResult.Error(
                message = "Missing required parameter: action",
                recoverable = true,
            )

            try {
                when (action) {
                    "wifi_status" -> wifiStatus(context)
                    "cellular_info" -> cellularInfo(context)
                    "connectivity_check" -> connectivityCheck(context, args)
                    else -> ToolResult.Error(
                        message = "Unknown network action: '$action'. Valid: wifi_status, cellular_info, connectivity_check",
                        recoverable = true,
                    )
                }
            } catch (e: SecurityException) {
                ToolResult.Error(
                    message = "Network permission denied. Grant Network access in Settings.",
                    recoverable = true,
                )
            } catch (e: Exception) {
                ToolResult.Error(
                    message = "Network operation failed: ${e.message ?: "Unknown error"}",
                    recoverable = true,
                )
            }
        }

    override fun toString(): String = "NetworkTool"

    // ── Actions ──────────────────────────────────────────────────────

    private fun wifiStatus(context: ToolContext): ToolResult {
        val connectivityManager = context.androidContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val wifiManager = context.androidContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager

        val network = connectivityManager.activeNetwork
        val caps = network?.let { connectivityManager.getNetworkCapabilities(it) }

        val hasWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val hasCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val hasEthernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        val hasVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val isMetered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
        val isInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val signalStrength = caps?.getSignalStrength()

        var ssid: String? = null
        var bssid: String? = null
        var rssi: Int? = null
        var linkSpeed: Int? = null
        var ipAddress: Int? = null

        if (hasWifi && wifiManager != null) {
            try {
                val wifiInfo = wifiManager.connectionInfo
                ssid = wifiInfo.ssid?.removeSurrounding("\"")
                bssid = wifiInfo.bssid
                rssi = wifiInfo.rssi
                linkSpeed = wifiInfo.linkSpeed
                @Suppress("DEPRECATION")
                ipAddress = wifiInfo.ipAddress
            } catch (_: SecurityException) {
                // Wifi info requires location permission on some devices
            }
        }

        val result = mapOf(
            "isConnected" to (network != null),
            "hasWifi" to hasWifi,
            "hasCellular" to hasCellular,
            "hasEthernet" to hasEthernet,
            "hasVpn" to hasVpn,
            "isMetered" to isMetered,
            "hasInternet" to isInternet,
            "signalStrength" to signalStrength,
            "wifi" to mapOf(
                "ssid" to ssid,
                "bssid" to bssid,
                "rssi" to rssi,
                "linkSpeed" to linkSpeed,
                "ipAddress" to formatIpAddress(ipAddress ?: 0),
            ),
        )

        return ToolResult.Success(toJson(result))
    }

    private fun cellularInfo(context: ToolContext): ToolResult {
        val telephonyManager = context.androidContext
            .getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        if (telephonyManager == null) {
            return ToolResult.Success(
                """{"available": false, "note": "No telephony service available (tablet or Wi-Fi only device)"}"""
            )
        }

        val result = try {
            mapOf(
                "available" to true,
                "networkOperator" to (telephonyManager.networkOperatorName ?: ""),
                "networkCountry" to (telephonyManager.networkCountryIso ?: ""),
                "simOperator" to (telephonyManager.simOperatorName ?: ""),
                "simCountry" to (telephonyManager.simCountryIso ?: ""),
                "phoneType" to phoneTypeString(telephonyManager.phoneType),
                "networkType" to networkTypeString(telephonyManager.dataNetworkType),
                "isRoaming" to telephonyManager.isNetworkRoaming,
                "simState" to simStateString(telephonyManager.simState),
            )
        } catch (e: SecurityException) {
            mapOf(
                "available" to true,
                "error" to "READ_PHONE_STATE permission required for detailed cellular info",
            )
        }

        return ToolResult.Success(toJson(result))
    }

    private fun connectivityCheck(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val host = args["host"] as? String ?: "8.8.8.8"
        val port = (args["port"] as? Number)?.toInt() ?: 53
        val timeout = (args["timeout"] as? Number)?.toLong()?.coerceIn(1L, 30L) ?: 5L

        val startTime = System.currentTimeMillis()
        val socket = Socket()
        val result = try {
            socket.connect(InetSocketAddress(host, port), (timeout * 1000).toInt())
            val rtt = System.currentTimeMillis() - startTime
            mapOf(
                "reachable" to true,
                "host" to host,
                "port" to port,
                "rttMs" to rtt,
                "localAddress" to socket.localAddress?.hostAddress,
            )
        } catch (e: Exception) {
            mapOf(
                "reachable" to false,
                "host" to host,
                "port" to port,
                "error" to (e.message ?: "Connection failed"),
                "timeoutSeconds" to timeout,
            )
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }

        return ToolResult.Success(toJson(result))
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun formatIpAddress(ip: Int): String {
        return "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
    }

    private fun phoneTypeString(type: Int): String = when (type) {
        TelephonyManager.PHONE_TYPE_NONE -> "none"
        TelephonyManager.PHONE_TYPE_GSM -> "gsm"
        TelephonyManager.PHONE_TYPE_CDMA -> "cdma"
        TelephonyManager.PHONE_TYPE_SIP -> "sip"
        else -> "unknown($type)"
    }

    private fun networkTypeString(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_GPRS -> "gprs"
        TelephonyManager.NETWORK_TYPE_EDGE -> "edge"
        TelephonyManager.NETWORK_TYPE_UMTS -> "umts"
        TelephonyManager.NETWORK_TYPE_HSDPA -> "hsdpa"
        TelephonyManager.NETWORK_TYPE_HSUPA -> "hsupa"
        TelephonyManager.NETWORK_TYPE_HSPA -> "hspa"
        TelephonyManager.NETWORK_TYPE_CDMA -> "cdma"
        TelephonyManager.NETWORK_TYPE_EVDO_0 -> "evdo_0"
        TelephonyManager.NETWORK_TYPE_EVDO_A -> "evdo_a"
        TelephonyManager.NETWORK_TYPE_1xRTT -> "1xrtt"
        TelephonyManager.NETWORK_TYPE_LTE -> "lte"
        TelephonyManager.NETWORK_TYPE_EHRPD -> "ehrpd"
        TelephonyManager.NETWORK_TYPE_HSPAP -> "hspap+"
        TelephonyManager.NETWORK_TYPE_NR -> "5g"
        else -> "unknown($type)"
    }

    private fun simStateString(state: Int): String = when (state) {
        TelephonyManager.SIM_STATE_UNKNOWN -> "unknown"
        TelephonyManager.SIM_STATE_ABSENT -> "absent"
        TelephonyManager.SIM_STATE_PIN_REQUIRED -> "pin_required"
        TelephonyManager.SIM_STATE_PUK_REQUIRED -> "puk_required"
        TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "network_locked"
        TelephonyManager.SIM_STATE_READY -> "ready"
        TelephonyManager.SIM_STATE_NOT_READY -> "not_ready"
        TelephonyManager.SIM_STATE_PERM_DISABLED -> "permanently_disabled"
        TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "io_error"
        TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "restricted"
        else -> "unknown($state)"
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
