package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Face3
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.model.AgniLanguage
import com.example.model.AssistantMode
import com.example.model.ChatMessage
import com.example.model.ModelOption
import com.example.model.PendingAttachment
import com.example.model.UiState
import com.example.ui.components.MarkdownMessageView
import com.example.ui.theme.AgniAmber
import com.example.ui.theme.AgniFlame
import com.example.ui.theme.NavyAbyss
import com.example.util.AttachmentLoader
import com.example.viewmodel.AgniDestination
import com.example.viewmodel.ChatComposerState
import com.example.voice.AgniVoiceSpeaker
import com.example.voice.AssistantVoiceGender
import com.example.voice.VoiceInputManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatWorkspaceScreen(
    composer: ChatComposerState,
    messagesState: UiState<List<ChatMessage>>,
    modelCatalog: List<ModelOption>,
    showDrawerButton: Boolean,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onSelectMode: (AssistantMode) -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectLanguage: (AgniLanguage) -> Unit,
    onSelectTranslationTarget: (AgniLanguage) -> Unit,
    onToggleTransliteration: (Boolean) -> Unit,
    onSelectVoiceGender: (AssistantVoiceGender) -> Unit,
    onToggleAutoSpeakReplies: (Boolean) -> Unit,
    onDraftChange: (String) -> Unit,
    onAttachFile: (PendingAttachment) -> Unit,
    onClearAttachment: () -> Unit,
    onVoiceListeningChanged: (Boolean) -> Unit,
    onVoiceResult: (String) -> Unit,
    onVoiceConversationResult: (String) -> Unit,
    onSendMessage: (String?) -> Unit,
    onStopGeneration: () -> Unit,
    onShowError: (String) -> Unit,
    onClearError: () -> Unit,
    onOpenFeedback: () -> Unit,
    onOpenMediaStudio: () -> Unit,
    onNavigateDestination: (AgniDestination) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()

    var showMoreMenuSheet by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var pickingTranslationTarget by remember { mutableStateOf(false) }

    var isSpeaking by remember { mutableStateOf(false) }
    var speakingMessageId by remember { mutableStateOf<String?>(null) }

    // Hands-free Male & Female Live Voice Conversation Mode state
    var isLiveVoiceOpen by remember { mutableStateOf(false) }
    var pendingStartLiveVoiceAfterPerm by remember { mutableStateOf(false) }
    var liveUserTranscript by remember { mutableStateOf("") }
    var liveVoiceHint by remember { mutableStateOf<String?>(null) }
    var autoListenTrigger by remember { mutableLongStateOf(0L) }

    // On-device Male & Female Assistant Text-to-Speech Speaker
    val voiceSpeaker = remember(context) {
        AgniVoiceSpeaker(
            context = context,
            onSpeakingStateChanged = { active, msgId ->
                isSpeaking = active
                speakingMessageId = msgId
            },
            onSpeechCompleted = { finishedId ->
                // Continuous hands-free loop: once AI finishes speaking, auto-listen for next question
                if (isLiveVoiceOpen && finishedId != null &&
                    (finishedId.startsWith("latest_") || finishedId.startsWith("voice_preview_"))
                ) {
                    autoListenTrigger++
                }
            },
            onError = { err ->
                if (isLiveVoiceOpen) liveVoiceHint = err else onShowError(err)
            }
        )
    }
    DisposableEffect(voiceSpeaker) {
        onDispose { voiceSpeaker.shutdown() }
    }

    // Auto-speak fresh AI answers when completed
    var lastSpokenSeq by remember { mutableLongStateOf(composer.lastCompletedReplySeq) }
    LaunchedEffect(composer.lastCompletedReplySeq) {
        val reply = composer.lastCompletedReplyText
        if (composer.lastCompletedReplySeq > lastSpokenSeq && !reply.isNullOrBlank()) {
            lastSpokenSeq = composer.lastCompletedReplySeq
            if (isLiveVoiceOpen || composer.autoSpeakReplies) {
                liveVoiceHint = null
                voiceSpeaker.speak(
                    rawText = reply,
                    localeTag = composer.selectedLanguage.localeTag,
                    gender = composer.voiceGender,
                    messageId = "latest_${composer.lastCompletedReplySeq}"
                )
            }
        }
    }

    // Stop speaking if user starts streaming a new message
    LaunchedEffect(composer.isStreaming) {
        if (composer.isStreaming && isSpeaking) {
            voiceSpeaker.stop()
        }
    }

    // Voice Input Manager
    val voiceManager = remember(context) {
        VoiceInputManager(
            context = context,
            onPartialResult = { partial ->
                if (isLiveVoiceOpen) {
                    liveUserTranscript = partial
                    liveVoiceHint = null
                } else {
                    onDraftChange(partial)
                }
            },
            onFinalResult = { finalText ->
                if (isLiveVoiceOpen) {
                    liveUserTranscript = finalText
                    liveVoiceHint = null
                    onVoiceConversationResult(finalText)
                } else {
                    onVoiceResult(finalText)
                }
            },
            onListeningChanged = { listening -> onVoiceListeningChanged(listening) },
            onError = { err ->
                if (isLiveVoiceOpen) {
                    liveVoiceHint = "শুনতে পাইনি — আবার কথা বলতে নিচের মাইক বাটনে চাপ দিন"
                } else {
                    onShowError(err)
                }
            }
        )
    }
    DisposableEffect(voiceManager) {
        onDispose { voiceManager.stopListening() }
    }

    // Auto-reopen mic cleanly after AI finishes speaking in Live Voice Conversation Mode
    LaunchedEffect(autoListenTrigger) {
        if (autoListenTrigger > 0L && isLiveVoiceOpen && !composer.isStreaming && !isSpeaking) {
            delay(450L)
            if (isLiveVoiceOpen && !composer.isStreaming && !isSpeaking) {
                val hasPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPerm) {
                    liveVoiceHint = null
                    voiceManager.startListening(composer.selectedLanguage.localeTag)
                }
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceSpeaker.stop()
            if (pendingStartLiveVoiceAfterPerm) {
                pendingStartLiveVoiceAfterPerm = false
                isLiveVoiceOpen = true
                liveUserTranscript = ""
                liveVoiceHint = null
            }
            voiceManager.startListening(composer.selectedLanguage.localeTag)
        } else {
            pendingStartLiveVoiceAfterPerm = false
            onShowError("Microphone permission is needed for voice conversation.")
        }
    }

    fun startLiveVoiceConversation(targetGender: AssistantVoiceGender = composer.voiceGender) {
        onSelectVoiceGender(targetGender)
        voiceSpeaker.stop()
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            isLiveVoiceOpen = true
            liveUserTranscript = ""
            liveVoiceHint = null
            voiceManager.startListening(composer.selectedLanguage.localeTag)
        } else {
            pendingStartLiveVoiceAfterPerm = true
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                AttachmentLoader.loadImage(context, uri)
                    .onSuccess { onAttachFile(it) }
                    .onFailure { onShowError(it.localizedMessage ?: "Failed to load image.") }
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                AttachmentLoader.loadPdf(context, uri)
                    .onSuccess { onAttachFile(it) }
                    .onFailure { onShowError(it.localizedMessage ?: "Failed to load PDF.") }
            }
        }
    }

    val messages = (messagesState as? UiState.Success)?.data ?: emptyList()
    val activeModel = modelCatalog.firstOrNull { it.modelKey == composer.selectedModelKey }
        ?: modelCatalog.first()

    LaunchedEffect(messages.size, composer.streamingText.length) {
        val total = messages.size + if (composer.isStreaming) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    val bgBase = MaterialTheme.colorScheme.background
    val emberGlow = AgniFlame.copy(alpha = 0.14f)
    val amberGlow = AgniAmber.copy(alpha = 0.08f)

    // Full-screen atmospheric Agni AI canvas
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        bgBase,
                        bgBase,
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    )
                )
            )
    ) {
        // Subtle ambient Agni flame glow in upper backdrop
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(emberGlow, amberGlow, Color.Transparent),
                        radius = 780f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            // Minimal Floating Top Bar (Agni Emblem + Voice Pill + 3-Dot More Button)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                if (showDrawerButton) {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("drawer_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = stringResource(R.string.cd_open_drawer)
                        )
                    }
                }

                // Agni AI Brand Identity
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AgniFlame.copy(alpha = 0.5f)),
                        color = NavyAbyss
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_agni_logo_fg_1795283955599),
                            contentDescription = stringResource(R.string.cd_agni_emblem),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "AGNI AI",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "${activeModel.displayName} · ${composer.selectedLanguage.nativeName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Compact Live Voice Conversation / Stop Speaking Pill
                Surface(
                    onClick = {
                        if (isSpeaking) {
                            voiceSpeaker.stop()
                        } else {
                            startLiveVoiceConversation(composer.voiceGender)
                        }
                    },
                    shape = RoundedCornerShape(50),
                    color = if (isSpeaking) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    border = BorderStroke(
                        1.dp,
                        if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.testTag("top_voice_toggle_pill")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Rounded.StopCircle else Icons.Rounded.RecordVoiceOver,
                            contentDescription = if (isSpeaking) stringResource(R.string.cd_stop_speaking)
                            else stringResource(R.string.cd_start_voice_call),
                            tint = if (isSpeaking) Color.White else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isSpeaking) "Stop" else "কথা (${composer.voiceGender.bengaliLabel})",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSpeaking) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.width(2.dp))

                // 3-Dot More Options Button (Houses Models, Modes, Language, Voice Settings, Studio, Attachments)
                IconButton(
                    onClick = { showMoreMenuSheet = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("more_options_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.cd_more_options)
                    )
                }
            }

            // Error Banner
            AnimatedVisibility(visible = composer.errorBanner != null) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = composer.errorBanner.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onClearError, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss error",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Full-Screen Main Canvas (Welcome Showcase OR Live Chat Feed)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    messagesState is UiState.Loading -> {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    messages.isEmpty() && !composer.isStreaming -> {
                        FullScreenWelcomeShowcase(
                            mode = composer.selectedMode,
                            language = composer.selectedLanguage,
                            voiceGender = composer.voiceGender,
                            onStartLiveVoice = { gender -> startLiveVoiceConversation(gender) },
                            onSelectPrompt = { prompt -> onSendMessage(prompt) }
                        )
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 820.dp)
                        ) {
                            items(messages, key = { it.id.ifBlank { "${it.role}_${it.content.hashCode()}" } }) { msg ->
                                val msgKey = msg.id.ifBlank { "${msg.role}_${msg.content.hashCode()}" }
                                val isThisSpeaking = isSpeaking && speakingMessageId == msgKey
                                ChatMessageBubble(
                                    message = msg,
                                    isSpeakingThis = isThisSpeaking,
                                    voiceGender = composer.voiceGender,
                                    onToggleSpeak = {
                                        if (isThisSpeaking) {
                                            voiceSpeaker.stop()
                                        } else {
                                            voiceSpeaker.speak(
                                                rawText = msg.content,
                                                localeTag = composer.selectedLanguage.localeTag,
                                                gender = composer.voiceGender,
                                                messageId = msgKey
                                            )
                                        }
                                    },
                                    onCopy = {
                                        clipboard.setText(AnnotatedString(msg.content))
                                    },
                                    onFeedback = onOpenFeedback
                                )
                            }
                            if (composer.isStreaming) {
                                item(key = "streaming_bubble") {
                                    StreamingAssistantBubble(
                                        text = composer.streamingText,
                                        citations = composer.streamingCitations,
                                        modelName = activeModel.displayName,
                                        mode = composer.selectedMode
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Voice Listening Status Banner
            AnimatedVisibility(visible = composer.isVoiceListening) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.listening_voice),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "${composer.selectedLanguage.displayName} (${composer.selectedLanguage.nativeName})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                        TextButton(onClick = { voiceManager.stopListening() }) {
                            Text("Stop")
                        }
                    }
                }
            }

            // Pending Attachment Preview Chip (when Image or PDF attached via 3-dot menu)
            AnimatedVisibility(visible = composer.pendingAttachment != null) {
                composer.pendingAttachment?.let { attachment ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (attachment.isImage && attachment.bitmap != null) {
                                Image(
                                    bitmap = attachment.bitmap.asImageBitmap(),
                                    contentDescription = attachment.fileName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.PictureAsPdf,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = attachment.fileName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${if (attachment.isPdf) "PDF" else "Image"} · ${formatBytes(attachment.sizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = onClearAttachment) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.cd_remove_attachment)
                                )
                            }
                        }
                    }
                }
            }

            // Sleek Floating Bottom Search / Ask Bar (Clean single bar at bottom)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                val canSend = composer.draftText.isNotBlank() || composer.pendingAttachment != null
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(
                        1.dp,
                        if (composer.isVoiceListening) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 820.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        OutlinedTextField(
                            value = composer.draftText,
                            onValueChange = onDraftChange,
                            placeholder = {
                                Text(
                                    text = modePlaceholder(composer.selectedMode, composer.selectedLanguage),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(21.dp)
                                )
                            },
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (!composer.isStreaming && canSend) onSendMessage(null)
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_prompt_input")
                        )

                        // Voice Mic Button inside Search Bar
                        IconButton(
                            onClick = {
                                if (composer.isVoiceListening) {
                                    voiceManager.stopListening()
                                } else {
                                    voiceSpeaker.stop()
                                    val hasPerm = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPerm) {
                                        voiceManager.startListening(composer.selectedLanguage.localeTag)
                                    } else {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("voice_input_button")
                        ) {
                            Icon(
                                imageVector = if (composer.isVoiceListening) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                contentDescription = stringResource(R.string.cd_voice_input),
                                tint = if (composer.isVoiceListening) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Send / Stop Action Button
                        Surface(
                            onClick = {
                                if (composer.isStreaming) onStopGeneration()
                                else if (canSend) {
                                    voiceSpeaker.stop()
                                    onSendMessage(null)
                                }
                            },
                            shape = CircleShape,
                            color = when {
                                composer.isStreaming -> MaterialTheme.colorScheme.error
                                canSend -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .testTag("send_message_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (composer.isStreaming) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = stringResource(R.string.cd_send_message),
                                    tint = if (composer.isStreaming || canSend) Color.White
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 3-Dot More Options Bottom Sheet
    if (showMoreMenuSheet) {
        AgniMoreOptionsSheet(
            composer = composer,
            activeModel = activeModel,
            onDismiss = { showMoreMenuSheet = false },
            onSelectVoiceGender = onSelectVoiceGender,
            onToggleAutoSpeak = onToggleAutoSpeakReplies,
            onTestVoice = { gender ->
                voiceSpeaker.previewVoice(gender, composer.selectedLanguage.localeTag)
            },
            onNewChat = {
                voiceSpeaker.stop()
                onNewChat()
            },
            onAttachImage = {
                imagePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onAttachPdf = {
                pdfPickerLauncher.launch(arrayOf("application/pdf"))
            },
            onOpenMediaStudio = onOpenMediaStudio,
            onOpenModelPicker = { showModelSheet = true },
            onOpenLanguagePicker = {
                pickingTranslationTarget = false
                showLanguageSheet = true
            },
            onOpenTranslationTargetPicker = {
                pickingTranslationTarget = true
                showLanguageSheet = true
            },
            onSelectMode = onSelectMode,
            onToggleTransliteration = onToggleTransliteration,
            onNavigateDestination = onNavigateDestination
        )
    }

    // Model Router Selection Bottom Sheet
    if (showModelSheet) {
        AgniModelPickerSheet(
            modelCatalog = modelCatalog,
            selectedModelKey = composer.selectedModelKey,
            onSelectModel = onSelectModel,
            onDismiss = { showModelSheet = false }
        )
    }

    // Multilingual Language Picker Bottom Sheet
    if (showLanguageSheet) {
        AgniLanguagePickerSheet(
            pickingTranslationTarget = pickingTranslationTarget,
            selectedLanguage = composer.selectedLanguage,
            translationTarget = composer.translationTarget,
            onSelect = { lang ->
                if (pickingTranslationTarget) onSelectTranslationTarget(lang)
                else onSelectLanguage(lang)
            },
            onDismiss = { showLanguageSheet = false }
        )
    }

    // Full-Screen Live Male / Female Voice Conversation Overlay
    if (isLiveVoiceOpen) {
        val displayedReply = when {
            composer.isStreaming && composer.streamingText.isNotBlank() -> composer.streamingText
            !composer.lastCompletedReplyText.isNullOrBlank() -> composer.lastCompletedReplyText
            else -> messages.lastOrNull { it.role == "assistant" }?.content.orEmpty()
        }
        LiveVoiceConversationOverlay(
            language = composer.selectedLanguage,
            voiceGender = composer.voiceGender,
            isListening = composer.isVoiceListening,
            isThinking = composer.isStreaming,
            isSpeaking = isSpeaking,
            liveUserTranscript = liveUserTranscript,
            lastAssistantReply = displayedReply,
            statusHint = liveVoiceHint,
            onSelectVoiceGender = { gender ->
                onSelectVoiceGender(gender)
                voiceManager.stopListening()
                voiceSpeaker.previewVoice(gender, composer.selectedLanguage.localeTag)
            },
            onTapMicOrInterrupt = {
                if (composer.isVoiceListening) {
                    voiceManager.stopListening()
                } else {
                    voiceSpeaker.stop()
                    if (composer.isStreaming) onStopGeneration()
                    liveVoiceHint = null
                    voiceManager.startListening(composer.selectedLanguage.localeTag)
                }
            },
            onStopSpeaking = {
                voiceSpeaker.stop()
            },
            onEndConversation = {
                voiceManager.stopListening()
                voiceSpeaker.stop()
                isLiveVoiceOpen = false
                liveVoiceHint = null
            }
        )
    }
}

@Composable
private fun FullScreenWelcomeShowcase(
    mode: AssistantMode,
    language: AgniLanguage,
    voiceGender: AssistantVoiceGender,
    onStartLiveVoice: (AssistantVoiceGender) -> Unit,
    onSelectPrompt: (String) -> Unit
) {
    val prompts = remember(mode, language) { starterPromptsFor(mode, language) }
    val greeting = remember(language) { greetingFor(language) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 760.dp)
    ) {
        // Expansive Full-Screen Hero Artwork Card
        item {
            Surface(
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, AgniFlame.copy(alpha = 0.35f)),
                shadowElevation = 10.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_agni_hero_banner_1795283950260),
                        contentDescription = stringResource(R.string.cd_hero_banner),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(236.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(236.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        NavyAbyss.copy(alpha = 0.35f),
                                        NavyAbyss.copy(alpha = 0.78f),
                                        NavyAbyss.copy(alpha = 0.96f)
                                    )
                                )
                            )
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(236.dp)
                            .padding(horizontal = 20.dp, vertical = 20.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.5.dp, AgniFlame.copy(alpha = 0.8f)),
                            color = NavyAbyss,
                            shadowElevation = 8.dp
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_agni_logo_fg_1795283955599),
                                contentDescription = stringResource(R.string.cd_agni_emblem),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(RoundedCornerShape(18.dp))
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = mode.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD0DCF0),
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
            }
        }

        // Direct 1-Tap Male & Female Live Voice Conversation Launchers on Welcome Canvas
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    WelcomeVoicePill(
                        icon = Icons.Rounded.Face3,
                        label = "মেয়ে ভয়েসে কথা বলুন",
                        selected = voiceGender == AssistantVoiceGender.FEMALE,
                        testTag = "welcome_female_voice_pill",
                        onClick = { onStartLiveVoice(AssistantVoiceGender.FEMALE) },
                        modifier = Modifier.weight(1f)
                    )
                    WelcomeVoicePill(
                        icon = Icons.Rounded.Face,
                        label = "ছেলে ভয়েসে কথা বলুন",
                        selected = voiceGender == AssistantVoiceGender.MALE,
                        testTag = "welcome_male_voice_pill",
                        onClick = { onStartLiveVoice(AssistantVoiceGender.MALE) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Sleek Quick Starters
        items(prompts) { (title, prompt) ->
            Surface(
                onClick = { onSelectPrompt(prompt) },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.9f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (mode == AssistantMode.CHAT) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_agni_flame),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = modeIcon(mode),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeVoicePill(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier.testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    isSpeakingThis: Boolean,
    voiceGender: AssistantVoiceGender,
    onToggleSpeak: () -> Unit,
    onCopy: () -> Unit,
    onFeedback: () -> Unit
) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            Surface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.widthIn(max = 520.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (!message.attachmentName.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (message.attachmentMimeType == "application/pdf") {
                                        Icons.Rounded.PictureAsPdf
                                    } else {
                                        Icons.Rounded.Image
                                    },
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = message.attachmentName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    1.dp,
                    if (isSpeakingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_agni_logo_fg_1795283955599),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AGNI AI · ${AssistantMode.fromKey(message.mode).shortLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = message.modelId,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    MarkdownMessageView(
                        content = message.content,
                        citations = message.citations
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onToggleSpeak,
                            modifier = Modifier.testTag("message_speak_button")
                        ) {
                            Icon(
                                imageVector = if (isSpeakingThis) Icons.Rounded.StopCircle else Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = if (isSpeakingThis) stringResource(R.string.cd_stop_speaking)
                                else stringResource(R.string.cd_speak_message),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isSpeakingThis) "Stop" else "শুনুন (${voiceGender.bengaliLabel})",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        TextButton(onClick = onCopy) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = stringResource(R.string.cd_copy_message),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick = onFeedback) {
                            Text("Feedback", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingAssistantBubble(
    text: String,
    citations: List<String>,
    modelName: String,
    mode: AssistantMode
) {
    Surface(
        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "$modelName · ${mode.label}…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (text.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                MarkdownMessageView(content = text, citations = citations)
            }
        }
    }
}

fun modeIcon(mode: AssistantMode): ImageVector = when (mode) {
    AssistantMode.CHAT -> Icons.Rounded.LocalFireDepartment
    AssistantMode.TRANSLATION -> Icons.Rounded.Translate
    AssistantMode.EDUCATION -> Icons.Rounded.School
    AssistantMode.MATH -> Icons.Rounded.Calculate
    AssistantMode.CODING -> Icons.Rounded.Code
    AssistantMode.RESEARCH -> Icons.Rounded.Public
}

private fun modePlaceholder(mode: AssistantMode, language: AgniLanguage): String = when (mode) {
    AssistantMode.CHAT -> if (language == AgniLanguage.BENGALI) "অগ্নি এ আই-কে প্রশ্ন করুন বা সার্চ করুন…" else "Search or ask Agni AI in ${language.displayName}…"
    AssistantMode.TRANSLATION -> "Enter text to translate accurately…"
    AssistantMode.EDUCATION -> "Ask any concept to learn step-by-step…"
    AssistantMode.MATH -> "Type an equation or attach from ⋮ menu…"
    AssistantMode.CODING -> "Describe a feature, bug, or paste code…"
    AssistantMode.RESEARCH -> "Ask a topic for web-grounded research…"
}

private fun greetingFor(language: AgniLanguage): String = when (language) {
    AgniLanguage.BENGALI -> "স্বাগতম! আজ কী জানতে চান?"
    AgniLanguage.HINDI -> "नमस्ते! आज आप क्या जानना चाहते हैं?"
    AgniLanguage.TAMIL -> "வணக்கம்! இன்று என்ன தெரிந்துகொள்ள விரும்புகிறீர்கள்?"
    AgniLanguage.TELUGU -> "నమస్కారం! ఈరోజు మీరు ఏమి తెలుసుకోవాలనుకుంటున్నారు?"
    AgniLanguage.MARATHI -> "नमस्कार! आज तुम्हाला काय जाणून घ्यायचे आहे?"
    AgniLanguage.GUJARATI -> "નમસ્તે! આજે તમે શું જાણવા માંગો છો?"
    AgniLanguage.KANNADA -> "ನಮಸ್ಕಾರ! ಇಂದು ನೀವು ಏನು ತಿಳಿಯಲು ಬಯಸುತ್ತೀರಿ?"
    AgniLanguage.MALAYALAM -> "നമസ്കാരം! ഇന്ന് നിങ്ങൾക്ക് എന്താണ് അറിയേണ്ടത്?"
    AgniLanguage.PUNJABI -> "ਸਤ ਸ੍ਰੀ ਅਕਾਲ! ਅੱਜ ਤੁਸੀਂ ਕੀ ਸਿੱਖਣਾ ਚਾਹੁੰਦੇ ਹੋ?"
    AgniLanguage.URDU -> "خوش آمدید! آج آپ کیا جاننا چاہتے ہیں؟"
    AgniLanguage.ODIA -> "ନମସ୍କାର! ଆଜି ଆପଣ କଣ ଜାଣିବାକୁ ଚାହୁଁଛନ୍ତି?"
    AgniLanguage.ENGLISH -> "One AI. Many Possibilities."
}

private fun starterPromptsFor(mode: AssistantMode, language: AgniLanguage): List<Pair<String, String>> =
    when (mode) {
        AssistantMode.CHAT -> listOf(
            "Multilingual Daily Brief" to "Explain how renewable solar microgrids can transform rural Indian villages in ${language.displayName}.",
            "Analyze Image or PDF" to "Explain what kinds of documents, handwritten notes, or diagrams I can upload to Agni AI for instant analysis.",
            "Creative Writing" to "Write a short, inspiring morning message in ${language.displayName} with a poetic touch."
        )
        AssistantMode.TRANSLATION -> listOf(
            "Business Greeting" to "Translate: 'Thank you for partnering with us. We look forward to building great technology together.'",
            "Idiomatic Expression" to "Translate naturally with cultural nuance: 'Where there is a will, there is a way.'",
            "Travel Essentials" to "Create 5 polite everyday phrases for navigating a new city with phonetic pronunciation."
        )
        AssistantMode.EDUCATION -> listOf(
            "Quantum Computing Intuition" to "Teach me how qubits differ from classical bits using a simple everyday analogy in ${language.displayName}.",
            "Photosynthesis Mastery" to "Break down light-dependent and Calvin cycle reactions step-by-step and quiz me at the end.",
            "Indian Constitutional History" to "Summarize the core principles of the Preamble and why each pillar matters today."
        )
        AssistantMode.MATH -> listOf(
            "Calculus Integration" to "Solve the definite integral of (3x^2 + 2x - 5) dx from x = 1 to x = 4 step-by-step and verify.",
            "Linear Algebra" to "Find the eigenvalues and eigenvectors of the 2x2 matrix [[4, 1], [2, 3]] showing every step.",
            "Probability Problem" to "Two fair dice are rolled. What is the probability that the sum is a prime number? Show all outcomes."
        )
        AssistantMode.CODING -> listOf(
            "FastAPI Modular Router" to "Write a clean Python FastAPI router example with dependency injection, rate limiting hook, and Pydantic validation.",
            "Kotlin Coroutines Flow" to "Show how to implement a debounced search bar using Kotlin StateFlow and flatMapLatest in Jetpack Compose.",
            "Algorithm Optimization" to "Explain and implement Dijkstra's shortest path algorithm in Python with complexity analysis."
        )
        AssistantMode.RESEARCH -> listOf(
            "ISRO Space Missions" to "Provide a structured research brief on India's latest ISRO lunar and human spaceflight milestones.",
            "Open-Source LLMs & PEFT" to "Compare QLoRA, LoRA, and full fine-tuning for adapting multilingual open-source LLMs for Indian languages.",
            "Climate & Clean Tech" to "Summarize current advances in sodium-ion batteries vs lithium-iron-phosphate (LFP) storage."
        )
    }

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576L -> String.format("%.1f MB", bytes / 1_048_576.0)
    bytes >= 1_024L -> String.format("%.1f KB", bytes / 1_024.0)
    else -> "$bytes B"
}
