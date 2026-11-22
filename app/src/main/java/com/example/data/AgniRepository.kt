package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.AiModelConfig
import com.example.model.ChatMessage
import com.example.model.ChatSession
import com.example.model.MemoryEntry
import com.example.model.ModelOption
import com.example.model.UploadedFileRecord
import com.example.model.UsageMetrics
import com.example.model.UserFeedback
import com.example.model.UserProfile
import com.example.model.UserSettings
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val PREFERENCES_DOC_ID = "preferences"
private const val USAGE_SUMMARY_DOC_ID = "summary"

class AgniRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    // --- 1. USER PROFILE & INITIALIZATION ---

    suspend fun ensureUserInitialized(
        displayName: String,
        email: String?,
        photoUrl: String?,
        defaultCatalog: List<ModelOption>
    ): Result<Boolean> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid)
            path = docRef.path

            val safeName = displayName.trim().ifBlank { "Agni Explorer" }.take(80)
            val existingUser = docRef.get().await()
            val isNewAccount = !existingUser.exists()
            if (isNewAccount) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to email?.take(160),
                    "photoUrl" to photoUrl?.take(500),
                    "preferredLanguage" to "English",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            }

            // Ensure settings/preferences document exists
            val settingsRef = docRef.collection("settings").document(PREFERENCES_DOC_ID)
            path = settingsRef.path
            if (!settingsRef.get().await().exists()) {
                settingsRef.set(
                    mapOf(
                        "userId" to uid,
                        "themeMode" to "dark",
                        "preferredLanguage" to "English",
                        "defaultModelId" to "gemini-3.8-flash",
                        "defaultMode" to "chat",
                        "voiceAutoSend" to false,
                        "memoryEnabled" to true,
                        "webResearchEnabled" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // Ensure usage/summary document exists
            val usageRef = docRef.collection("usage").document(USAGE_SUMMARY_DOC_ID)
            path = usageRef.path
            if (!usageRef.get().await().exists()) {
                usageRef.set(
                    mapOf(
                        "userId" to uid,
                        "totalMessages" to 0L,
                        "totalImagesAnalyzed" to 0L,
                        "totalPdfsAnalyzed" to 0L,
                        "totalVoiceInputs" to 0L,
                        "totalTranslations" to 0L,
                        "estimatedTokens" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // Seed ai_models collection if empty
            val modelsCol = docRef.collection("ai_models")
            path = modelsCol.path
            val existingModels = modelsCol.get().await()
            if (existingModels.isEmpty) {
                defaultCatalog.forEach { option ->
                    val modelDocId = option.modelKey.replace(".", "_")
                    modelsCol.document(modelDocId).set(
                        mapOf(
                            "userId" to uid,
                            "modelKey" to option.modelKey,
                            "displayName" to option.displayName.take(80),
                            "provider" to option.provider.take(64),
                            "tier" to option.tier,
                            "enabled" to option.isLiveReady,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            Result.success(isNewAccount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            userDoc(userId).snapshots()
                .map { snap ->
                    snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error -> logFlowError(error, userId, OperationType.GET, path); throw error }
        )
    }

    // --- 2. CHATS ---

    fun observeChats(userId: String): Flow<List<ChatSession>> = flow {
        val path = "users/$userId/chats"
        emitAll(
            userDoc(userId).collection("chats")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareByDescending<ChatSession> { it.pinned }
                                .thenByDescending { it.updatedAt?.seconds ?: 0L }
                        )
                }
                .catch { error -> logFlowError(error, userId, OperationType.LIST, path); throw error }
        )
    }

    suspend fun createChat(
        title: String,
        mode: String,
        modelId: String,
        language: String
    ): Result<String> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/chats"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("chats").document()
            path = docRef.path
            val cleanTitle = title.trim().ifBlank { "New Conversation" }.take(120)
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "title" to cleanTitle,
                    "mode" to mode,
                    "modelId" to modelId.take(64),
                    "language" to language.take(40),
                    "messageCount" to 0L,
                    "lastMessagePreview" to "",
                    "pinned" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateChatMetadata(
        chatId: String,
        title: String? = null,
        mode: String? = null,
        modelId: String? = null,
        language: String? = null,
        preview: String? = null,
        incrementMessagesBy: Long = 0L,
        pinned: Boolean? = null
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/chats/$chatId"
        return try {
            val uid = requireUserId()
            require(chatId.isNotBlank()) { "Chat ID cannot be blank." }
            val docRef = userDoc(uid).collection("chats").document(chatId)
            path = docRef.path
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (title != null) updates["title"] = title.trim().ifBlank { "Conversation" }.take(120)
            if (mode != null) updates["mode"] = mode
            if (modelId != null) updates["modelId"] = modelId.take(64)
            if (language != null) updates["language"] = language.take(40)
            if (preview != null) updates["lastMessagePreview"] = preview.take(300)
            if (incrementMessagesBy > 0L) updates["messageCount"] = FieldValue.increment(incrementMessagesBy)
            if (pinned != null) updates["pinned"] = pinned

            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteChat(chatId: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/chats/$chatId"
        return try {
            val uid = requireUserId()
            val chatRef = userDoc(uid).collection("chats").document(chatId)
            path = chatRef.path
            // Delete child messages first
            val messages = chatRef.collection("messages").get().await()
            messages.documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            chatRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun clearAllChats(): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/chats"
        return try {
            val uid = requireUserId()
            val chats = userDoc(uid).collection("chats").get().await()
            for (chatDoc in chats.documents) {
                deleteChat(chatDoc.id).getOrThrow()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- 3. MESSAGES ---

    fun observeMessages(userId: String, chatId: String): Flow<List<ChatMessage>> = flow {
        val path = "users/$userId/chats/$chatId/messages"
        emitAll(
            userDoc(userId).collection("chats").document(chatId).collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error -> logFlowError(error, userId, OperationType.LIST, path); throw error }
        )
    }

    suspend fun addMessage(
        chatId: String,
        role: String,
        content: String,
        mode: String,
        modelId: String,
        language: String,
        attachmentName: String? = null,
        attachmentMimeType: String? = null,
        citations: List<String> = emptyList()
    ): Result<String> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/chats/$chatId/messages"
        return try {
            val uid = requireUserId()
            require(chatId.isNotBlank()) { "Chat ID cannot be blank." }
            val safeContent = content.trim().take(32000)
            require(safeContent.isNotEmpty()) { "Message content cannot be blank." }

            val docRef = userDoc(uid).collection("chats").document(chatId).collection("messages").document()
            path = docRef.path
            val payload = mutableMapOf<String, Any?>(
                "userId" to uid,
                "chatId" to chatId,
                "role" to role,
                "content" to safeContent,
                "mode" to mode,
                "modelId" to modelId.take(64),
                "language" to language.take(40),
                "attachmentName" to attachmentName?.take(200),
                "attachmentMimeType" to attachmentMimeType?.take(80),
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (citations.isNotEmpty()) {
                payload["citations"] = citations.take(20)
            }
            docRef.set(payload.filterValues { it != null }).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- 4. FILES ---

    fun observeFiles(userId: String): Flow<List<UploadedFileRecord>> = flow {
        val path = "users/$userId/files"
        emitAll(
            userDoc(userId).collection("files")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(UploadedFileRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error -> logFlowError(error, userId, OperationType.LIST, path); throw error }
        )
    }

    suspend fun recordUploadedFile(
        chatId: String?,
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
        analysisSummary: String?
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/files"
        return try {
            val uid = requireUserId()
            val allowedMimes = setOf("image/jpeg", "image/png", "image/webp", "application/pdf")
            val safeMime = if (mimeType in allowedMimes) mimeType else "image/jpeg"
            val safeSize = sizeBytes.coerceIn(1L, 20_971_520L)

            val docRef = userDoc(uid).collection("files").document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "chatId" to chatId?.take(128),
                "fileName" to fileName.trim().ifBlank { "attachment" }.take(200),
                "mimeType" to safeMime,
                "sizeBytes" to safeSize,
                "analysisSummary" to analysisSummary?.trim()?.take(1000),
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteFileRecord(fileId: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/files/$fileId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("files").document(fileId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- 5. SETTINGS ---

    fun observeSettings(userId: String): Flow<UserSettings> = flow {
        val path = "users/$userId/settings/$PREFERENCES_DOC_ID"
        emitAll(
            userDoc(userId).collection("settings").document(PREFERENCES_DOC_ID)
                .snapshots()
                .map { snap ->
                    snap.toObject(UserSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: UserSettings(userId = userId)
                }
                .catch { error -> logFlowError(error, userId, OperationType.GET, path); throw error }
        )
    }

    suspend fun updateSettings(
        themeMode: String? = null,
        preferredLanguage: String? = null,
        defaultModelId: String? = null,
        defaultMode: String? = null,
        voiceAutoSend: Boolean? = null,
        memoryEnabled: Boolean? = null,
        webResearchEnabled: Boolean? = null
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/settings/$PREFERENCES_DOC_ID"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("settings").document(PREFERENCES_DOC_ID)
            path = docRef.path
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            if (themeMode != null) updates["themeMode"] = themeMode
            if (preferredLanguage != null) updates["preferredLanguage"] = preferredLanguage.take(40)
            if (defaultModelId != null) updates["defaultModelId"] = defaultModelId.take(64)
            if (defaultMode != null) updates["defaultMode"] = defaultMode
            if (voiceAutoSend != null) updates["voiceAutoSend"] = voiceAutoSend
            if (memoryEnabled != null) updates["memoryEnabled"] = memoryEnabled
            if (webResearchEnabled != null) updates["webResearchEnabled"] = webResearchEnabled

            docRef.update(updates).await()

            // Also sync preferredLanguage to root user profile if changed
            if (preferredLanguage != null) {
                userDoc(uid).update(
                    mapOf(
                        "preferredLanguage" to preferredLanguage.take(40),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- 6. AI MODELS ---

    fun observeAiModels(userId: String): Flow<List<AiModelConfig>> = flow {
        val path = "users/$userId/ai_models"
        emitAll(
            userDoc(userId).collection("ai_models")
                .snapshots()
                .map { snap ->
                    snap.toObjects(AiModelConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error -> logFlowError(error, userId, OperationType.LIST, path); throw error }
        )
    }

    // --- 7. FEEDBACK ---

    fun observeFeedback(userId: String): Flow<List<UserFeedback>> = flow {
        val path = "users/$userId/feedback"
        emitAll(
            userDoc(userId).collection("feedback")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserFeedback::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error -> logFlowError(error, userId, OperationType.LIST, path); throw error }
        )
    }

    suspend fun submitFeedback(
        chatId: String?,
        rating: Int,
        category: String,
        comment: String,
        modelId: String
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/feedback"
        return try {
            val uid = requireUserId()
            val safeComment = comment.trim().take(2000)
            require(safeComment.isNotEmpty()) { "Feedback comment cannot be empty." }
            val safeRating = rating.coerceIn(1, 5).toLong()

            val docRef = userDoc(uid).collection("feedback").document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "chatId" to chatId?.take(128),
                "rating" to safeRating,
                "category" to category,
                "comment" to safeComment,
                "modelId" to modelId.take(64),
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- 8. USAGE ---

    fun observeUsage(userId: String): Flow<UsageMetrics> = flow {
        val path = "users/$userId/usage/$USAGE_SUMMARY_DOC_ID"
        emitAll(
            userDoc(userId).collection("usage").document(USAGE_SUMMARY_DOC_ID)
                .snapshots()
                .map { snap ->
                    snap.toObject(UsageMetrics::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: UsageMetrics(userId = userId)
                }
                .catch { error -> logFlowError(error, userId, OperationType.GET, path); throw error }
        )
    }

    suspend fun incrementUsage(
        messagesDelta: Long = 0L,
        imagesDelta: Long = 0L,
        pdfsDelta: Long = 0L,
        voiceDelta: Long = 0L,
        translationsDelta: Long = 0L,
        tokensDelta: Long = 0L
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/usage/$USAGE_SUMMARY_DOC_ID"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("usage").document(USAGE_SUMMARY_DOC_ID)
            path = docRef.path
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            if (messagesDelta > 0L) updates["totalMessages"] = FieldValue.increment(messagesDelta)
            if (imagesDelta > 0L) updates["totalImagesAnalyzed"] = FieldValue.increment(imagesDelta)
            if (pdfsDelta > 0L) updates["totalPdfsAnalyzed"] = FieldValue.increment(pdfsDelta)
            if (voiceDelta > 0L) updates["totalVoiceInputs"] = FieldValue.increment(voiceDelta)
            if (translationsDelta > 0L) updates["totalTranslations"] = FieldValue.increment(translationsDelta)
            if (tokensDelta > 0L) updates["estimatedTokens"] = FieldValue.increment(tokensDelta)
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- 9. MEMORY ---

    fun observeMemories(userId: String): Flow<List<MemoryEntry>> = flow {
        val path = "users/$userId/memory"
        emitAll(
            userDoc(userId).collection("memory")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(MemoryEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error -> logFlowError(error, userId, OperationType.LIST, path); throw error }
        )
    }

    suspend fun addMemory(fact: String, category: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/memory"
        return try {
            val uid = requireUserId()
            val safeFact = fact.trim().take(500)
            require(safeFact.isNotEmpty()) { "Memory text cannot be empty." }
            val docRef = userDoc(uid).collection("memory").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "fact" to safeFact,
                    "category" to category,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleMemory(memoryId: String, active: Boolean): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/memory/$memoryId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("memory").document(memoryId)
            path = docRef.path
            docRef.update(
                mapOf(
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(memoryId: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/memory/$memoryId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("memory").document(memoryId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun clearAllMemories(): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/memory"
        return try {
            val uid = requireUserId()
            val docs = userDoc(uid).collection("memory").get().await()
            docs.documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun logFlowError(
        error: Throwable,
        userId: String,
        operationType: OperationType,
        path: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }
}
