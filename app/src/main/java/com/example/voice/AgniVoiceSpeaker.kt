package com.example.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

private const val TAG = "AgniVoiceSpeaker"
private const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"

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
 * Hybrid multilingual Male & Sweet Female Assistant Voice Engine:
 * 1. Primary: Natural neural voice via Firebase AI Logic (`Aoede` sweet female / `Charon` male).
 * 2. Fallback: Upgraded Google Android Speech Services TTS with verified Bengali / Hindi / English
 *    Female vs Male voice selection (explicitly preventing male voice codes like `bn-in-x-bin` or
 *    `bn-bd-x-ban` from ever being picked for the Female option).
 */
class AgniVoiceSpeaker(
    context: Context,
    private val onSpeakingStateChanged: (isSpeaking: Boolean, activeMessageId: String?) -> Unit,
    private val onSpeechCompleted: (messageId: String?) -> Unit = {},
    private val onError: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val speakerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cloudTts = AgniCloudTtsClient(appContext)

    private var tts: TextToSpeech? = null
    private var isReady = false
    private var activeMessageId: String? = null
    private var pendingSpeak: (() -> Unit)? = null
    private var activeCloudJob: Job? = null

    init {
        initTtsEngine(preferredEngine = GOOGLE_TTS_PACKAGE)
    }

    private fun initTtsEngine(preferredEngine: String?) {
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                attachUtteranceListener()
                pendingSpeak?.invoke()
                pendingSpeak = null
            } else if (preferredEngine != null) {
                Log.w(TAG, "Preferred TTS engine ($preferredEngine) failed ($status); retrying default engine")
                runCatching { tts?.shutdown() }
                tts = null
                initTtsEngine(preferredEngine = null)
            } else {
                Log.w(TAG, "TextToSpeech initialization failed with status=$status")
                mainHandler.post {
                    onError("Device text-to-speech engine could not start. Check Google Speech Services in phone settings.")
                }
            }
        }

        tts = if (preferredEngine != null) {
            runCatching { TextToSpeech(appContext, listener, preferredEngine) }
                .getOrElse { TextToSpeech(appContext, listener) }
        } else {
            TextToSpeech(appContext, listener)
        }
    }

    private fun attachUtteranceListener() {
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
    }

    fun speak(
        rawText: String,
        localeTag: String,
        gender: AssistantVoiceGender,
        messageId: String? = null
    ) {
        val cleanText = sanitizeForSpeech(rawText)
        if (cleanText.isBlank()) return

        stopInternal(notifyIdle = false)
        activeMessageId = messageId

        if (cloudTts.isAvailableNow(cleanText.length)) {
            mainHandler.post {
                onSpeakingStateChanged(true, activeMessageId)
            }
            activeCloudJob = speakerScope.launch {
                val playedViaCloud = try {
                    cloudTts.speakAndPlay(
                        cleanText = cleanText,
                        localeTag = localeTag,
                        gender = gender,
                        onPlaybackStarted = {
                            mainHandler.post {
                                onSpeakingStateChanged(true, activeMessageId)
                            }
                        }
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }

                if (playedViaCloud) {
                    val finishedId = activeMessageId
                    activeMessageId = null
                    onSpeakingStateChanged(false, null)
                    onSpeechCompleted(finishedId)
                } else {
                    speakWithDeviceTts(cleanText, localeTag, gender, messageId)
                }
            }
        } else {
            speakWithDeviceTts(cleanText, localeTag, gender, messageId)
        }
    }

    private fun speakWithDeviceTts(
        cleanText: String,
        localeTag: String,
        gender: AssistantVoiceGender,
        messageId: String?
    ) {
        if (!isReady) {
            pendingSpeak = { speakWithDeviceTts(cleanText, localeTag, gender, messageId) }
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
                    "নমস্কার! আমি অগ্নি এ আই, আপনার মিষ্টি মেয়ে ভয়েস অ্যাসিস্ট্যান্ট। বলুন, আজ আপনাকে কীভাবে সাহায্য করতে পারি?"
                }
            localeTag.startsWith("hi", ignoreCase = true) ->
                if (gender == AssistantVoiceGender.MALE) {
                    "नमस्ते! मैं अग्नि एआई हूँ। बताइए, मैं आपकी कैसे मदद कर सकता हूँ?"
                } else {
                    "नमस्ते! मैं अग्नि एआई हूँ, आपकी महिला वॉइस असिस्टेंट। बताइए, मैं आपकी कैसे मदद कर सकती हूँ?"
                }
            else ->
                if (gender == AssistantVoiceGender.MALE) {
                    "Hello! I am Agni AI, your male voice assistant. Ask me anything."
                } else {
                    "Hello! I am Agni AI, your female voice assistant. How can I help you today?"
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
        stopInternal(notifyIdle = true)
    }

    private fun stopInternal(notifyIdle: Boolean) {
        pendingSpeak = null
        activeCloudJob?.cancel()
        activeCloudJob = null
        cloudTts.stopPlayback()
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        activeMessageId = null
        if (notifyIdle) {
            mainHandler.post {
                onSpeakingStateChanged(false, null)
            }
        }
    }

    fun shutdown() {
        stopInternal(notifyIdle = false)
        speakerScope.cancel()
        try {
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

    private data class VoiceSelectionResult(
        val voice: Voice?,
        val isVerifiedGenderMatch: Boolean
    )

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

        // Gather ALL installed local AND network voices for this language so HD female voices
        // (such as bn-in-x-bnf-network / bn-in-x-bnx-network) are never discarded when only a
        // male offline voice (such as bn-in-x-bin-local) is cached on the phone.
        val candidateVoices = runCatching {
            engine.voices
                ?.filter { voice ->
                    val notInstalled = voice.features?.contains(
                        TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED
                    ) == true
                    !notInstalled && voice.locale?.language.equals(targetLocale.language, ignoreCase = true)
                }
                .orEmpty()
        }.getOrDefault(emptyList())

        val selection = selectVoiceForGender(candidateVoices, targetLocale, gender)
        if (selection.voice != null) {
            runCatching {
                engine.voice = selection.voice
                Log.d(TAG, "Selected device TTS voice=${selection.voice.name} verified=${selection.isVerifiedGenderMatch} for $gender")
            }
        }

        when (gender) {
            AssistantVoiceGender.FEMALE -> {
                if (selection.isVerifiedGenderMatch) {
                    // Sweet, warm natural female voice register
                    engine.setPitch(1.07f)
                    engine.setSpeechRate(0.96f)
                } else {
                    // Strong feminine pitch lift if the phone literally only has a male offline pack
                    engine.setPitch(1.50f)
                    engine.setSpeechRate(1.02f)
                }
            }
            AssistantVoiceGender.MALE -> {
                if (selection.isVerifiedGenderMatch) {
                    engine.setPitch(0.88f)
                    engine.setSpeechRate(0.95f)
                } else {
                    engine.setPitch(0.72f)
                    engine.setSpeechRate(0.95f)
                }
            }
        }
    }

    private fun selectVoiceForGender(
        voices: List<Voice>,
        targetLocale: Locale,
        gender: AssistantVoiceGender
    ): VoiceSelectionResult {
        if (voices.isEmpty()) return VoiceSelectionResult(null, false)

        val lang = targetLocale.language.lowercase(Locale.ROOT)

        // Verified Google Speech Services voice IDs on Android:
        // Bengali: bn-in-x-bnf & bn-in-x-bnx are FEMALE; bn-in-x-bin, bn-in-x-bnm, bn-bd-x-ban, bn-in-x-bnd are MALE!
        val verifiedFemaleTokens = when (lang) {
            "bn" -> listOf("-bnf-", "-bnx-", "bnf", "bnx", "female", "woman", "girl", "-f-", "_f_")
            "hi" -> listOf("-hia-", "-hif-", "-hic-", "hia", "hif", "hic", "female", "woman", "-f-", "_f_")
            "en" -> listOf("-ena-", "-enc-", "-enf-", "-iob-", "-sfb-", "-sfg-", "-tpf-", "-iol-", "-gba-", "female", "woman", "-f-", "_f_")
            else -> listOf("female", "woman", "girl", "-f-", "_f_", "wavenet-a", "wavenet-c", "wavenet-e", "neural2-a", "neural2-c")
        }

        val verifiedMaleTokens = when (lang) {
            "bn" -> listOf("-bnm-", "-bin-", "-ban-", "-bnd-", "bnm", "bin", "ban", "bnd", "male", "man", "boy", "-m-", "_m_")
            "hi" -> listOf("-hid-", "-hie-", "-him-", "hid", "hie", "him", "male", "man", "-m-", "_m_")
            "en" -> listOf("-end-", "-ene-", "-enm-", "-iom-", "-iog-", "-tpd-", "-gbb-", "male", "man", "-m-", "_m_")
            else -> listOf("male", "man", "boy", "-m-", "_m_", "wavenet-b", "wavenet-d", "neural2-b", "neural2-d")
        }

        fun Voice.isKnownFemale(): Boolean {
            val n = name.lowercase(Locale.ROOT)
            if (n.contains("male") && !n.contains("female")) return false
            return verifiedFemaleTokens.any { token -> n.contains(token) } &&
                verifiedMaleTokens.none { token -> token != "male" && n.contains(token) }
        }

        fun Voice.isKnownMale(): Boolean {
            val n = name.lowercase(Locale.ROOT)
            if (n.contains("female")) return false
            return verifiedMaleTokens.any { token -> n.contains(token) }
        }

        // Prefer high-quality & exact-country voices; prefer local over network ONLY among equal matches
        fun rankComparator(): Comparator<Voice> = compareBy<Voice>(
            { !it.locale?.country.equals(targetLocale.country, ignoreCase = true) },
            { it.isNetworkConnectionRequired },
            { -it.quality },
            { it.name }
        )

        return when (gender) {
            AssistantVoiceGender.FEMALE -> {
                // 1. Strict verified female voice (checks both local & network so bn-in-x-bnf-network wins over male local!)
                val verifiedFemale = voices
                    .filter { it.isKnownFemale() }
                    .sortedWith(
                        compareBy<Voice>(
                            // Prioritize bnf (sweetest Bengali female voice) first
                            { !it.name.lowercase(Locale.ROOT).contains("bnf") },
                            { !it.locale?.country.equals(targetLocale.country, ignoreCase = true) },
                            { it.isNetworkConnectionRequired },
                            { -it.quality },
                            { it.name }
                        )
                    )
                    .firstOrNull()
                if (verifiedFemale != null) {
                    return VoiceSelectionResult(verifiedFemale, isVerifiedGenderMatch = true)
                }

                // 2. Any voice in this language that is NOT in the male blacklist
                val nonMaleVoice = voices
                    .filterNot { it.isKnownMale() }
                    .sortedWith(rankComparator())
                    .firstOrNull()
                if (nonMaleVoice != null) {
                    return VoiceSelectionResult(nonMaleVoice, isVerifiedGenderMatch = false)
                }

                // 3. Absolute last resort (device has ONLY male voices installed) -> use feminine pitch shift
                VoiceSelectionResult(voices.sortedWith(rankComparator()).first(), isVerifiedGenderMatch = false)
            }

            AssistantVoiceGender.MALE -> {
                // 1. Strict verified male voice
                val verifiedMale = voices
                    .filter { it.isKnownMale() }
                    .sortedWith(
                        compareBy<Voice>(
                            { !it.name.lowercase(Locale.ROOT).contains("bnm") },
                            { !it.locale?.country.equals(targetLocale.country, ignoreCase = true) },
                            { it.isNetworkConnectionRequired },
                            { -it.quality },
                            { it.name }
                        )
                    )
                    .firstOrNull()
                if (verifiedMale != null) {
                    return VoiceSelectionResult(verifiedMale, isVerifiedGenderMatch = true)
                }

                // 2. Any voice not known female
                val nonFemaleVoice = voices
                    .filterNot { it.isKnownFemale() }
                    .sortedWith(rankComparator())
                    .firstOrNull()
                if (nonFemaleVoice != null) {
                    return VoiceSelectionResult(nonFemaleVoice, isVerifiedGenderMatch = false)
                }

                VoiceSelectionResult(voices.sortedWith(rankComparator()).last(), isVerifiedGenderMatch = false)
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
