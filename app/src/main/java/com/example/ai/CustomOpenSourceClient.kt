package com.example.ai

import com.example.data.CustomEndpointConfig
import com.example.data.EndpointUrlValidator
import com.example.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

data class CustomConnectionTestResult(
    val latencyMs: Long,
    val resolvedEndpoint: String,
    val samplePreview: String
)

/**
 * Lightweight, zero-extra-dependency HTTPS client for user-configured FastAPI, vLLM,
 * Ollama (via HTTPS tunnel), Hugging Face Inference, Groq, Together, or OpenRouter endpoints.
 * Supports OpenAI-compatible /v1/chat/completions as well as simple FastAPI JSON responses.
 */
object CustomOpenSourceClient {

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val MAX_RESPONSE_CHARS = 512_000

    suspend fun generateCompletion(
        config: CustomEndpointConfig,
        systemInstruction: String,
        history: List<ChatMessage>,
        userPrompt: String
    ): String = withContext(Dispatchers.IO) {
        val validatedUrl = EndpointUrlValidator.validateHttpsEndpoint(config.endpointUrl)
            .getOrThrow()
        val targetUrl = EndpointUrlValidator.resolveCompletionsUrl(validatedUrl)

        val messagesJson = JSONArray().apply {
            if (systemInstruction.isNotBlank()) {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemInstruction)
                })
            }
            history.takeLast(10)
                .filter { it.content.isNotBlank() }
                .forEach { msg ->
                    val role = if (msg.role == "assistant") "assistant" else "user"
                    put(JSONObject().apply {
                        put("role", role)
                        put("content", msg.content)
                    })
                }
            put(JSONObject().apply {
                put("role", "user")
                put("content", userPrompt)
            })
        }

        val payload = JSONObject().apply {
            put("model", config.modelName.trim().ifBlank { "agni-ai-v1" })
            put("messages", messagesJson)
            put("temperature", 0.7)
            put("max_tokens", 1024)
            put("stream", false)
        }

        val rawResponse = postJson(
            targetUrl = targetUrl,
            apiKey = config.apiKey,
            bodyJson = payload.toString()
        )
        extractReplyText(rawResponse)
            ?: throw IllegalStateException("Custom server responded, but no answer text was found in the JSON response.")
    }

    suspend fun testConnection(config: CustomEndpointConfig): Result<CustomConnectionTestResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val validatedUrl = EndpointUrlValidator.validateHttpsEndpoint(config.endpointUrl)
                    .getOrThrow()
                val targetUrl = EndpointUrlValidator.resolveCompletionsUrl(validatedUrl)
                val startedAt = System.currentTimeMillis()

                val reply = generateCompletion(
                    config = config.copy(endpointUrl = validatedUrl),
                    systemInstruction = "You are AGNI AI. Reply briefly in 1 short sentence.",
                    history = emptyList(),
                    userPrompt = "Say hello and confirm your model name."
                )
                val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
                CustomConnectionTestResult(
                    latencyMs = elapsed,
                    resolvedEndpoint = targetUrl,
                    samplePreview = reply.replace("\n", " ").take(120)
                )
            }
        }

    private fun postJson(
        targetUrl: String,
        apiKey: String,
        bodyJson: String
    ): String {
        val connection = (URL(targetUrl).openConnection() as? HttpsURLConnection)
            ?: throw IllegalStateException("Only secure HTTPS connections are supported.")

        return try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doInput = true
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "AgniAI-Android/1.0")
            if (apiKey.isNotBlank()) {
                connection.setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            }

            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(bodyJson)
                writer.flush()
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = if (stream != null) readBounded(stream) else ""

            if (code !in 200..299) {
                val detail = extractErrorDetail(body) ?: "HTTP $code"
                throw IllegalStateException("Custom model server error ($code): $detail")
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(stream: java.io.InputStream): String {
        val sb = StringBuilder()
        BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
            val buffer = CharArray(4096)
            var total = 0
            while (total < MAX_RESPONSE_CHARS) {
                val read = reader.read(buffer)
                if (read == -1) break
                val allowed = minOf(read, MAX_RESPONSE_CHARS - total)
                sb.append(buffer, 0, allowed)
                total += allowed
            }
        }
        return sb.toString()
    }

    private fun extractReplyText(rawJson: String): String? {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return null

        val root = runCatching { JSONObject(trimmed) }.getOrNull() ?: return trimmed

        // 1. OpenAI / FastAPI / vLLM / Hugging Face / OpenRouter / Groq format
        val choices = root.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val first = choices.optJSONObject(0)
            val message = first?.optJSONObject("message")
            val content = message?.optString("content")?.takeIf { it.isNotBlank() }
                ?: first?.optString("text")?.takeIf { it.isNotBlank() }
            if (content != null) return content.trim()
        }

        // 2. Simple custom FastAPI / Ollama JSON keys
        val directKeys = listOf("answer", "response", "reply", "output", "text", "generated_text")
        for (key in directKeys) {
            val value = root.optString(key).takeIf { it.isNotBlank() }
            if (value != null) return value.trim()
        }

        // 3. Ollama /api/chat shape: { "message": { "content": "..." } }
        val ollamaMsg = root.optJSONObject("message")?.optString("content")?.takeIf { it.isNotBlank() }
        if (ollamaMsg != null) return ollamaMsg.trim()

        return null
    }

    private fun extractErrorDetail(rawBody: String): String? {
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) return null
        val json = runCatching { JSONObject(trimmed) }.getOrNull() ?: return trimmed.take(180)
        val errObj = json.optJSONObject("error")
        if (errObj != null) {
            val msg = errObj.optString("message").takeIf { it.isNotBlank() }
            if (msg != null) return msg.take(180)
        }
        return json.optString("detail").takeIf { it.isNotBlank() }?.take(180)
            ?: json.optString("message").takeIf { it.isNotBlank() }?.take(180)
            ?: trimmed.take(180)
    }
}
