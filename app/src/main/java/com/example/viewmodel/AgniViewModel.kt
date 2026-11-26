package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiInferenceRequest
import com.example.ai.AiModelRouter
import com.example.ai.AiStreamEvent
import com.example.ai.CUSTOM_MODEL_KEY
import com.example.ai.CustomOpenSourceClient
import com.example.data.AgniRepository
import com.example.data.CustomEndpointConfig
import com.example.data.CustomModelConfigStore
import com.example.media.AgniImageAiService
import com.example.media.AgniMediaProcessor
import com.example.media.ImageSearchClient
import com.example.media.SearchedImageResult
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
import com.example.voice.AssistantVoiceGender
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
    MEDIA_STUDIO,
    HUB,
    ARCHITECTURE,
    MEMORY,
    SETTINGS
}

enum class PipelineNodeId {
    USER_QUESTION,
    AGNI_CLIENT,
    AUTHENTICATION,
    FASTAPI_BACKEND,
    REQUEST_VALIDATION,
    MODEL_ROUTER,
    OPEN_SOURCE_AI,
    AGNI_FINE_TUNED,
    RESPONSE_PROCESSING,
    SAFETY_CHECKS,
    SAVE_CHAT_HISTORY,
    RETURN_ANSWER
}

data class PipelineTraceState(
    val completedNodes: Set<PipelineNodeId> = emptySet(),
    val activeNode: PipelineNodeId? = null,
    val lastModelName: String? = null,
    val lastLatencyMs: Long? = null
)

sealed interface CustomConnectionTestState {
    data object Idle : CustomConnectionTestState
    data object Testing : CustomConnectionTestState
    data class Success(val latencyMs: Long, val preview: String) : CustomConnectionTestState
    data class Error(val message: String) : CustomConnectionTestState
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
    val currentDestination: AgniDestination = AgniDestination.CHAT,
    val voiceGender: AssistantVoiceGender = AssistantVoiceGender.FEMALE,
    val autoSpeakReplies: Boolean = true,
    val lastCompletedReplyText: String? = null,
    val lastCompletedReplySeq: Long = 0L
)

