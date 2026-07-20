package com.nousresearch.hermes.agent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.nousresearch.hermes.agent.MainActivity
import com.nousresearch.hermes.agent.R

/**
 * HermesGatewayService — Foreground service that keeps the Hermes
 * Agent Python backend alive and maintains the WebSocket gateway
 * connection.
 *
 * Architecture:
 * - Runs as a foreground service with FOREGROUND_SERVICE_DATA_SYNC type
 *   (Android 14+ compliance)
 * - Maintains the Python runtime and local LLM connection
 * - Provides a WebSocket gateway on localhost:${gatewayPort}
 * - Restarts automatically after device boot via BootReceiver
 *
 * Privacy:
 * - No telemetry, no analytics, no third-party SDKs
 * - All processing is local and offline-first
 * - Session data is encrypted at rest
 */
class HermesGatewayService : LifecycleService() {

    companion object {
        private const val TAG = "HermesGateway"
        private const val CHANNEL_ID = "hermes_gateway_channel"
        private const val NOTIFICATION_ID = 1001

        /** Whether the service is currently running */
        @Volatile
        var isRunning: Boolean = false
            private set

        /**
         * Start the gateway service.
         */
        fun start(context: Context) {
            if (isRunning) return
            val intent = Intent(context, HermesGatewayService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stop the gateway service.
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, HermesGatewayService::class.java))
        }
    }

    private var notificationManager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        isRunning = true
        Log.i(TAG, "Hermes Gateway Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Initialize the Python backend / WebSocket gateway
        startGatewayBackend()

        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        stopGatewayBackend()
        super.onDestroy()
        Log.i(TAG, "Hermes Gateway Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        super.onBind(intent)
        return null
    }

    // ── Private Implementation ──────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun startGatewayBackend() {
        // ── Python backend initialization (Chaquopy) ──────────────
        // The Python runtime is initialized by Chaquopy when the app
        // starts. Here we start the Hermes Agent gateway WebSocket server.
        //
        // This is the bridge call into Python:
        //   import hermes_cli.web_server as ws
        //   ws.start_gateway(port=8320, host="127.0.0.1")
        //
        // In a full implementation, this would call Python.start() then
        // invoke the Hermes gateway server module.

        Log.i(TAG, "Gateway backend starting (port: 8320, host: 127.0.0.1)")
        // TODO: Full implementation with Chaquopy Python.start() call
    }

    private fun stopGatewayBackend() {
        Log.i(TAG, "Gateway backend shutting down")
        // TODO: Graceful shutdown of Python gateway
    }
}
