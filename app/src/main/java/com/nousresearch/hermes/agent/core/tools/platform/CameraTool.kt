package com.nousresearch.hermes.agent.core.tools.platform

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.nousresearch.hermes.agent.core.ToolDescriptor
import com.nousresearch.hermes.agent.core.ToolResult
import com.nousresearch.hermes.agent.core.tools.HermesTool
import com.nousresearch.hermes.agent.core.tools.ToolContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CameraTool — Capture images using the device camera.
 *
 * Capabilities:
 * - capture_image: Launch the camera app to capture a photo. The photo is saved
 *   to the app's private storage and the file path is returned.
 *
 * This tool uses an Intent to launch the system camera app (ACTION_IMAGE_CAPTURE).
 * For the capture to work, the activity context and activityResultLauncher must
 * be available in ToolContext.
 *
 * Permissions: CAMERA
 * Privacy: Images are saved to app-private storage. No telemetry, no cloud upload.
 */
class CameraTool : HermesTool {

    override val descriptor = ToolDescriptor(
        name = "camera",
        description = "Capture photos using the device camera. " +
            "Use capture_image to take a photo. The image is saved to app storage " +
            "and the file path is returned. Requires CAMERA permission.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf("capture_image"),
                    "description" to "The camera action to perform",
                ),
                "fileName" to mapOf(
                    "type" to "string",
                    "description" to "Optional file name for the captured image (without extension)",
                ),
                "useFrontCamera" to mapOf(
                    "type" to "boolean",
                    "description" to "Whether to prefer front-facing camera (default: false, may not work with all camera apps)",
                ),
            ),
            "required" to listOf("action"),
        ),
    )

    override val requiresConfirmation: Boolean get() = true

    override val requiresPermissions: List<String> get() = listOf(
        Manifest.permission.CAMERA,
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.Main) {
            val action = args["action"] as? String ?: return@withContext ToolResult.Error(
                message = "Missing required parameter: action",
                recoverable = true,
            )

            try {
                when (action) {
                    "capture_image" -> captureImage(context, args)
                    else -> ToolResult.Error(
                        message = "Unknown camera action: '$action'. Valid: capture_image",
                        recoverable = true,
                    )
                }
            } catch (e: SecurityException) {
                ToolResult.Error(
                    message = "Camera permission denied. Grant Camera access in Settings.",
                    recoverable = true,
                )
            } catch (e: Exception) {
                ToolResult.Error(
                    message = "Camera operation failed: ${e.message ?: "Unknown error"}",
                    recoverable = true,
                )
            }
        }

    override fun toString(): String = "CameraTool"

    // ── Actions ──────────────────────────────────────────────────────

    private fun captureImage(context: ToolContext, args: Map<String, Any?>): ToolResult {
        if (!context.hasActivity) {
            return ToolResult.Error(
                message = "Camera capture requires an Activity context. " +
                    "Cannot launch camera intent from background service.",
                recoverable = true,
            )
        }

        val fileName = (args["fileName"] as? String)
            ?: "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}"
        val useFrontCamera = args["useFrontCamera"] as? Boolean ?: false

        // Create the image file in app-private external files directory
        val storageDir = context.androidContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val imageFile = File(storageDir, "${fileName}.jpg")
        imageFile.parentFile?.mkdirs()

        val photoUri: Uri = FileProvider.getUriForFile(
            context.androidContext,
            "${context.androidContext.packageName}.fileprovider",
            imageFile,
        )

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // Hint to use front camera (not all camera apps respect this)
            if (useFrontCamera) {
                putExtra("android.intent.extras.CAMERA_FACING", 1)
            }
        }

        if (intent.resolveActivity(context.resolveContext.packageManager) == null) {
            return ToolResult.Error(
                message = "No camera app found to handle capture request",
                recoverable = true,
            )
        }

        // Use the activity result launcher to start the camera
        val launcher = context.activityResultLauncher
        if (launcher != null) {
            launcher.launch(intent)
            return ToolResult.Success(
                """{"status": "capture_initiated", "filePath": "${imageFile.absolutePath}", "fileName": "${fileName}.jpg"}"""
            )
        }

        // Fallback: start directly if we have the activity context
        context.resolveContext.startActivity(intent)
        return ToolResult.Success(
            """{"status": "capture_initiated", "filePath": "${imageFile.absolutePath}", "fileName": "${fileName}.jpg", "note": "Camera app opened. The result will be available after capture completes."}"""
        )
    }
}
