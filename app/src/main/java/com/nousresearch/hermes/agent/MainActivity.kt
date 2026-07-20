package com.nousresearch.hermes.agent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.nousresearch.hermes.agent.ui.HermesNavHost
import com.nousresearch.hermes.agent.ui.SplashScreen
import com.nousresearch.hermes.agent.ui.themes.HermesTheme

/**
 * MainActivity — The Hermes Agent chat surface.
 *
 * Sets up:
 * - Edge-to-edge display
 * - Material 3 theming with navy/dark palette
 * - Compose Navigation (NavHost with bottom nav)
 * - Deep links (hermes-agent://chat/...)
 * - Runtime permissions on first launch
 *
 * Ecclesiastes 3:1 NLT:
 * "For everything there is a season, a time for every activity under heaven."
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_FIRST_LAUNCH = "first_launch_complete"
    }

    // Permission request launcher for runtime permissions
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        permissions.forEach { (permission, granted) ->
            Log.d(TAG, "Permission $permission: ${if (granted) "granted" else "denied"}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val isFirstLaunch = !getSharedPreferences("hermes_prefs", MODE_PRIVATE)
            .getBoolean(PREFS_FIRST_LAUNCH, false)

        setContent {
            HermesTheme {
                var showSplash by remember { mutableStateOf(isFirstLaunch) }

                if (showSplash) {
                    SplashScreen(
                        onInitialized = {
                            showSplash = false
                            // Mark first launch as complete
                            getSharedPreferences("hermes_prefs", MODE_PRIVATE)
                                .edit()
                                .putBoolean(PREFS_FIRST_LAUNCH, true)
                                .apply()
                            // Request runtime permissions after splash
                            requestRuntimePermissions()
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    HermesNavHost(
                        showBottomBar = true,
                        onNavigateToChat = { sessionId ->
                            // Handle deep link navigation
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Handle deep links that started the activity
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    // ── Deep Link Handling ──────────────────────────────────────────

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "hermes-agent" && data.host == "chat") {
            val sessionId = data.pathSegments.firstOrNull()
            if (sessionId != null) {
                Log.i(TAG, "Deep link: loading session $sessionId")
                // The NavHost will handle this via Navigation Compose
            }
        }
    }

    // ── Runtime Permissions ─────────────────────────────────────────

    private fun requestRuntimePermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Microphone (required for voice input)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            val policy = (application as HermesApplication).policy
            if (policy.requestMicrophoneOnFirstLaunch) {
                permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
            }
        }

        // Notifications (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                val policy = (application as HermesApplication).policy
                if (policy.requestNotificationOnFirstLaunch) {
                    permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
