package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiInferenceRequest
import com.example.ai.AiModelRouter
import com.example.ai.AiStreamEvent
import com.example.data.AgniRepository
import com.example.model.AgniLanguage
import com.example.model.AiModelConfig
import com.example.model.AssistantMode
import com.example.model.ChatMessage
import com.example.model.ChatSession
import com.example.model.FeedbackCategory
import com.example.model.MemoryCategory
import com.example.model.MemoryEntry
import com.example.model.ModelOption
import com.example.model.PendingAttachment
import com.example.model.UiState
import com.example.model.UploadedFileRecord
import com.example.model.UsageMetrics
import com.example.model.UserFeedback
import com.example.model.UserProfile
import com.example.model.UserSettings
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "AgniViewModel"
private const val STOP_TIMEOUT_MS = 5_000L

enum class AgniDestination {
    CHAT,
    HUB,
    MEMORY,
    SETTINGS
}

data class ChatComposerState(
    val activeChatId: String? = null,
    val selectedMode: AssistantMode = AssistantMode.CHAT,
    val selectedModelKey: String = "gemini-3.8-flash",
    val selectedLanguage: AgniLanguage = AgniLanguage.ENGLISH,
    val translationTarget: AgniLanguage = AgniLanguage.BENGALI,
    val includeTransliteration: Boolean = true,
    val draftText: String = "",
    val pendingAttachment: PendingAttachment? = null,
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val streamingCitations: List<String> = emptyList(),
    val isVoiceListening: Boolean = false,
    val errorBanner: String? = null,
    val statusSnackbar: String? = null,
    val historySearchQuery: String = "",
    val currentDestination: AgniDestination = AgniDestination.CHAT
)

