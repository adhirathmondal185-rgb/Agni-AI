package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI
import java.util.Locale

data class CustomEndpointConfig(
    val displayLabel: String = "Agni Fine-Tuned / Open-Source",
    val endpointUrl: String = "",
    val modelName: String = "agni-ai-v1",
    val apiKey: String = ""
) {
    val isConfigured: Boolean
        get() = endpointUrl.isNotBlank() && modelName.isNotBlank()
}

object EndpointUrlValidator {
    private val blockedHosts = setOf(
        "localhost",
        "127.0.0.1",
        "0.0.0.0",
        "::1",
        "[::1]",
        "169.254.169.254",
        "metadata.google.internal"
    )

    fun validateHttpsEndpoint(rawUrl: String): Result<String> {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter your FastAPI or Open-Source HTTPS endpoint URL."))
        }
        val uri = runCatching { URI(trimmed) }.getOrElse {
            return Result.failure(IllegalArgumentException("Invalid URL format. Example: https://your-server.trycloudflare.com/v1/chat/completions"))
        }
        val scheme = uri.scheme?.lowercase(Locale.US)
        if (scheme != "https") {
            return Result.failure(IllegalArgumentException("Only secure HTTPS URLs (https://...) are allowed so your prompts and token stay encrypted."))
        }
        val host = uri.host?.lowercase(Locale.US).orEmpty()
        if (host.isBlank() || !host.contains('.')) {
            return Result.failure(IllegalArgumentException("Enter a valid public HTTPS domain (for example, your Cloudflare Tunnel, Hugging Face, or Render URL)."))
        }
        if (host in blockedHosts || host.startsWith("127.") || host.startsWith("169.254.")) {
            return Result.failure(IllegalArgumentException("Localhost and internal metadata addresses cannot be used from mobile devices. Use a public HTTPS tunnel URL."))
        }
        return Result.success(trimmed)
    }

    /**
     * Resolves a user-entered base URL to the OpenAI-compatible chat completions path
     * if no explicit path was entered, while preserving custom paths like /chat or /v1/chat/completions.
     */
    fun resolveCompletionsUrl(validatedUrl: String): String {
        val uri = URI(validatedUrl)
        val path = uri.path.orEmpty()
        return if (path.isEmpty() || path == "/") {
            validatedUrl.trimEnd('/') + "/v1/chat/completions"
        } else {
            validatedUrl
        }
    }
}

/**
 * Stores the user's self-hosted FastAPI / Open-Source / AGNI Fine-Tuned endpoint config
 * strictly on-device in app-private SharedPreferences, isolated per signed-in Google UID.
 * Credentials are never synced to Firestore or logged.
 */
class CustomModelConfigStore(
    context: Context,
    userId: String
) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "agni_custom_endpoint_${userId.hashCode()}",
        Context.MODE_PRIVATE
    )

    private val _config = MutableStateFlow(load())
    val config: StateFlow<CustomEndpointConfig> = _config.asStateFlow()

    private fun load(): CustomEndpointConfig {
        return CustomEndpointConfig(
            displayLabel = prefs.getString(KEY_DISPLAY_LABEL, DEFAULT_LABEL)
                ?.takeIf { it.isNotBlank() } ?: DEFAULT_LABEL,
            endpointUrl = prefs.getString(KEY_ENDPOINT_URL, "").orEmpty(),
            modelName = prefs.getString(KEY_MODEL_NAME, DEFAULT_MODEL)
                ?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL,
            apiKey = prefs.getString(KEY_API_KEY, "").orEmpty()
        )
    }

    fun save(config: CustomEndpointConfig): Result<CustomEndpointConfig> {
        val validatedUrl = EndpointUrlValidator.validateHttpsEndpoint(config.endpointUrl)
            .getOrElse { return Result.failure(it) }
        val cleanModel = config.modelName.trim().ifBlank { DEFAULT_MODEL }
        val cleanLabel = config.displayLabel.trim().ifBlank { DEFAULT_LABEL }.take(48)
        val cleanKey = config.apiKey.trim()

        val normalized = CustomEndpointConfig(
            displayLabel = cleanLabel,
            endpointUrl = validatedUrl,
            modelName = cleanModel,
            apiKey = cleanKey
        )
        prefs.edit()
            .putString(KEY_DISPLAY_LABEL, normalized.displayLabel)
            .putString(KEY_ENDPOINT_URL, normalized.endpointUrl)
            .putString(KEY_MODEL_NAME, normalized.modelName)
            .putString(KEY_API_KEY, normalized.apiKey)
            .apply()
        _config.value = normalized
        return Result.success(normalized)
    }

    fun clear() {
        prefs.edit().clear().apply()
        _config.value = CustomEndpointConfig()
    }

    companion object {
        private const val KEY_DISPLAY_LABEL = "display_label"
        private const val KEY_ENDPOINT_URL = "endpoint_url"
        private const val KEY_MODEL_NAME = "model_name"
        private const val KEY_API_KEY = "api_key"
        private const val DEFAULT_LABEL = "Agni Fine-Tuned / Open-Source"
        private const val DEFAULT_MODEL = "agni-ai-v1"
    }
}
