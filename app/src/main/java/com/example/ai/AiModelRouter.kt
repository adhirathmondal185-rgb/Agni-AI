package com.example.ai

import android.util.Log
import com.example.data.CustomEndpointConfig
import com.example.model.AgniLanguage
import com.example.model.AssistantMode
import com.example.model.ChatMessage
import com.example.model.ModelOption
import com.example.model.PendingAttachment
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "AgniAiRouter"
private const val INFERENCE_TIMEOUT_MS = 60_000L
const val CUSTOM_MODEL_KEY = "external-opensource-peft"

data class AiInferenceRequest(
    val prompt: String,
    val modelKey: String,
    val mode: AssistantMode,
    val language: AgniLanguage,
    val translationTarget: AgniLanguage = AgniLanguage.ENGLISH,
    val includeTransliteration: Boolean = false,
    val history: List<ChatMessage> = emptyList(),
    val activeMemories: List<String> = emptyList(),
    val attachment: PendingAttachment? = null,
    val webResearchEnabled: Boolean = true,
    val customEndpoint: CustomEndpointConfig? = null
)

sealed interface AiStreamEvent {
    data class Chunk(val accumulatedText: String, val citations: List<String>) : AiStreamEvent
    data class Complete(val finalText: String, val citations: List<String>) : AiStreamEvent
}

/**
 * Modular, replaceable AI Model Router abstraction.
 * Allows swapping inference backends (e.g., Firebase AI Logic today, or an external FastAPI/vLLM
 * open-source model router in the future) without changing any UI or ViewModel contract.
 */
interface AiModelRouter {
    fun catalog(): List<ModelOption>
    fun streamResponse(request: AiInferenceRequest): Flow<AiStreamEvent>
}

class FirebaseAiModelRouter : AiModelRouter {

    private val modelCatalog = listOf(
        ModelOption(
            modelKey = "gemini-3.8-flash",
            displayName = "Agni Flash (Gemini 3.8)",
            provider = "Firebase AI Logic",
            tier = "balanced",
            badge = "DEFAULT",
            description = "Frontier-class multilingual speed, Vision, PDF analysis & live Web Research.",
            supportsMultimodal = true,
            supportsWebGrounding = true,
            isLiveReady = true
        ),
        ModelOption(
            modelKey = "gemini-3.5-flash-lite",
            displayName = "Agni Lite (Gemini 3.5 Lite)",
            provider = "Firebase AI Logic",
            tier = "fast",
            badge = "ULTRA FAST",
            description = "Low-latency workhorse ideal for rapid translations, Q&A, and voice chat.",
            supportsMultimodal = true,
            supportsWebGrounding = true,
            isLiveReady = true
        ),
        ModelOption(
            modelKey = "gemini-4-argon",
            displayName = "Agni Argon (Gemini 4)",
            provider = "Firebase AI Logic",
            tier = "reasoning",
            badge = "DEEP STEM",
            description = "Maximum reasoning power for advanced mathematics, coding & deep research (Blaze plan).",
            supportsMultimodal = true,
            supportsWebGrounding = true,
            isLiveReady = true
        ),
        ModelOption(
            modelKey = "external-opensource-peft",
            displayName = "Custom Open-Source / PEFT Router",
            provider = "External FastAPI / PyTorch Backend",
            tier = "external",
            badge = "EXTERNAL API",
            description = "Plug-in slot for self-hosted Llama/Mistral/Sarvam LoRA adapters. Requires deploying an external inference API.",
            supportsMultimodal = false,
            supportsWebGrounding = false,
            isLiveReady = false
        )
    )

    override fun catalog(): List<ModelOption> = modelCatalog

