package com.example.model

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AssistantMode(
    val key: String,
    val label: String,
    val shortLabel: String,
    val subtitle: String
) {
    CHAT(
        key = "chat",
        label = "AI Chat",
        shortLabel = "Chat",
        subtitle = "Multilingual reasoning, drafting & daily assistance"
    ),
    TRANSLATION(
        key = "translation",
        label = "Translation",
        shortLabel = "Translate",
        subtitle = "Natural Indian & global language translation with script fidelity"
    ),
    EDUCATION(
        key = "education",
        label = "Education Assistant",
        shortLabel = "Learn",
        subtitle = "Socratic explanations, concepts, analogies & practice checks"
    ),
    MATH(
        key = "math",
        label = "Mathematics Solver",
        shortLabel = "Math",
        subtitle = "Step-by-step derivations, formulas & verification"
    ),
    CODING(
        key = "coding",
        label = "Coding Assistant",
        shortLabel = "Code",
        subtitle = "Architecture, debugging, clean snippets & complexity analysis"
    ),
    RESEARCH(
        key = "research",
        label = "Research Assistant",
        shortLabel = "Research",
        subtitle = "Deep synthesis with live Web Research grounding"
    );

    companion object {
        fun fromKey(raw: String?): AssistantMode =
            entries.firstOrNull { it.key == raw } ?: CHAT
    }
}

enum class AgniLanguage(
    val displayName: String,
    val nativeName: String,
    val localeTag: String
) {
    ENGLISH("English", "English", "en-IN"),
    BENGALI("Bengali", "বাংলা", "bn-IN"),
    HINDI("Hindi", "हिन्दी", "hi-IN"),
    TAMIL("Tamil", "தமிழ்", "ta-IN"),
    TELUGU("Telugu", "తెలుగు", "te-IN"),
    MARATHI("Marathi", "मराठी", "mr-IN"),
    GUJARATI("Gujarati", "ગુજરાતી", "gu-IN"),
    KANNADA("Kannada", "ಕನ್ನಡ", "kn-IN"),
    MALAYALAM("Malayalam", "മലയാളം", "ml-IN"),
    PUNJABI("Punjabi", "ਪੰਜਾਬੀ", "pa-IN"),
    URDU("Urdu", "اردو", "ur-IN"),
    ODIA("Odia", "ଓଡ଼ିଆ", "or-IN");

    companion object {
        fun fromName(raw: String?): AgniLanguage =
            entries.firstOrNull { it.displayName.equals(raw, ignoreCase = true) } ?: ENGLISH
    }
}

enum class FeedbackCategory(val key: String, val label: String) {
    ACCURACY("accuracy", "Accuracy"),
    LANGUAGE("language", "Indian Languages"),
    SPEED("speed", "Speed"),
    UI("ui", "Design & UX"),
    FEATURE("feature", "Feature Idea"),
    BUG("bug", "Issue Report");

    companion object {
        fun fromKey(raw: String?): FeedbackCategory =
            entries.firstOrNull { it.key == raw } ?: ACCURACY
    }
}

enum class MemoryCategory(val key: String, val label: String) {
    PREFERENCE("preference", "Preference"),
    LANGUAGE("language", "Language"),
    PROFESSION("profession", "Work / Study"),
    GOAL("goal", "Goal"),
    GENERAL("general", "General");

    companion object {
        fun fromKey(raw: String?): MemoryCategory =
            entries.firstOrNull { it.key == raw } ?: GENERAL
    }
}

data class ModelOption(
    val modelKey: String,
    val displayName: String,
    val provider: String,
    val tier: String,
    val badge: String,
    val description: String,
    val supportsMultimodal: Boolean,
    val supportsWebGrounding: Boolean,
    val isLiveReady: Boolean
)

data class PendingAttachment(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val uri: Uri? = null,
    val bitmap: Bitmap? = null,
    val pdfBytes: ByteArray? = null
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isPdf: Boolean get() = mimeType == "application/pdf"
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

// --- FIRESTORE DATA MODELS (1:1 parity with firestore.rules) ---

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val photoUrl: String? = null,
    val preferredLanguage: String = "English",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "New Conversation",
    val mode: String = "chat",
    val modelId: String = "gemini-3.8-flash",
    val language: String = "English",
    val messageCount: Long = 0L,
    val lastMessagePreview: String = "",
    val pinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val chatId: String = "",
    val role: String = "user",
    val content: String = "",
    val mode: String = "chat",
    val modelId: String = "gemini-3.8-flash",
    val language: String = "English",
    val attachmentName: String? = null,
    val attachmentMimeType: String? = null,
    val citations: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UploadedFileRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val chatId: String? = null,
    val fileName: String = "",
    val mimeType: String = "image/jpeg",
    val sizeBytes: Long = 0L,
    val analysisSummary: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val themeMode: String = "dark",
    val preferredLanguage: String = "English",
    val defaultModelId: String = "gemini-3.8-flash",
    val defaultMode: String = "chat",
    val voiceAutoSend: Boolean = false,
    val memoryEnabled: Boolean = true,
    val webResearchEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AiModelConfig(
    @DocumentId val id: String = "",
    val userId: String = "",
    val modelKey: String = "gemini-3.8-flash",
    val displayName: String = "Agni Flash",
    val provider: String = "Firebase AI Logic",
    val tier: String = "balanced",
    val enabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserFeedback(
    @DocumentId val id: String = "",
    val userId: String = "",
    val chatId: String? = null,
    val rating: Long = 5L,
    val category: String = "accuracy",
    val comment: String = "",
    val modelId: String = "gemini-3.8-flash",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UsageMetrics(
    @DocumentId val id: String = "",
    val userId: String = "",
    val totalMessages: Long = 0L,
    val totalImagesAnalyzed: Long = 0L,
    val totalPdfsAnalyzed: Long = 0L,
    val totalVoiceInputs: Long = 0L,
    val totalTranslations: Long = 0L,
    val estimatedTokens: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MemoryEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fact: String = "",
    val category: String = "general",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
