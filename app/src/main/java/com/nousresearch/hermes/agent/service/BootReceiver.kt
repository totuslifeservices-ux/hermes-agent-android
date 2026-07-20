package com.nousresearch.hermes.agent.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BootReceiver — Restarts the Hermes Gateway Service after device reboot.
 *
 * Registered in AndroidManifest.xml with:
 *   <action android:name="android.intent.action.BOOT_COMPLETED" />
 *
 * The gateway service is a foreground service that maintains the Python
 * backend and WebSocket gateway for the Hermes Agent.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Device boot completed — starting Hermes Gateway Service")
            HermesGatewayService.start(context)
        }
    }
}