    override fun streamResponse(request: AiInferenceRequest): Flow<AiStreamEvent> = flow {
        if (request.modelKey == CUSTOM_MODEL_KEY) {
            val cfg = request.customEndpoint
            if (cfg == null || !cfg.isConfigured) {
                throw IllegalStateException(
                    "Custom AGNI Model is not configured yet. Open Settings -> Custom AGNI Model to add your FastAPI or Open-Source HTTPS endpoint URL."
                )
            }
            val systemInstructionText = buildSystemInstruction(request)
            val effectivePrompt = request.prompt.ifBlank {
                when {
                    request.attachment?.isImage == true -> "The user attached an image (${request.attachment.fileName}), which this text-only custom endpoint cannot inspect directly. Explain politely how to switch to Agni Flash for vision."
                    request.attachment?.isPdf == true -> "The user attached a PDF (${request.attachment.fileName}), which this text-only custom endpoint cannot inspect directly. Explain politely how to switch to Agni Flash for PDF analysis."
                    else -> "Hello"
                }
            }
            val reply = try {
                withTimeout(INFERENCE_TIMEOUT_MS) {
                    CustomOpenSourceClient.generateCompletion(
                        config = cfg,
                        systemInstruction = systemInstructionText,
                        history = request.history,
                        userPrompt = effectivePrompt
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Custom endpoint timed out after ${INFERENCE_TIMEOUT_MS}ms", e)
                throw IllegalStateException("Custom model server timed out after 60 seconds. Check that your Colab/FastAPI server is running.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Custom endpoint inference failed", e)
                throw e
            }
            val cleanReply = reply.trim()
            if (cleanReply.isBlank()) {
                throw IllegalStateException("Your custom AGNI model server returned an empty response.")
            }
            emit(AiStreamEvent.Chunk(cleanReply, emptyList()))
            emit(AiStreamEvent.Complete(cleanReply, emptyList()))
            return@flow
        }

        val selected = modelCatalog.firstOrNull { it.modelKey == request.modelKey }
        val resolvedModelKey = if (selected != null && selected.isLiveReady) {
            selected.modelKey
        } else {
            "gemini-3.8-flash"
        }

        val systemInstructionText = buildSystemInstruction(request)
        val useWebGrounding = request.mode == AssistantMode.RESEARCH && request.webResearchEnabled

        // Lazily construct GenerativeModel on inference (never eagerly at startup)
        val tools = if (useWebGrounding) listOf(Tool.googleSearch()) else null
        val generativeModel = Firebase.ai.generativeModel(
            modelName = resolvedModelKey,
            systemInstruction = content { text(systemInstructionText) },
            tools = tools
        )

        val priorContents = buildHistoryContents(request.history)
        val userContent = buildUserContent(request)

        val accumulated = StringBuilder()
        val citations = linkedSetOf<String>()

        try {
            withTimeout(INFERENCE_TIMEOUT_MS) {
                val stream = if (priorContents.isNotEmpty() && request.attachment == null) {
                    val chat = generativeModel.startChat(history = priorContents)
                    chat.sendMessageStream(userContent)
                } else {
                    val fullPayload = priorContents + userContent
                    generativeModel.generateContentStream(fullPayload)
                }

                stream.collect { response ->
                    val piece = response.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                    }
                    response.candidates.firstOrNull()
                        ?.groundingMetadata
                        ?.groundingChunks
                        ?.forEach { chunk ->
                            val web = chunk.web
                            val label = web?.title?.takeIf { it.isNotBlank() }
                                ?: web?.uri?.takeIf { it.isNotBlank() }
                            val uri = web?.uri?.takeIf { it.isNotBlank() }
                            if (label != null && uri != null && label != uri) {
                                citations.add("$label — $uri")
                            } else if (label != null) {
                                citations.add(label)
                            }
                        }
                    if (accumulated.isNotEmpty()) {
                        emit(AiStreamEvent.Chunk(accumulated.toString(), citations.take(12)))
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Inference timed out after ${INFERENCE_TIMEOUT_MS}ms", e)
            throw IllegalStateException("Response timed out after 60 seconds. Please retry or try Agni Lite.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI inference failed for $resolvedModelKey", e)
            throw e
        }

        val finalOutput = accumulated.toString().trim()
        if (finalOutput.isBlank()) {
            throw IllegalStateException("The AI model returned an empty response. Please try again.")
        }
        emit(AiStreamEvent.Complete(finalOutput, citations.take(12)))
    }

    private fun buildHistoryContents(history: List<ChatMessage>): List<Content> {
        // Keep last 12 turns, strictly alternating user/model and ending on a model turn
        // so prior failed/unanswered user prompts never produce invalid consecutive user turns.
        val cleaned = mutableListOf<Pair<String, String>>()
        history
            .takeLast(12)
            .filter { it.content.isNotBlank() }
            .forEach { msg ->
                val role = if (msg.role == "assistant") "model" else "user"
                if (cleaned.isEmpty() && role == "model") return@forEach
                if (cleaned.isNotEmpty() && cleaned.last().first == role) {
                    val prev = cleaned.removeAt(cleaned.lastIndex)
                    cleaned.add(role to "${prev.second}\n\n${msg.content}")
                } else {
                    cleaned.add(role to msg.content)
                }
            }
        while (cleaned.isNotEmpty() && cleaned.last().first == "user") {
            cleaned.removeAt(cleaned.lastIndex)
        }
        return cleaned.map { (role, text) ->
            content(role = role) {
                text(text)
            }
        }
    }

    private fun buildUserContent(request: AiInferenceRequest): Content {
        val attachment = request.attachment
        return content(role = "user") {
            if (attachment != null) {
                when {
                    attachment.isImage && attachment.bitmap != null -> {
                        image(attachment.bitmap)
                    }
                    attachment.isPdf && attachment.pdfBytes != null -> {
                        inlineData(attachment.pdfBytes, "application/pdf")
                    }
                }
            }
            val effectivePrompt = request.prompt.ifBlank {
                when {
                    attachment?.isImage == true -> "Analyze this image in detail and explain what you see."
                    attachment?.isPdf == true -> "Analyze this PDF document: provide a structured summary, key insights, and action items."
                    else -> "Hello"
                }
            }
            text(effectivePrompt)
        }
    }

    private fun buildSystemInstruction(request: AiInferenceRequest): String {
        val sb = StringBuilder()
        sb.appendLine("You are AGNI AI ('One AI. Many Possibilities.'), an original, thoughtful, high-precision multilingual AI assistant.")
        sb.appendLine("Preferred response language: ${request.language.displayName} (${request.language.nativeName}). Respond naturally in ${request.language.displayName} unless the user explicitly writes in or asks for another language.")

        when (request.mode) {
            AssistantMode.CHAT -> {
                sb.appendLine("Mode: General AI Chat. Be clear, direct, structured, and helpful.")
            }
            AssistantMode.TRANSLATION -> {
                sb.appendLine("Mode: Expert Multilingual Translator.")
                sb.appendLine("Translate the user's input accurately and naturally into ${request.translationTarget.displayName} (${request.translationTarget.nativeName}).")
                sb.appendLine("Preserve cultural nuance, idioms, and proper native script.")
                if (request.includeTransliteration) {
                    sb.appendLine("Include phonetic Roman transliteration below the translated text and brief nuance notes.")
                }
            }
            AssistantMode.EDUCATION -> {
                sb.appendLine("Mode: Education Assistant & Socratic Tutor.")
                sb.appendLine("Break concepts into digestible steps, give an intuitive real-world analogy, highlight common pitfalls, and conclude with 2 short practice check questions.")
            }
            AssistantMode.MATH -> {
                sb.appendLine("Mode: Mathematics Solver.")
                sb.appendLine("Solve step by step: state the Given values, identify the Formula/Theorem, show each algebraic or numerical transformation clearly, double-check the result, and clearly box or bold the Final Answer.")
            }
            AssistantMode.CODING -> {
                sb.appendLine("Mode: Senior Coding Assistant.")
                sb.appendLine("Provide clean, idiomatic, production-ready code inside fenced markdown code blocks (```language), explain key design choices, edge cases, and time/space complexity.")
            }
            AssistantMode.RESEARCH -> {
                sb.appendLine("Mode: Research Assistant.")
                sb.appendLine("Deliver a rigorous, well-structured brief with: Executive Summary, Key Findings (bulleted), Trade-offs/Perspectives, and Verification/Next Steps. Cite factual details transparently.")
            }
        }

        if (request.activeMemories.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("User-approved memory context (respect these preferences naturally without repeating them verbatim):")
            request.activeMemories.take(15).forEach { fact ->
                sb.appendLine("- $fact")
            }
        }
        return sb.toString()
    }
}
