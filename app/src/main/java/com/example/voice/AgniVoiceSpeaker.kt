package com.example.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale
import java.util.UUID

private const val TAG = "AgniVoiceSpeaker"

enum class AssistantVoiceGender(
    val key: String,
    val englishLabel: String,
    val bengaliLabel: String
) {
    FEMALE("female", "Female", "মেয়ে"),
    MALE("male", "Male", "ছেলে");

    companion object {
        fun fromKey(key: String?): AssistantVoiceGender =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: FEMALE
    }
}

/**
 * Real on-device multilingual Text-to-Speech engine supporting distinct Male and Female
 * assistant voices across Bengali, Hindi, English, and all supported Indian languages.
 */
class AgniVoiceSpeaker(
    context: Context,
    private val onSpeakingStateChanged: (isSpeaking: Boolean, activeMessageId: String?) -> Unit,
    private val onSpeechCompleted: (messageId: String?) -> Unit = {},
    private val onError: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var activeMessageId: String? = null
    private var pendingSpeak: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post {
                            onSpeakingStateChanged(true, activeMessageId)
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId?.endsWith("_last") == true) {
                            mainHandler.post {
                                val finishedId = activeMessageId
                                activeMessageId = null
                                onSpeakingStateChanged(false, null)
                                onSpeechCompleted(finishedId)
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post {
                            activeMessageId = null
                            onSpeakingStateChanged(false, null)
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        Log.w(TAG, "TTS utterance error code=$errorCode")
                        mainHandler.post {
                            activeMessageId = null
                            onSpeakingStateChanged(false, null)
                        }
                    }
                })
                pendingSpeak?.invoke()
                pendingSpeak = null
            } else {
                Log.w(TAG, "TextToSpeech initialization failed with status=$status")
                mainHandler.post {
                    onError("Device text-to-speech engine could not start. Check Google Speech Services in phone settings.")
                }
            }
        }
    }

    fun speak(
        rawText: String,
        localeTag: String,
        gender: AssistantVoiceGender,
        messageId: String? = null
    ) {
        val cleanText = sanitizeForSpeech(rawText)
        if (cleanText.isBlank()) return

        if (!isReady) {
            pendingSpeak = { speak(rawText, localeTag, gender, messageId) }
            return
        }

        val engine = tts ?: return
        engine.stop()
        activeMessageId = messageId

        val targetLocale = resolveLocale(localeTag)
        configureVoiceProfile(engine, targetLocale, gender)

        val maxLen = TextToSpeech.getMaxSpeechInputLength().coerceIn(500, 3500)
        val chunks = splitIntoSpeechChunks(cleanText, maxLen)
        val batchId = UUID.randomUUID().toString()

        chunks.forEachIndexed { index, chunk ->
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val suffix = if (index == chunks.lastIndex) "_last" else "_$index"
            engine.speak(chunk, queueMode, null, "$batchId$suffix")
        }
        mainHandler.post {
            onSpeakingStateChanged(true, activeMessageId)
        }
    }

    fun previewVoice(gender: AssistantVoiceGender, localeTag: String) {
        val sample = when {
            localeTag.startsWith("bn", ignoreCase = true) ->
                if (gender == AssistantVoiceGender.MALE) {
                    "নমস্কার! আমি অগ্নি এ আই, আপনার ছেলে ভয়েস অ্যাসিস্ট্যান্ট। বলুন, আজ কীভাবে সাহায্য করতে পারি?"
                } else {
                    "নমস্কার! আমি অগ্নি এ আই, আপনার মেয়ে ভয়েস অ্যাসিস্ট্যান্ট। বলুন, আজ কীভাবে সাহায্য করতে পারি?"
                }
            localeTag.startsWith("hi", ignoreCase = true) ->
                if (gender == AssistantVoiceGender.MALE) {
                    "नमस्ते! मैं अग्नि एआई हूँ। बताइए, मैं आपकी कैसे मदद कर सकता हूँ?"
                } else {
                    "नमस्ते! मैं अग्नि एआई हूँ। बताइए, मैं आपकी कैसे मदद कर सकती हूँ?"
                }
            else ->
                if (gender == AssistantVoiceGender.MALE) {
                    "Hello! I am Agni AI, your male voice assistant. Ask me anything."
                } else {
                    "Hello! I am Agni AI, your female voice assistant. Ask me anything."
                }
        }
        speak(
            rawText = sample,
            localeTag = localeTag,
            gender = gender,
            messageId = "voice_preview_${gender.key}"
        )
    }

    fun stop() {
        pendingSpeak = null
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        activeMessageId = null
        mainHandler.post {
            onSpeakingStateChanged(false, null)
        }
    }

    fun shutdown() {
        pendingSpeak = null
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        } finally {
            tts = null
            isReady = false
            activeMessageId = null
        }
    }

    private fun resolveLocale(localeTag: String): Locale {
        val parsed = Locale.forLanguageTag(localeTag)
        return if (parsed.language.isNotBlank()) parsed else Locale("bn", "IN")
    }

    private fun configureVoiceProfile(
        engine: TextToSpeech,
        targetLocale: Locale,
        gender: AssistantVoiceGender
    ) {
        val langResult = engine.setLanguage(targetLocale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallbackLang = Locale(targetLocale.language)
            val fallbackRes = engine.setLanguage(fallbackLang)
            if (fallbackRes == TextToSpeech.LANG_MISSING_DATA || fallbackRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.US)
            }
        }

        val matchingVoices = runCatching {
            engine.voices
                ?.filter { voice ->
                    !voice.isNetworkConnectionRequired &&
                        voice.locale?.language.equals(targetLocale.language, ignoreCase = true)
                }
                ?.sortedBy { it.name }
                .orEmpty()
        }.getOrDefault(emptyList())

        val allLangVoices = if (matchingVoices.isNotEmpty()) {
            matchingVoices
        } else {
            runCatching {
                engine.voices
                    ?.filter { it.locale?.language.equals(targetLocale.language, ignoreCase = true) }
                    ?.sortedBy { it.name }
                    .orEmpty()
            }.getOrDefault(emptyList())
        }

        val chosenVoice: Voice? = selectVoiceForGender(allLangVoices, gender)
        var usedDistinctGenderVoice = false
        if (chosenVoice != null) {
            runCatching {
                engine.voice = chosenVoice
                usedDistinctGenderVoice = allLangVoices.size >= 2
            }
        }

        when (gender) {
            AssistantVoiceGender.FEMALE -> {
                engine.setPitch(if (usedDistinctGenderVoice) 1.05f else 1.18f)
                engine.setSpeechRate(0.98f)
            }
            AssistantVoiceGender.MALE -> {
                engine.setPitch(if (usedDistinctGenderVoice) 0.85f else 0.72f)
                engine.setSpeechRate(0.95f)
            }
        }
    }

    private fun selectVoiceForGender(
        voices: List<Voice>,
        gender: AssistantVoiceGender
    ): Voice? {
        if (voices.isEmpty()) return null
        if (voices.size == 1) return voices.first()

        // Google Android Speech Services voice code heuristics + explicit keywords
        val femaleHints = listOf("female", "woman", "girl", "-f-", "bnf", "bnx", "hif", "enf", "tpf", "sfg", "iob", "sfb")
        val maleHints = listOf("male", "man", "boy", "-m-", "bnm", "bnd", "him", "hid", "enm", "end", "iog", "sfg_m", "tpd", "iom")

        return when (gender) {
            AssistantVoiceGender.FEMALE -> {
                voices.firstOrNull { v ->
                    val n = v.name.lowercase(Locale.ROOT)
                    femaleHints.any { n.contains(it) } && !n.contains("male")
                } ?: voices.first()
            }
            AssistantVoiceGender.MALE -> {
                voices.firstOrNull { v ->
                    val n = v.name.lowercase(Locale.ROOT)
                    maleHints.any { n.contains(it) } && !n.contains("female")
                } ?: voices.last()
            }
        }
    }

    private fun sanitizeForSpeech(raw: String): String {
        return raw
            .replace(Regex("```[\\s\\S]*?```"), " Code block. ")
            .replace(Regex("`([^`]*)`"), "$1")
            .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), "")
            .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s+"), "")
            .replace(Regex("(?m)^\\s*[-*•]+\\s+"), "")
            .replace(Regex("[*_~>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun splitIntoSpeechChunks(text: String, maxLen: Int): List<String> {
        if (text.length <= maxLen) return listOf(text)
        val chunks = mutableListOf<String>()
        var remaining = text
        while (remaining.length > maxLen) {
            val slice = remaining.substring(0, maxLen)
            val breakIdx = listOf(
                slice.lastIndexOf('।'),
                slice.lastIndexOf('.'),
                slice.lastIndexOf('?'),
                slice.lastIndexOf('!'),
                slice.lastIndexOf('\n'),
                slice.lastIndexOf(' ')
            ).maxOrNull()?.takeIf { it > maxLen / 3 } ?: maxLen

            chunks.add(remaining.substring(0, breakIdx).trim())
            remaining = remaining.substring(breakIdx).trim()
        }
        if (remaining.isNotBlank()) {
            chunks.add(remaining)
        }
        return chunks
    }
}