class AgniViewModel(
    private val repository: AgniRepository,
    private val aiRouter: AiModelRouter,
    val currentUserId: String,
    initialDisplayName: String,
    initialEmail: String?,
    initialPhotoUrl: String?
) : ViewModel() {

    val modelCatalog: List<ModelOption> = aiRouter.catalog()

    private val _composer = MutableStateFlow(ChatComposerState())
    val composer: StateFlow<ChatComposerState> = _composer.asStateFlow()

    private val _activeChatId = MutableStateFlow<String?>(null)
    private var activeGenerationJob: Job? = null

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            if (unwrapFirestoreError(error) == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing profile", error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val settings: StateFlow<UserSettings> = repository.observeSettings(currentUserId)
        .catch { error ->
            if (unwrapFirestoreError(error) == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing settings", error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UserSettings(userId = currentUserId))

    val chatsState: StateFlow<UiState<List<ChatSession>>> = repository.observeChats(currentUserId)
        .toUiState("Failed to load chat history")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    @OptIn(ExperimentalCoroutinesApi::class)
    val messagesState: StateFlow<UiState<List<ChatMessage>>> = _activeChatId
        .flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeMessages(currentUserId, chatId)
                    .toUiState("Failed to load conversation messages")
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Success(emptyList()))

    val filesState: StateFlow<UiState<List<UploadedFileRecord>>> = repository.observeFiles(currentUserId)
        .toUiState("Failed to load files")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val aiModelsState: StateFlow<UiState<List<AiModelConfig>>> = repository.observeAiModels(currentUserId)
        .toUiState("Failed to load model registry")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val feedbackState: StateFlow<UiState<List<UserFeedback>>> = repository.observeFeedback(currentUserId)
        .toUiState("Failed to load feedback")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val usage: StateFlow<UsageMetrics> = repository.observeUsage(currentUserId)
        .catch { error ->
            if (unwrapFirestoreError(error) == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing usage", error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UsageMetrics(userId = currentUserId))

    val memoriesState: StateFlow<UiState<List<MemoryEntry>>> = repository.observeMemories(currentUserId)
        .toUiState("Failed to load memories")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    init {
        viewModelScope.launch {
            val initResult = repository.ensureUserInitialized(
                displayName = initialDisplayName,
                email = initialEmail,
                photoUrl = initialPhotoUrl,
                defaultCatalog = modelCatalog
            )
            initResult.onSuccess { isNewAccount ->
                if (isNewAccount) {
                    val who = initialEmail ?: initialDisplayName
                    _composer.update {
                        it.copy(
                            statusSnackbar = "Account created for $who — welcome to Agni AI!"
                        )
                    }
                }
            }.onFailure { err ->
                _composer.update {
                    it.copy(errorBanner = err.localizedMessage ?: "Could not initialize user workspace.")
                }
            }
        }

        // Sync initial settings defaults once loaded
        viewModelScope.launch {
            settings.collect { pref ->
                if (_activeChatId.value == null) {
                    _composer.update { state ->
                        state.copy(
                            selectedLanguage = AgniLanguage.fromName(pref.preferredLanguage),
                            selectedModelKey = pref.defaultModelId.ifBlank { "gemini-3.8-flash" }
                        )
                    }
                }
            }
        }
    }

    fun navigateTo(destination: AgniDestination) {
        _composer.update { it.copy(currentDestination = destination) }
    }

    fun startNewChat(mode: AssistantMode = _composer.value.selectedMode) {
        activeGenerationJob?.cancel()
        _activeChatId.value = null
        _composer.update {
            it.copy(
                activeChatId = null,
                selectedMode = mode,
                isStreaming = false,
                streamingText = "",
                streamingCitations = emptyList(),
                errorBanner = null,
                currentDestination = AgniDestination.CHAT
            )
        }
    }

    fun selectChat(chat: ChatSession) {
        activeGenerationJob?.cancel()
        _activeChatId.value = chat.id
        _composer.update {
            it.copy(
                activeChatId = chat.id,
                selectedMode = AssistantMode.fromKey(chat.mode),
                selectedModelKey = chat.modelId.ifBlank { "gemini-3.8-flash" },
                selectedLanguage = AgniLanguage.fromName(chat.language),
                isStreaming = false,
                streamingText = "",
                streamingCitations = emptyList(),
                errorBanner = null,
                currentDestination = AgniDestination.CHAT
            )
        }
    }

    fun selectMode(mode: AssistantMode) {
        _composer.update { it.copy(selectedMode = mode, errorBanner = null) }
    }

    fun selectModel(modelKey: String) {
        val model = modelCatalog.firstOrNull { it.modelKey == modelKey } ?: return
        if (!model.isLiveReady) {
            _composer.update {
                it.copy(
                    statusSnackbar = "${model.displayName} requires an external FastAPI/PyTorch server. Using live Firebase AI Logic models."
                )
            }
            return
        }
        _composer.update { it.copy(selectedModelKey = modelKey, errorBanner = null) }
        viewModelScope.launch {
            repository.updateSettings(defaultModelId = modelKey)
        }
    }

    fun selectLanguage(language: AgniLanguage) {
        _composer.update { it.copy(selectedLanguage = language) }
        viewModelScope.launch {
            repository.updateSettings(preferredLanguage = language.displayName)
        }
    }

    fun selectTranslationTarget(language: AgniLanguage) {
        _composer.update { it.copy(translationTarget = language) }
    }

    fun toggleTransliteration(enabled: Boolean) {
        _composer.update { it.copy(includeTransliteration = enabled) }
    }

    fun updateDraft(text: String) {
        _composer.update { it.copy(draftText = text) }
    }

    fun updateSearchQuery(query: String) {
        _composer.update { it.copy(historySearchQuery = query) }
    }

    fun attachFile(attachment: PendingAttachment) {
        _composer.update { it.copy(pendingAttachment = attachment, errorBanner = null) }
    }

    fun clearAttachment() {
        _composer.update { it.copy(pendingAttachment = null) }
    }

    fun setVoiceListening(listening: Boolean) {
        _composer.update { it.copy(isVoiceListening = listening) }
    }

    fun onVoiceFinalTranscript(transcript: String) {
        val combined = if (_composer.value.draftText.isBlank()) {
            transcript
        } else {
            "${_composer.value.draftText.trimEnd()} $transcript"
        }
        _composer.update { it.copy(draftText = combined, isVoiceListening = false) }
        viewModelScope.launch {
            repository.incrementUsage(voiceDelta = 1L)
        }
        if (settings.value.voiceAutoSend && combined.isNotBlank()) {
            sendMessage()
        }
    }

    fun showError(message: String) {
        _composer.update { it.copy(errorBanner = message) }
    }

    fun clearError() {
        _composer.update { it.copy(errorBanner = null) }
    }

    fun clearSnackbar() {
        _composer.update { it.copy(statusSnackbar = null) }
    }

    fun stopGeneration() {
        activeGenerationJob?.cancel()
        _composer.update { it.copy(isStreaming = false) }
    }

    fun sendMessage(overridePrompt: String? = null) {
        val state = _composer.value
        val promptText = (overridePrompt ?: state.draftText).trim()
        val attachment = state.pendingAttachment

        if (promptText.isEmpty() && attachment == null) return
        if (state.isStreaming) return

        val effectiveDisplayText = when {
            promptText.isNotEmpty() -> promptText
            attachment?.isImage == true -> "Analyze image: ${attachment.fileName}"
            attachment?.isPdf == true -> "Analyze PDF: ${attachment.fileName}"
            else -> return
        }

        val currentMessages = (messagesState.value as? UiState.Success)?.data ?: emptyList()
        val activeMemories = if (settings.value.memoryEnabled) {
            ((memoriesState.value as? UiState.Success)?.data ?: emptyList())
                .filter { it.active }
                .map { it.fact }
        } else {
            emptyList()
        }

        _composer.update {
            it.copy(
                draftText = "",
                pendingAttachment = null,
                isStreaming = true,
                streamingText = "",
                streamingCitations = emptyList(),
                errorBanner = null
            )
        }

        activeGenerationJob = viewModelScope.launch {
            try {
                // 1. Ensure a chat document exists BEFORE writing messages (enforced by firestore.rules exists())
                val chatId = _activeChatId.value ?: run {
                    val autoTitle = effectiveDisplayText
                        .replace("\n", " ")
                        .take(54)
                        .ifBlank { state.selectedMode.label }
                    val created = repository.createChat(
                        title = autoTitle,
                        mode = state.selectedMode.key,
                        modelId = state.selectedModelKey,
                        language = state.selectedLanguage.displayName
                    ).getOrThrow()
                    _activeChatId.value = created
                    _composer.update { it.copy(activeChatId = created) }
                    created
                }

                // 2. Persist user message
                repository.addMessage(
                    chatId = chatId,
                    role = "user",
                    content = effectiveDisplayText,
                    mode = state.selectedMode.key,
                    modelId = state.selectedModelKey,
                    language = state.selectedLanguage.displayName,
                    attachmentName = attachment?.fileName,
                    attachmentMimeType = attachment?.mimeType
                ).getOrThrow()

                // 3. Stream live AI response via replaceable AiModelRouter
                var finalResponseText = ""
                var finalCitations: List<String> = emptyList()

                aiRouter.streamResponse(
                    AiInferenceRequest(
                        prompt = promptText,
                        modelKey = state.selectedModelKey,
                        mode = state.selectedMode,
                        language = state.selectedLanguage,
                        translationTarget = state.translationTarget,
                        includeTransliteration = state.includeTransliteration,
                        history = currentMessages,
                        activeMemories = activeMemories,
                        attachment = attachment,
                        webResearchEnabled = settings.value.webResearchEnabled
                    )
                ).collect { event ->
                    when (event) {
                        is AiStreamEvent.Chunk -> {
                            _composer.update {
                                it.copy(
                                    streamingText = event.accumulatedText,
                                    streamingCitations = event.citations
                                )
                            }
                        }
                        is AiStreamEvent.Complete -> {
                            finalResponseText = event.finalText
                            finalCitations = event.citations
                        }
                    }
                }

                // 4. Persist assistant message
                repository.addMessage(
                    chatId = chatId,
                    role = "assistant",
                    content = finalResponseText,
                    mode = state.selectedMode.key,
                    modelId = state.selectedModelKey,
                    language = state.selectedLanguage.displayName,
                    citations = finalCitations
                ).getOrThrow()

                // 5. Update chat preview + message count
                repository.updateChatMetadata(
                    chatId = chatId,
                    mode = state.selectedMode.key,
                    modelId = state.selectedModelKey,
                    language = state.selectedLanguage.displayName,
                    preview = finalResponseText.replace("\n", " ").take(140),
                    incrementMessagesBy = 2L
                )

                // 6. Log analyzed file metadata if attachment was included
                if (attachment != null) {
                    repository.recordUploadedFile(
                        chatId = chatId,
                        fileName = attachment.fileName,
                        mimeType = attachment.mimeType,
                        sizeBytes = attachment.sizeBytes,
                        analysisSummary = finalResponseText.take(360)
                    )
                }

                // 7. Update usage metrics in Firestore
                val estTokens = ((effectiveDisplayText.length + finalResponseText.length) / 4L).coerceAtLeast(1L)
                repository.incrementUsage(
                    messagesDelta = 1L,
                    imagesDelta = if (attachment?.isImage == true) 1L else 0L,
                    pdfsDelta = if (attachment?.isPdf == true) 1L else 0L,
                    translationsDelta = if (state.selectedMode == AssistantMode.TRANSLATION) 1L else 0L,
                    tokensDelta = estTokens
                )

                _composer.update {
                    it.copy(
                        isStreaming = false,
                        streamingText = "",
                        streamingCitations = emptyList()
                    )
                }
            } catch (e: CancellationException) {
                _composer.update { it.copy(isStreaming = false) }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error during AI message exchange", e)
                _composer.update {
                    it.copy(
                        isStreaming = false,
                        streamingText = "",
                        streamingCitations = emptyList(),
                        errorBanner = e.localizedMessage ?: "Could not complete AI request."
                    )
                }
            }
        }
    }

    // --- CHAT MANAGEMENT ---

    fun togglePinChat(chat: ChatSession) {
        viewModelScope.launch {
            repository.updateChatMetadata(chatId = chat.id, pinned = !chat.pinned)
                .onFailure { err ->
                    _composer.update { it.copy(errorBanner = err.localizedMessage ?: "Failed to update pin.") }
                }
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            if (_activeChatId.value == chatId) {
                startNewChat()
            }
            repository.deleteChat(chatId)
                .onSuccess {
                    _composer.update { it.copy(statusSnackbar = "Conversation deleted.") }
                }
                .onFailure { err ->
                    _composer.update { it.copy(errorBanner = err.localizedMessage ?: "Failed to delete chat.") }
                }
        }
    }

    fun clearAllChats() {
        viewModelScope.launch {
            startNewChat()
            repository.clearAllChats()
                .onSuccess {
                    _composer.update { it.copy(statusSnackbar = "All chat history cleared.") }
                }
                .onFailure { err ->
                    _composer.update { it.copy(errorBanner = err.localizedMessage ?: "Failed to clear history.") }
                }
        }
    }

    // --- MEMORY ---

    fun addMemory(fact: String, category: MemoryCategory) {
        if (fact.isBlank()) return
        viewModelScope.launch {
            repository.addMemory(fact.trim(), category.key)
                .onSuccess {
                    _composer.update { it.copy(statusSnackbar = "Saved to Agni Memory.") }
                }
                .onFailure { err ->
                    _composer.update { it.copy(errorBanner = err.localizedMessage ?: "Failed to save memory.") }
                }
        }
    }

    fun toggleMemory(entry: MemoryEntry) {
        viewModelScope.launch {
            repository.toggleMemory(entry.id, !entry.active)
                .onFailure { err ->
                    _composer.update { it.copy(errorBanner = err.localizedMessage ?: "Failed to update memory.") }
                }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            repository.deleteMemory(memoryId)
                .onFailure { err ->
                    _composer.update { it.copy(errorBanner = err.localizedMessage ?: "Failed to delete memory.") }
                }
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            repository.clearAllMemories()
                .onSuccess {
                    _composer.update { it.copy(statusSnackbar = "All memories cleared.") }
                }
                .onFailure { err ->
                    _composer.update { it.copy(errorBanner = err.localizedMessage ?: "Failed to clear memories.") }
                }
        }
    }

    // --- FILES ---

    fun deleteFileRecord(fileId: String) {
        viewModelScope.launch {
            repository.deleteFileRecord(fileId)
                .onSuccess {
                    _composer.update { it.copy(statusSnackbar = "File record removed.") }
                }
                .onFailure { err ->
                    _composer.update { it.copy(errorBanner = err.localizedMessage ?: "Failed to remove file record.") }
                }
        }
    }

    // --- SETTINGS ---

    fun updateThemeMode(themeMode: String) {
        viewModelScope.launch {
            repository.updateSettings(themeMode = themeMode)
                .onFailure { err ->
                    _composer.update { it.copy(errorBanner = err.localizedMessage ?: "Failed to save theme.") }
                }
        }
    }

    fun updateVoiceAutoSend(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings(voiceAutoSend = enabled)
        }
    }

    fun updateMemoryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings(memoryEnabled = enabled)
        }
    }

    fun updateWebResearchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings(webResearchEnabled = enabled)
        }
    }

    // --- FEEDBACK ---

    fun submitFeedback(rating: Int, category: FeedbackCategory, comment: String) {
        if (comment.isBlank()) {
            _composer.update { it.copy(errorBanner = "Please enter feedback details before submitting.") }
            return
        }
        viewModelScope.launch {
            repository.submitFeedback(
                chatId = _activeChatId.value,
                rating = rating,
                category = category.key,
                comment = comment,
                modelId = _composer.value.selectedModelKey
            ).onSuccess {
                _composer.update { it.copy(statusSnackbar = "Thank you! Feedback saved.") }
            }.onFailure { err ->
                _composer.update { it.copy(errorBanner = err.localizedMessage ?: "Failed to submit feedback.") }
            }
        }
    }

    private fun <T> Flow<T>.toUiState(fallbackError: String): Flow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, fallbackError, root)
                emit(UiState.Error(root.message ?: fallbackError))
            }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
}
