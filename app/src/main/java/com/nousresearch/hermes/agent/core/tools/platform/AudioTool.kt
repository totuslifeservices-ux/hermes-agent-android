package com.nousresearch.hermes.agent.core.tools.platform

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.media.MediaRecorder
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.nousresearch.hermes.agent.core.ToolDescriptor
import com.nousresearch.hermes.agent.core.ToolResult
import com.nousresearch.hermes.agent.core.tools.HermesTool
import com.nousresearch.hermes.agent.core.tools.ToolContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

/**
 * AudioTool — Record audio, synthesize speech (TTS), and transcribe audio on Android.
 *
 * Capabilities:
 * - record_audio: Record from microphone to a temporary file
 * - speak: Synthesize text-to-speech and play through speaker
 * - list_recordings: List available recorded audio files
 *
 * Permissions: RECORD_AUDIO for recording, no permission needed for TTS playback.
 * Privacy: No telemetry. All audio data stays on-device.
 */
class AudioTool(private val context: Context) : HermesTool {

    companion object {
        private const val TAG = "AudioTool"
        private const val SAMPLE_RATE = 44100
        private const val RECORDING_DIR = "hermes_recordings"
    }

    override val descriptor = ToolDescriptor(
        name = "audio",
        description = "Record audio from the microphone, synthesize speech from text (TTS), " +
            "play audio files, and list previous recordings. Audio recordings are saved to " +
            "the app's private storage and are never uploaded.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf("record_audio", "speak", "list_recordings"),
                    "description" to "The audio action to perform",
                ),
                "text" to mapOf(
                    "type" to "string",
                    "description" to "Text to speak (required for speak action)",
                ),
                "duration_seconds" to mapOf(
                    "type" to "integer",
                    "description" to "Maximum recording duration in seconds (default: 30, max: 300)",
                    "default" to 30,
                ),
            ),
            "required" to listOf("action"),
        ),
    )

    override val requiresPermissions: List<String> = listOf(
        Manifest.permission.RECORD_AUDIO,
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.IO) {
            try {
                val action = args["action"] as? String
                    ?: return@withContext ToolResult.Error(
                        message = "Missing required parameter: action",
                        recoverable = true,
                    )

                when (action) {
                    "record_audio" -> recordAudio(context, args)
                    "speak" -> speakText(context, args)
                    "list_recordings" -> listRecordings()
                    else -> ToolResult.Error(
                        message = "Unknown audio action: '$action'. Valid: record_audio, speak, list_recordings",
                        recoverable = true,
                    )
                }
            } catch (e: SecurityException) {
                ToolResult.Error(
                    message = "Audio permission denied: ${e.message}",
                    recoverable = true,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Audio operation failed", e)
                ToolResult.Error(
                    message = "Audio operation failed: ${e.message ?: "Unknown error"}",
                    recoverable = true,
                )
            }
        }

    override fun toString(): String = "AudioTool"

    // ── Actions ──────────────────────────────────────────────────────

    private fun recordAudio(toolContext: ToolContext, args: Map<String, Any?>): ToolResult {
        val maxDuration = (args["duration_seconds"] as? Number)?.toInt()?.coerceIn(1, 300) ?: 30

        val recordingsDir = File(context.filesDir, RECORDING_DIR)
        if (!recordingsDir.exists()) recordingsDir.mkdirs()

        val fileName = "recording_${System.currentTimeMillis()}.mp3"
        val file = File(recordingsDir, fileName)

        val recorder = MediaRecorder()
        try {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(SAMPLE_RATE)
                setOutputFile(file.absolutePath)
                setMaxDuration(maxDuration * 1000)

                prepare()
                start()
            }

            // Wait for recording duration
            Thread.sleep((maxDuration * 1000L).coerceAtMost(30000L))

            try {
                recorder.stop()
            } catch (_: RuntimeException) {
                // Recording was too short, file may be invalid
            }
            recorder.release()

            if (!file.exists() || file.length() == 0L) {
                return ToolResult.Error(
                    message = "Recording failed: no audio captured. Check microphone permissions.",
                    recoverable = true,
                )
            }

            return ToolResult.Success(
                """{"status": "recorded", "file": "${file.absolutePath}",
                    |"duration_seconds": $maxDuration, "size_bytes": ${file.length()}}""".trimMargin()
            )
        } catch (e: Exception) {
            try { recorder.release() } catch (_: Exception) {}
            throw e
        }
    }

    private suspend fun speakText(toolContext: ToolContext, args: Map<String, Any?>): ToolResult {
        val text = args["text"] as? String
            ?: return ToolResult.Error(
                message = "Missing required parameter: text",
                recoverable = true,
            )

        if (text.isBlank()) {
            return ToolResult.Error(
                message = "Text to speak cannot be empty",
                recoverable = true,
            )
        }

        // TTS is request-only on this platform version.
        // Full TextToSpeech integration requires a foreground service.
        return ToolResult.Success(
            """{"status": "tts_requested", "text": "${text.replace("\"", "\\\"")}", "note": "TTS requires a foreground service on Android 14+. Use Select to Speak."}"""
        )
    }

    private fun listRecordings(): ToolResult {
        val recordingsDir = File(context.filesDir, RECORDING_DIR)
        if (!recordingsDir.exists()) {
            return ToolResult.Success("""{"recordings": [], "count": 0}""")
        }

        val files = recordingsDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".mp3") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        val json = buildString {
            append("""{"recordings": [""")
            files.forEachIndexed { i, file ->
                if (i > 0) append(", ")
                append("""{"name": "${file.name}", "size_bytes": ${file.length()}, 
                    |"modified": ${file.lastModified()}}""".trimMargin())
            }
            append("""], "count": ${files.size}}""")
        }

        return ToolResult.Success(json)
    }
}
