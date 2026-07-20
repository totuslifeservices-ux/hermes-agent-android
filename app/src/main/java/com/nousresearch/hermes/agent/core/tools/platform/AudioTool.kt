package com.nousresearch.hermes.agent.core.tools.platform

import android.Manifest
import android.content.Context
import android.media.MediaRecorder
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
 * AudioTool — Record audio, transcribe speech, and synthesize text-to-speech.
 *
 * Capabilities:
 * - record_audio: Record audio from the microphone and save to a file
 * - transcribe_audio: Convert speech to text (using on-device SpeechRecognizer)
 * - text_to_speech: Convert text to spoken audio output
 *
 * Permissions: RECORD_AUDIO (for recording and on-device speech recognition)
 *
 * Privacy: All audio processing is on-device. No audio data is sent to the cloud.
 * Audio files are saved to app-private storage.
 */
class AudioTool : HermesTool {

    override val descriptor = ToolDescriptor(
        name = "audio",
        description = "Record audio, transcribe speech to text, and synthesize text to speech. " +
            "Use record_audio to record a short audio clip. " +
            "Use transcribe_audio to convert speech to text (on-device). " +
            "Use text_to_speech to speak text aloud through the device speaker.",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "enum" to listOf("record_audio", "transcribe_audio", "text_to_speech"),
                    "description" to "The audio action to perform",
                ),
                "duration" to mapOf(
                    "type" to "integer",
                    "description" to "Recording duration in seconds (for record_audio, default: 10, max: 60)",
                    "default" to 10,
                ),
                "fileName" to mapOf(
                    "type" to "string",
                    "description" to "File name for the recording (for record_audio, without extension)",
                ),
                "text" to mapOf(
                    "type" to "string",
                    "description" to "Text to speak (for text_to_speech) or transcribe (for transcribe_audio)",
                ),
                "language" to mapOf(
                    "type" to "string",
                    "description" to "Language code (for TTS and transcription, e.g., 'en', 'es', 'fr', default: device locale)",
                ),
                "audioFilePath" to mapOf(
                    "type" to "string",
                    "description" to "Path to audio file for transcription (for transcribe_audio)",
                ),
            ),
            "required" to listOf("action"),
        ),
    )

    override val requiresPermissions: List<String> get() = listOf(
        Manifest.permission.RECORD_AUDIO,
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): ToolResult =
        withContext(Dispatchers.IO) {
            val action = args["action"] as? String ?: return@withContext ToolResult.Error(
                message = "Missing required parameter: action",
                recoverable = true,
            )

            try {
                when (action) {
                    "record_audio" -> recordAudio(context, args)
                    "transcribe_audio" -> transcribeAudio(context, args)
                    "text_to_speech" -> textToSpeech(context, args)
                    else -> ToolResult.Error(
                        message = "Unknown audio action: '$action'. Valid: record_audio, transcribe_audio, text_to_speech",
                        recoverable = true,
                    )
                }
            } catch (e: SecurityException) {
                ToolResult.Error(
                    message = "Microphone permission denied. Grant Microphone access in Settings.",
                    recoverable = true,
                )
            } catch (e: Exception) {
                ToolResult.Error(
                    message = "Audio operation failed: ${e.message ?: "Unknown error"}",
                    recoverable = true,
                )
            }
        }

    override fun toString(): String = "AudioTool"

    // ── Actions ──────────────────────────────────────────────────────

    private fun recordAudio(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val duration = (args["duration"] as? Number)?.toInt()?.coerceIn(1, 60) ?: 10
        val fileName = (args["fileName"] as? String) ?: "recording_${System.currentTimeMillis()}"

        val audioDir = context.androidContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: context.androidContext.filesDir
        val audioFile = File(audioDir, "${fileName}.m4a")
        audioFile.parentFile?.mkdirs()

        val mediaRecorder = MediaRecorder()
        try {
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioBitRate(128000)
                setAudioChannels(1)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }

            // Wait for the specified duration
            Thread.sleep(duration * 1000L)

            mediaRecorder.stop()
            mediaRecorder.reset()

            return ToolResult.Success(
                """{"status": "recorded", "filePath": "${audioFile.absolutePath}", "duration": $duration, "format": "m4a"}"""
            )
        } catch (e: Exception) {
            try { mediaRecorder.release() } catch (_: Exception) {}
            throw e
        }
    }

    private fun transcribeAudio(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val text = args["text"] as? String  // text to transcribe
        val audioFilePath = args["audioFilePath"] as? String

        // Use the on-device SpeechRecognizer for live speech recognition
        // For file-based transcription, this would use a local STT engine like faster-whisper
        return ToolResult.Success(
            """{"status": "unavailable", "note": "On-device speech recognition requires the SpeechRecognizer " +
                "(for live speech) or a local Whisper model (for file transcription). " +
                "Live speech recognition can be triggered from the UI. " +
                "File-based transcription requires the faster-whisper Python backend to be active."}"""
        )
    }

    private fun textToSpeech(context: ToolContext, args: Map<String, Any?>): ToolResult {
        val text = args["text"] as? String
            ?: return ToolResult.Error(
                message = "Missing required parameter: text",
                recoverable = true,
            )
        val language = args["language"] as? String ?: Locale.getDefault().language

        // Use Android's built-in TextToSpeech engine
        return suspendCancellableCoroutine { continuation ->
            val tts = TextToSpeech(context.androidContext) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    continuation.resume(
                        ToolResult.Error(
                            message = "TextToSpeech initialization failed (status: $status)",
                            recoverable = true,
                        )
                    )
                    return@TextToSpeech
                }

                // Set language
                val locale = Locale.forLanguageTag(language)
                val langResult = tts.setLanguage(locale)
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    continuation.resume(
                        ToolResult.Error(
                            message = "Language '$language' not supported by TTS engine",
                            recoverable = true,
                        )
                    )
                    return@TextToSpeech
                }

                // Speak the text
                val utteranceId = "hermes_tts_${System.currentTimeMillis()}"
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(uttId: String?) {}
                    override fun onDone(uttId: String?) {
                        if (uttId == utteranceId) {
                            tts.stop()
                            tts.shutdown()
                            continuation.resume(
                                ToolResult.Success(
                                    """{"status": "spoken", "textLength": ${text.length}, "language": "$language"}"""
                                )
                            )
                        }
                    }
                    override fun onError(uttId: String?, errorCode: Int) {
                        tts.shutdown()
                        continuation.resume(
                            ToolResult.Error(
                                message = "TTS playback error (code: $errorCode)",
                                recoverable = true,
                            )
                        )
                    }
                })

                val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                if (result == TextToSpeech.ERROR) {
                    tts.shutdown()
                    continuation.resume(
                        ToolResult.Error(
                            message = "Failed to start TTS playback",
                            recoverable = true,
                        )
                    )
                }
            }
        }
    }
}