data class MediaStudioState(
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<SearchedImageResult> = emptyList(),
    val searchError: String? = null,
    val editorSourceBitmap: Bitmap? = null,
    val editorSourceTitle: String = "Agni Photo",
    val isAiGenerating: Boolean = false,
    val videoSlides: List<Bitmap> = emptyList(),
    val isVideoExporting: Boolean = false,
    val videoExportProgress: Float = 0f,
    val lastExportedVideoUri: Uri? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class AgniViewModel(
    private val appContext: Context,
    private val repository: AgniRepository,
    private val aiRouter: AiModelRouter,
    private val customModelStore: CustomModelConfigStore,
    val currentUserId: String,
    initialDisplayName: String,
    initialEmail: String?,
    initialPhotoUrl: String?
) : ViewModel() {

    private val imageAiService = AgniImageAiService()
    private val _mediaStudio = MutableStateFlow(MediaStudioState())
    val mediaStudio: StateFlow<MediaStudioState> = _mediaStudio.asStateFlow()

    private val baseCatalog: List<ModelOption> = aiRouter.catalog()

    val customEndpointConfig: StateFlow<CustomEndpointConfig> = customModelStore.config

    private val _customTestState = MutableStateFlow<CustomConnectionTestState>(CustomConnectionTestState.Idle)
    val customTestState: StateFlow<CustomConnectionTestState> = _customTestState.asStateFlow()

    val modelCatalog: StateFlow<List<ModelOption>> = customEndpointConfig
        .map { cfg -> buildDynamicCatalog(cfg) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = buildDynamicCatalog(customModelStore.config.value)
        )

    private val voicePrefs = appContext.getSharedPreferences("agni_voice_prefs", Context.MODE_PRIVATE)

    private val _composer = MutableStateFlow(
        ChatComposerState(
            voiceGender = AssistantVoiceGender.fromKey(voicePrefs.getString("voice_gender", "female")),
            autoSpeakReplies = voicePrefs.getBoolean("auto_speak_replies", true)
        )
    )
    val composer: StateFlow<ChatComposerState> = _composer.asStateFlow()

    private val _pipelineTrace = MutableStateFlow(PipelineTraceState())
    val pipelineTrace: StateFlow<PipelineTraceState> = _pipelineTrace.asStateFlow()

    private val _activeChatId = MutableStateFlow<String?>(null)
    private var activeGenerationJob: Job? = null

    private fun buildDynamicCatalog(cfg: CustomEndpointConfig): List<ModelOption> {
        return baseCatalog.map { option ->
            if (option.modelKey != CUSTOM_MODEL_KEY) {
                option
            } else if (cfg.isConfigured) {
                option.copy(
                    displayName = cfg.displayLabel,
                    provider = "Custom FastAPI · ${cfg.modelName}",
                    badge = "CUSTOM LIVE",
                    description = "Connected to your self-hosted FastAPI / AGNI Fine-Tuned endpoint (${cfg.endpointUrl}).",
                    isLiveReady = true
                )
            } else {
                option.copy(
                    displayName = "Custom AGNI / Open-Source Model",
                    provider = "External FastAPI / PyTorch Backend",
                    badge = "SETUP API",
                    description = "Tap to configure your own AGNI Fine-Tuned LoRA, FastAPI, vLLM, or Open-Source HTTPS endpoint.",
                    isLiveReady = false
                )
            }
        }
    }

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
                defaultCatalog = baseCatalog
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
        val model = modelCatalog.value.firstOrNull { it.modelKey == modelKey } ?: return
        if (!model.isLiveReady) {
            _composer.update {
                it.copy(
                    currentDestination = AgniDestination.SETTINGS,
                    statusSnackbar = "Add your Custom AGNI / FastAPI HTTPS URL in Settings to activate your own model."
                )
            }
            return
        }
        _composer.update { it.copy(selectedModelKey = modelKey, errorBanner = null) }
        viewModelScope.launch {
            repository.updateSettings(defaultModelId = modelKey)
        }
    }

    fun saveAndActivateCustomEndpoint(
        displayLabel: String,
        endpointUrl: String,
        modelName: String,
        apiKey: String
    ) {
        val result = customModelStore.save(
            CustomEndpointConfig(
                displayLabel = displayLabel,
                endpointUrl = endpointUrl,
                modelName = modelName,
                apiKey = apiKey
            )
        )
        result.onSuccess { saved ->
            _customTestState.value = CustomConnectionTestState.Idle
            _composer.update {
                it.copy(
                    selectedModelKey = CUSTOM_MODEL_KEY,
                    errorBanner = null,
                    statusSnackbar = "Saved & activated ${saved.displayLabel} (${saved.modelName})."
                )
            }
            viewModelScope.launch {
                repository.updateSettings(defaultModelId = CUSTOM_MODEL_KEY)
            }
        }.onFailure { err ->
            _customTestState.value = CustomConnectionTestState.Error(
                err.localizedMessage ?: "Invalid HTTPS endpoint configuration."
            )
        }
    }

    fun testCustomEndpoint(
        displayLabel: String,
        endpointUrl: String,
        modelName: String,
        apiKey: String
    ) {
        viewModelScope.launch {
            _customTestState.value = CustomConnectionTestState.Testing
            val candidate = CustomEndpointConfig(
                displayLabel = displayLabel.ifBlank { "Agni Custom Model" },
                endpointUrl = endpointUrl,
                modelName = modelName.ifBlank { "agni-ai-v1" },
                apiKey = apiKey
            )
            CustomOpenSourceClient.testConnection(candidate)
                .onSuccess { res ->
                    _customTestState.value = CustomConnectionTestState.Success(
                        latencyMs = res.latencyMs,
                        preview = res.samplePreview
                    )
                }
                .onFailure { err ->
                    _customTestState.value = CustomConnectionTestState.Error(
                        err.localizedMessage ?: "Could not connect to custom server."
                    )
                }
        }
    }

    fun clearCustomEndpoint() {
        customModelStore.clear()
        _customTestState.value = CustomConnectionTestState.Idle
        if (_composer.value.selectedModelKey == CUSTOM_MODEL_KEY) {
            _composer.update {
                it.copy(
                    selectedModelKey = "gemini-3.8-flash",
                    statusSnackbar = "Custom endpoint cleared. Switched back to Agni Flash."
                )
            }
            viewModelScope.launch {
                repository.updateSettings(defaultModelId = "gemini-3.8-flash")
            }
        } else {
            _composer.update { it.copy(statusSnackbar = "Custom endpoint cleared.") }
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

    fun selectVoiceGender(gender: AssistantVoiceGender) {
        voicePrefs.edit().putString("voice_gender", gender.key).apply()
        _composer.update { it.copy(voiceGender = gender) }
    }

    fun toggleAutoSpeakReplies(enabled: Boolean) {
        voicePrefs.edit().putBoolean("auto_speak_replies", enabled).apply()
        _composer.update { it.copy(autoSpeakReplies = enabled) }
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
        if ((settings.value.voiceAutoSend || _composer.value.autoSpeakReplies) && combined.isNotBlank()) {
            sendMessage()
        }
    }

    fun sendVoiceConversationTranscript(transcript: String) {
        val clean = transcript.trim()
        if (clean.isEmpty()) return
        _composer.update { it.copy(draftText = "", isVoiceListening = false, errorBanner = null) }
        viewModelScope.launch {
            repository.incrementUsage(voiceDelta = 1L)
        }
        sendMessage(overridePrompt = clean)
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
            val startedAtMs = System.currentTimeMillis()
            val isCustomRoute = state.selectedModelKey == CUSTOM_MODEL_KEY
            val selectedModelDisplay = modelCatalog.value
                .firstOrNull { it.modelKey == state.selectedModelKey }
                ?.displayName ?: state.selectedModelKey

            fun recordStage(completed: Set<PipelineNodeId>, active: PipelineNodeId?, latencyMs: Long? = null) {
                _pipelineTrace.value = PipelineTraceState(
                    completedNodes = completed,
                    activeNode = active,
                    lastModelName = selectedModelDisplay,
                    lastLatencyMs = latencyMs
                )
            }

            try {
                // Stage A -> B -> C (-> D if FastAPI custom backend) -> E
                val validatedNodes = mutableSetOf(
                    PipelineNodeId.USER_QUESTION,
                    PipelineNodeId.AGNI_CLIENT,
                    PipelineNodeId.AUTHENTICATION,
                    PipelineNodeId.REQUEST_VALIDATION
                )
                if (isCustomRoute) {
                    validatedNodes.add(PipelineNodeId.FASTAPI_BACKEND)
                }
                recordStage(completed = validatedNodes.toSet(), active = PipelineNodeId.MODEL_ROUTER)

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

                // Stage F -> H (or G): Model Router dispatching to active model
                validatedNodes.add(PipelineNodeId.MODEL_ROUTER)
                val targetModelBranch = if (isCustomRoute) {
                    PipelineNodeId.OPEN_SOURCE_AI
                } else {
                    PipelineNodeId.AGNI_FINE_TUNED
                }
                recordStage(completed = validatedNodes.toSet(), active = targetModelBranch)

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
                        webResearchEnabled = settings.value.webResearchEnabled,
                        customEndpoint = customEndpointConfig.value
                    )
                ).collect { event ->
                    when (event) {
                        is AiStreamEvent.Chunk -> {
                            validatedNodes.add(targetModelBranch)
                            recordStage(completed = validatedNodes.toSet(), active = PipelineNodeId.RESPONSE_PROCESSING)
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

                // Stage I -> J: Response Processing + Safety & Quality Checks
                validatedNodes.add(targetModelBranch)
                validatedNodes.add(PipelineNodeId.RESPONSE_PROCESSING)
                validatedNodes.add(PipelineNodeId.SAFETY_CHECKS)
                recordStage(completed = validatedNodes.toSet(), active = PipelineNodeId.SAVE_CHAT_HISTORY)

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

                // Stage K -> L: Chat History saved & Answer returned to User
                validatedNodes.add(PipelineNodeId.SAVE_CHAT_HISTORY)
                validatedNodes.add(PipelineNodeId.RETURN_ANSWER)
                recordStage(
                    completed = validatedNodes.toSet(),
                    active = null,
                    latencyMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
                )

                _composer.update {
                    it.copy(
                        isStreaming = false,
                        streamingText = "",
                        streamingCitations = emptyList(),
                        lastCompletedReplyText = finalResponseText,
                        lastCompletedReplySeq = it.lastCompletedReplySeq + 1L
                    )
                }
            } catch (e: CancellationException) {
                _pipelineTrace.update { it.copy(activeNode = null) }
                _composer.update { it.copy(isStreaming = false) }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error during AI message exchange", e)
                _pipelineTrace.update { it.copy(activeNode = null) }
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

    // --- MEDIA STUDIO (IMAGE FINDER, AI IMAGE CREATE/EDIT, PHOTO & VIDEO STUDIO) ---

    fun clearStudioMessage() {
        _mediaStudio.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    fun searchStudioImages(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            _mediaStudio.update {
                it.copy(searchQuery = clean, isSearching = true, searchError = null)
            }
            ImageSearchClient.searchImages(clean)
                .onSuccess { results ->
                    _mediaStudio.update {
                        it.copy(isSearching = false, searchResults = results, searchError = null)
                    }
                }
                .onFailure { err ->
                    _mediaStudio.update {
                        it.copy(
                            isSearching = false,
                            searchError = err.localizedMessage ?: "Could not search images."
                        )
                    }
                }
        }
    }

    fun selectSearchResultForEdit(item: SearchedImageResult) {
        viewModelScope.launch {
            _mediaStudio.update { it.copy(statusMessage = "Loading photo into editor…", errorMessage = null) }
            ImageSearchClient.downloadBitmap(item.thumbUrl)
                .onSuccess { bmp ->
                    _mediaStudio.update {
                        it.copy(
                            editorSourceBitmap = bmp,
                            editorSourceTitle = item.title,
                            statusMessage = "Ready to edit: ${item.title}",
                            errorMessage = null
                        )
                    }
                }
                .onFailure { err ->
                    _mediaStudio.update {
                        it.copy(
                            statusMessage = null,
                            errorMessage = err.localizedMessage ?: "Failed to load selected photo."
                        )
                    }
                }
        }
    }

    fun downloadSearchResult(item: SearchedImageResult) {
        viewModelScope.launch {
            _mediaStudio.update { it.copy(statusMessage = "Saving photo to Gallery…", errorMessage = null) }
            ImageSearchClient.downloadBitmap(item.fullUrl)
                .recoverCatching { ImageSearchClient.downloadBitmap(item.thumbUrl).getOrThrow() }
                .onSuccess { bmp ->
                    AgniMediaProcessor.saveBitmapToGallery(appContext, bmp, "AgniAI_Found")
                        .onSuccess {
                            _mediaStudio.update {
                                it.copy(statusMessage = "Saved to Gallery (Pictures/AgniAI).", errorMessage = null)
                            }
                        }
                        .onFailure { err ->
                            _mediaStudio.update {
                                it.copy(statusMessage = null, errorMessage = err.localizedMessage ?: "Save failed.")
                            }
                        }
                }
                .onFailure { err ->
                    _mediaStudio.update {
                        it.copy(statusMessage = null, errorMessage = err.localizedMessage ?: "Download failed.")
                    }
                }
        }
    }

    fun addSearchResultToVideo(item: SearchedImageResult) {
        viewModelScope.launch {
            _mediaStudio.update { it.copy(statusMessage = "Adding photo to Video Studio…", errorMessage = null) }
            ImageSearchClient.downloadBitmap(item.thumbUrl, maxDim = 1080)
                .onSuccess { bmp ->
                    _mediaStudio.update {
                        it.copy(
                            videoSlides = (it.videoSlides + bmp).take(12),
                            statusMessage = "Added '${item.title}' to Video Studio (${(it.videoSlides.size + 1).coerceAtMost(12)} slides).",
                            errorMessage = null
                        )
                    }
                }
                .onFailure { err ->
                    _mediaStudio.update {
                        it.copy(statusMessage = null, errorMessage = err.localizedMessage ?: "Failed to add photo.")
                    }
                }
        }
    }

    fun sendSearchResultToChat(item: SearchedImageResult) {
        viewModelScope.launch {
            _mediaStudio.update { it.copy(statusMessage = "Preparing photo for AI Chat…", errorMessage = null) }
            ImageSearchClient.downloadBitmap(item.thumbUrl, maxDim = 1280)
                .onSuccess { bmp ->
                    sendBitmapToChat(bmp, "${item.title.take(30)}.jpg")
                }
                .onFailure { err ->
                    _mediaStudio.update {
                        it.copy(statusMessage = null, errorMessage = err.localizedMessage ?: "Failed to attach photo.")
                    }
                }
        }
    }

    fun sendBitmapToChat(bitmap: Bitmap, title: String) {
        val approxBytes = (bitmap.byteCount / 4L).coerceAtLeast(1024L)
        val attachment = PendingAttachment(
            fileName = if (title.endsWith(".jpg", ignoreCase = true)) title else "${title.take(32)}.jpg",
            mimeType = "image/jpeg",
            sizeBytes = approxBytes,
            bitmap = bitmap
        )
        _composer.update {
            it.copy(
                pendingAttachment = attachment,
                currentDestination = AgniDestination.CHAT,
                errorBanner = null
            )
        }
    }

    fun setEditorBitmap(bitmap: Bitmap, title: String) {
        _mediaStudio.update {
            it.copy(
                editorSourceBitmap = bitmap,
                editorSourceTitle = title,
                errorMessage = null
            )
        }
    }

    fun generateAiImage(prompt: String) {
        val clean = prompt.trim()
        if (clean.isEmpty() || _mediaStudio.value.isAiGenerating) return
        viewModelScope.launch {
            _mediaStudio.update {
                it.copy(isAiGenerating = true, statusMessage = null, errorMessage = null)
            }
            try {
                val bmp = imageAiService.generateImage(clean)
                _mediaStudio.update {
                    it.copy(
                        isAiGenerating = false,
                        editorSourceBitmap = bmp,
                        editorSourceTitle = clean.take(36),
                        statusMessage = "AI photo created! You can now edit or save it.",
                        errorMessage = null
                    )
                }
            } catch (e: CancellationException) {
                _mediaStudio.update { it.copy(isAiGenerating = false) }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI image generation error", e)
                _mediaStudio.update {
                    it.copy(
                        isAiGenerating = false,
                        errorMessage = e.localizedMessage
                            ?: "AI image generation failed. Note: Cloud AI image generation may require Firebase Blaze plan."
                    )
                }
            }
        }
    }

    fun aiEditCurrentImage(instruction: String) {
        val src = _mediaStudio.value.editorSourceBitmap ?: return
        val clean = instruction.trim()
        if (clean.isEmpty() || _mediaStudio.value.isAiGenerating) return
        viewModelScope.launch {
            _mediaStudio.update {
                it.copy(isAiGenerating = true, statusMessage = null, errorMessage = null)
            }
            try {
                val edited = imageAiService.editImage(src, clean)
                _mediaStudio.update {
                    it.copy(
                        isAiGenerating = false,
                        editorSourceBitmap = edited,
                        statusMessage = "AI edit applied! Tap Save Photo to keep it in Gallery.",
                        errorMessage = null
                    )
                }
            } catch (e: CancellationException) {
                _mediaStudio.update { it.copy(isAiGenerating = false) }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI photo edit error", e)
                _mediaStudio.update {
                    it.copy(
                        isAiGenerating = false,
                        errorMessage = e.localizedMessage
                            ?: "AI photo edit failed. On-device filters, sliders, rotation & captions still work offline!"
                    )
                }
            }
        }
    }

    fun saveEditedBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            AgniMediaProcessor.saveBitmapToGallery(appContext, bitmap, "AgniAI_Edit")
                .onSuccess {
                    _mediaStudio.update {
                        it.copy(
                            statusMessage = "Photo saved to Gallery (Pictures/AgniAI).",
                            errorMessage = null
                        )
                    }
                }
                .onFailure { err ->
                    _mediaStudio.update {
                        it.copy(
                            statusMessage = null,
                            errorMessage = err.localizedMessage ?: "Could not save photo."
                        )
                    }
                }
        }
    }

    fun addBitmapToVideoSlides(bitmap: Bitmap) {
        _mediaStudio.update { state ->
            val updated = (state.videoSlides + bitmap).take(12)
            state.copy(
                videoSlides = updated,
                statusMessage = "Video Studio now has ${updated.size} photo slide(s).",
                errorMessage = null
            )
        }
    }

    fun removeVideoSlide(index: Int) {
        _mediaStudio.update { state ->
            if (index !in state.videoSlides.indices) state
            else state.copy(videoSlides = state.videoSlides.toMutableList().apply { removeAt(index) })
        }
    }

    fun clearVideoSlides() {
        _mediaStudio.update { it.copy(videoSlides = emptyList()) }
    }

    fun exportSlideshowVideo(secondsPerSlide: Int, titleText: String) {
        val slides = _mediaStudio.value.videoSlides
        if (slides.isEmpty() || _mediaStudio.value.isVideoExporting) return
        viewModelScope.launch {
            _mediaStudio.update {
                it.copy(
                    isVideoExporting = true,
                    videoExportProgress = 0.02f,
                    statusMessage = null,
                    errorMessage = null
                )
            }
            AgniMediaProcessor.createSlideshowVideo(
                context = appContext,
                bitmaps = slides,
                secondsPerSlide = secondsPerSlide,
                titleText = titleText,
                onProgress = { pct ->
                    _mediaStudio.update { it.copy(videoExportProgress = pct) }
                }
            ).onSuccess { savedUri ->
                _mediaStudio.update {
                    it.copy(
                        isVideoExporting = false,
                        videoExportProgress = 1f,
                        lastExportedVideoUri = savedUri,
                        statusMessage = "MP4 video created & saved to Gallery (Movies/AgniAI)!",
                        errorMessage = null
                    )
                }
            }.onFailure { err ->
                Log.e(TAG, "Slideshow MP4 export failed", err)
                _mediaStudio.update {
                    it.copy(
                        isVideoExporting = false,
                        videoExportProgress = 0f,
                        errorMessage = err.localizedMessage ?: "Video export failed."
                    )
                }
            }
        }
    }

    fun trimStudioVideo(sourceUri: Uri, startMs: Long, endMs: Long) {
        if (_mediaStudio.value.isVideoExporting) return
        viewModelScope.launch {
            _mediaStudio.update {
                it.copy(
                    isVideoExporting = true,
                    videoExportProgress = 0.02f,
                    statusMessage = null,
                    errorMessage = null
                )
            }
            AgniMediaProcessor.trimVideo(
                context = appContext,
                sourceUri = sourceUri,
                startMs = startMs,
                endMs = endMs,
                onProgress = { pct ->
                    _mediaStudio.update { it.copy(videoExportProgress = pct) }
                }
            ).onSuccess { savedUri ->
                _mediaStudio.update {
                    it.copy(
                        isVideoExporting = false,
                        videoExportProgress = 1f,
                        lastExportedVideoUri = savedUri,
                        statusMessage = "Trimmed MP4 saved to Gallery (Movies/AgniAI)!",
                        errorMessage = null
                    )
                }
            }.onFailure { err ->
                Log.e(TAG, "Video trim failed", err)
                _mediaStudio.update {
                    it.copy(
                        isVideoExporting = false,
                        videoExportProgress = 0f,
                        errorMessage = err.localizedMessage ?: "Video trim failed."
                    )
                }
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
