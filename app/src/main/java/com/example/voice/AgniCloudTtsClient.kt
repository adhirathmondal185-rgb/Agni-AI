package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private const val CLOUD_TTS_TAG = "AgniCloudTtsClient"
private const val TTS_MODEL_NAME = "gemini-3.1-flash-tts-preview"
private const val CLOUD_TTS_TIMEOUT_MS = 10_000L
private const val FAILURE_COOLDOWN_MS = 5 * 60 * 1000L
private const val MAX_CLOUD_TTS_CHARS = 750

/**
 * Natural neural Female & Male speech synthesizer powered by Firebase AI Logic
 * (`gemini-3.1-flash-tts-preview`). Falls back cleanly to on-device Google TTS
 * whenever offline, timed out, or unavailable.
 */
@OptIn(PublicPreviewAPI::class)
class AgniCloudTtsClient(
    private val appContext: Context
) {
    private val cooldownUntilMs = AtomicLong(0L)

    @Volatile
    private var activeAudioTrack: AudioTrack? = null

    @Volatile
    private var activeMediaPlayer: MediaPlayer? = null

    private val femaleModel by lazy {
        Firebase.ai.generativeModel(
            modelName = TTS_MODEL_NAME,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
                speechConfig = SpeechConfig(voice = Voice("Aoede"))
            }
        )
    }

    private val maleModel by lazy {
        Firebase.ai.generativeModel(
            modelName = TTS_MODEL_NAME,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.AUDIO)
                speechConfig = SpeechConfig(voice = Voice("Charon"))
            }
        )
    }

    fun isAvailableNow(textLength: Int): Boolean {
        if (textLength > MAX_CLOUD_TTS_CHARS) return false
        return System.currentTimeMillis() >= cooldownUntilMs.get()
    }

    fun stopPlayback() {
        val track = activeAudioTrack
        activeAudioTrack = null
        if (track != null) {
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.flush()
                }
                track.stop()
            }
            runCatching { track.release() }
        }

        val player = activeMediaPlayer
        activeMediaPlayer = null
        if (player != null) {
            runCatching {
                if (player.isPlaying) player.stop()
            }
            runCatching { player.release() }
        }
    }

    /**
     * Synthesizes and plays speech for [cleanText].
     * Returns `true` if audio was synthesized and played completely, or `false` if the caller
     * should seamlessly fall back to on-device Android TTS.
     */
    suspend fun speakAndPlay(
        cleanText: String,
        localeTag: String,
        gender: AssistantVoiceGender,
        onPlaybackStarted: () -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailableNow(cleanText.length)) return@withContext false

        val model = when (gender) {
            AssistantVoiceGender.FEMALE -> femaleModel
            AssistantVoiceGender.MALE -> maleModel
        }

        val toneHint = when (gender) {
            AssistantVoiceGender.FEMALE -> "warm, sweet, gentle, clear female assistant voice"
            AssistantVoiceGender.MALE -> "warm, calm, clear male assistant voice"
        }

        val prompt = buildString {
            append("Read the following text aloud verbatim in a ")
            append(toneHint)
            append(" (locale ")
            append(localeTag)
            append("). Do not add, translate, or skip any words:\n\n")
            append(cleanText)
        }

        val audioPart = try {
            val response = withTimeout(CLOUD_TTS_TIMEOUT_MS) {
                model.generateContent(prompt)
            }
            response.candidates
                .firstOrNull()
                ?.content
                ?.parts
                ?.filterIsInstance<InlineDataPart>()
                ?.firstOrNull { part ->
                    part.mimeType.startsWith("audio/", ignoreCase = true) &&
                        part.inlineData.isNotEmpty()
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(CLOUD_TTS_TAG, "Cloud neural TTS unavailable, switching to on-device TTS: ${e.message}")
            cooldownUntilMs.set(System.currentTimeMillis() + FAILURE_COOLDOWN_MS)
            return@withContext false
        }

        if (audioPart == null || audioPart.inlineData.isEmpty()) {
            Log.w(CLOUD_TTS_TAG, "Cloud TTS returned no audio bytes; falling back to device TTS")
            return@withContext false
        }

        currentCoroutineContext().ensureActive()

        val mime = audioPart.mimeType.lowercase()
        val bytes = audioPart.inlineData
        return@withContext try {
            if (mime.contains("pcm") || mime.contains("l16")) {
                val sampleRate = extractSampleRate(mime)
                playPcmBytes(bytes, sampleRate, onPlaybackStarted)
            } else {
                playEncodedAudio(bytes, mime, onPlaybackStarted)
            }
            true
        } catch (e: CancellationException) {
            stopPlayback()
            throw e
        } catch (e: Exception) {
            Log.w(CLOUD_TTS_TAG, "Cloud TTS audio playback failed: ${e.message}")
            stopPlayback()
            false
        }
    }

    private fun extractSampleRate(mimeType: String): Int {
        val match = Regex("rate=(\\d+)").find(mimeType)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(8_000, 48_000) ?: 24_000
    }

    private suspend fun playPcmBytes(
        pcmBytes: ByteArray,
        sampleRate: Int,
        onPlaybackStarted: () -> Unit
    ) {
        stopPlayback()
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        activeAudioTrack = audioTrack
        try {
            audioTrack.play()
            onPlaybackStarted()

            var offset = 0
            val chunkSize = 4096
            while (offset < pcmBytes.size && activeAudioTrack === audioTrack) {
                currentCoroutineContext().ensureActive()
                val toWrite = minOf(chunkSize, pcmBytes.size - offset)
                val written = audioTrack.write(pcmBytes, offset, toWrite, AudioTrack.WRITE_BLOCKING)
                if (written <= 0) break
                offset += written
            }
            if (activeAudioTrack === audioTrack) {
                // Allow trailing audio buffer to finish draining cleanly
                val tailMs = ((minBuf.toLong() * 1000L) / (sampleRate * 2L)).coerceIn(80L, 280L)
                kotlinx.coroutines.delay(tailMs)
            }
        } finally {
            if (activeAudioTrack === audioTrack) {
                activeAudioTrack = null
            }
            runCatching {
                audioTrack.stop()
                audioTrack.release()
            }
        }
    }

    private suspend fun playEncodedAudio(
        bytes: ByteArray,
        mimeType: String,
        onPlaybackStarted: () -> Unit
    ) {
        stopPlayback()
        val ext = when {
            mimeType.contains("wav") -> "wav"
            mimeType.contains("ogg") -> "ogg"
            mimeType.contains("mp4") || mimeType.contains("m4a") -> "m4a"
            else -> "mp3"
        }
        val tempFile = File.createTempFile("agni_tts_", ".$ext", appContext.cacheDir)
        try {
            FileOutputStream(tempFile).use { it.write(bytes) }
            suspendCancellableCoroutine<Unit> { continuation ->
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setDataSource(tempFile.absolutePath)
                    setOnPreparedListener { mp ->
                        onPlaybackStarted()
                        mp.start()
                    }
                    setOnCompletionListener {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                    setOnErrorListener { _, _, _ ->
                        if (continuation.isActive) continuation.resume(Unit)
                        true
                    }
                }
                activeMediaPlayer = player
                continuation.invokeOnCancellation {
                    stopPlayback()
                }
                player.prepareAsync()
            }
        } finally {
            stopPlayback()
            runCatching { tempFile.delete() }
        }
    }
}
