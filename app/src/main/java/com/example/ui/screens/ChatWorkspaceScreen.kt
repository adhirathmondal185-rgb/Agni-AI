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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NavyAbyss
import com.example.util.AttachmentLoader
import com.example.viewmodel.ChatComposerState
import com.example.voice.VoiceInputManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    onDraftChange: (String) -> Unit,
    onAttachFile: (PendingAttachment) -> Unit,
    onClearAttachment: () -> Unit,
    onVoiceListeningChanged: (Boolean) -> Unit,
    onVoiceResult: (String) -> Unit,
    onSendMessage: (String?) -> Unit,
    onStopGeneration: () -> Unit,
    onShowError: (String) -> Unit,
    onClearError: () -> Unit,
    onOpenFeedback: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()

    var showModelSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var pickingTranslationTarget by remember { mutableStateOf(false) }

    // Voice Input Manager
    val voiceManager = remember(context) {
        VoiceInputManager(
            context = context,
            onPartialResult = { partial -> onDraftChange(partial) },
            onFinalResult = { finalText -> onVoiceResult(finalText) },
            onListeningChanged = { listening -> onVoiceListeningChanged(listening) },
            onError = { err -> onShowError(err) }
        )
    }
    DisposableEffect(voiceManager) {
        onDispose { voiceManager.stopListening() }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceManager.startListening(composer.selectedLanguage.localeTag)
        } else {
            onShowError("Microphone permission is needed for multilingual voice input.")
        }
    }

    // Zero-permission Photo Picker for Image Analysis
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

    // Zero-permission Storage Access Framework picker for PDF Analysis
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        // Top Header Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showDrawerButton) {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.testTag("drawer_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = stringResource(R.string.cd_open_drawer)
                            )
                        }
                    }

                    // Model Router Pill Selector
                    Surface(
                        onClick = { showModelSheet = true },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("model_selector_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(AgniFlame)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeModel.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${composer.selectedLanguage.nativeName} · ${composer.selectedMode.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Select AI Model",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Language Selector Chip
                    Surface(
                        onClick = {
                            pickingTranslationTarget = false
                            showLanguageSheet = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.testTag("language_selector_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Language,
                                contentDescription = "Select language",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = composer.selectedLanguage.nativeName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }

                    IconButton(
                        onClick = onNewChat,
                        modifier = Modifier.testTag("top_new_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = stringResource(R.string.cd_new_chat),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Specialist Mode Switcher Ribbon
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(AssistantMode.entries) { mode ->
                        val selected = mode == composer.selectedMode
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectMode(mode) },
                            label = {
                                Text(
                                    text = mode.shortLabel,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = modeIcon(mode),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("mode_chip_${mode.key}")
                        )
                    }
                }

                // Translation Studio Bar when in Translation Mode
                AnimatedVisibility(visible = composer.selectedMode == AssistantMode.TRANSLATION) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Target:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    onClick = {
                                        pickingTranslationTarget = true
                                        showLanguageSheet = true
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.SwapHoriz,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${composer.translationTarget.displayName} (${composer.translationTarget.nativeName})",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Phonetics",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Switch(
                                    checked = composer.includeTransliteration,
                                    onCheckedChange = onToggleTransliteration
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        // Error Banner
        AnimatedVisibility(visible = composer.errorBanner != null) {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
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

        // Main Chat Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            when {
                messagesState is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                messages.isEmpty() && !composer.isStreaming -> {
                    WelcomeEmptyState(
                        mode = composer.selectedMode,
                        language = composer.selectedLanguage,
                        onSelectPrompt = { prompt -> onSendMessage(prompt) }
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 820.dp)
                    ) {
                        items(messages, key = { it.id.ifBlank { "${it.role}_${it.content.hashCode()}" } }) { msg ->
                            ChatMessageBubble(
                                message = msg,
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

        // Voice Listening Banner
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
                                text = "Locale: ${composer.selectedLanguage.displayName} (${composer.selectedLanguage.localeTag})",
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

        // Pending Attachment Preview Chip (Image or PDF)
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
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(48.dp)
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
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = attachment.fileName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${if (attachment.isPdf) "PDF Document" else "Image"} · ${formatBytes(attachment.sizeBytes)} · Ready for AI analysis",
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

        // Elevated Multimodal Composer Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
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
                        maxLines = 4,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_prompt_input")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    val canSend = composer.draftText.isNotBlank() || composer.pendingAttachment != null
                    Surface(
                        onClick = {
                            if (composer.isStreaming) onStopGeneration()
                            else if (canSend) onSendMessage(null)
                        },
                        shape = CircleShape,
                        color = when {
                            composer.isStreaming -> MaterialTheme.colorScheme.error
                            canSend -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier
                            .size(50.dp)
                            .testTag("send_message_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (composer.isStreaming) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                                contentDescription = stringResource(R.string.cd_send_message),
                                tint = if (composer.isStreaming || canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Multimodal Action Toolbar (Image, PDF, Voice, Web indicator)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ComposerToolChip(
                            icon = Icons.Rounded.Image,
                            label = "Image",
                            testTag = "attach_image_button",
                            onClick = {
                                imagePickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                        ComposerToolChip(
                            icon = Icons.Rounded.PictureAsPdf,
                            label = "PDF",
                            testTag = "attach_pdf_button",
                            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }
                        )
                        ComposerToolChip(
                            icon = if (composer.isVoiceListening) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                            label = if (composer.isVoiceListening) "Stop" else "Voice",
                            highlighted = composer.isVoiceListening,
                            testTag = "voice_input_button",
                            onClick = {
                                if (composer.isVoiceListening) {
                                    voiceManager.stopListening()
                                } else {
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
                            }
                        )
                    }

                    if (composer.selectedMode == AssistantMode.RESEARCH) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Web Grounding",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }
    }

    // Model Router Selection Bottom Sheet
    if (showModelSheet) {
        ModalBottomSheet(onDismissRequest = { showModelSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Agni AI Model Router",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Switch models anytime without changing your workflow. Inference credentials stay off-device via Firebase App Check.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                modelCatalog.forEach { option ->
                    val isSelected = option.modelKey == composer.selectedModelKey
                    Surface(
                        onClick = {
                            onSelectModel(option.modelKey)
                            showModelSheet = false
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = option.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (option.isLiveReady) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    else MaterialTheme.colorScheme.outlineVariant
                                ) {
                                    Text(
                                        text = option.badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (option.isLiveReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Provider: ${option.provider}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (option.isLiveReady) EmeraldSuccess else AgniAmber
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Multilingual Language Picker Bottom Sheet
    if (showLanguageSheet) {
        ModalBottomSheet(onDismissRequest = { showLanguageSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = if (pickingTranslationTarget) "Select Target Translation Language"
                    else "Select Assistant & Voice Language",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(380.dp)
                ) {
                    items(AgniLanguage.entries) { lang ->
                        val active = if (pickingTranslationTarget) {
                            lang == composer.translationTarget
                        } else {
                            lang == composer.selectedLanguage
                        }
                        Surface(
                            onClick = {
                                if (pickingTranslationTarget) onSelectTranslationTarget(lang)
                                else onSelectLanguage(lang)
                                showLanguageSheet = false
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(
                                1.dp,
                                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "${lang.displayName}  ·  ${lang.nativeName}",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Voice Locale: ${lang.localeTag}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (active) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun WelcomeEmptyState(
    mode: AssistantMode,
    language: AgniLanguage,
    onSelectPrompt: (String) -> Unit
) {
    val prompts = remember(mode, language) { starterPromptsFor(mode, language) }
    val greeting = remember(language) { greetingFor(language) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 760.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_agni_hero_banner_1795283950260),
                        contentDescription = stringResource(R.string.cd_hero_banner),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(168.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(168.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        NavyAbyss.copy(alpha = 0.94f),
                                        NavyAbyss.copy(alpha = 0.68f),
                                        NavyAbyss.copy(alpha = 0.28f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_agni_logo_fg_1795283955599),
                                contentDescription = stringResource(R.string.cd_agni_emblem),
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "AGNI AI · ${mode.label.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AgniAmber
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                        Text(
                            text = mode.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFD0DCF0)
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "QUICK STARTERS (${language.nativeName})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(prompts) { (title, prompt) ->
            Surface(
                onClick = { onSelectPrompt(prompt) },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = modeIcon(mode),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
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
private fun ChatMessageBubble(
    message: ChatMessage,
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
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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

@Composable
private fun ComposerToolChip(
    icon: ImageVector,
    label: String,
    testTag: String,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

fun modeIcon(mode: AssistantMode): ImageVector = when (mode) {
    AssistantMode.CHAT -> Icons.Rounded.AutoAwesome
    AssistantMode.TRANSLATION -> Icons.Rounded.Translate
    AssistantMode.EDUCATION -> Icons.Rounded.School
    AssistantMode.MATH -> Icons.Rounded.Calculate
    AssistantMode.CODING -> Icons.Rounded.Code
    AssistantMode.RESEARCH -> Icons.Rounded.Public
}

private fun modePlaceholder(mode: AssistantMode, language: AgniLanguage): String = when (mode) {
    AssistantMode.CHAT -> "Ask Agni AI in ${language.displayName}…"
    AssistantMode.TRANSLATION -> "Enter text to translate accurately…"
    AssistantMode.EDUCATION -> "Ask any concept to learn step-by-step…"
    AssistantMode.MATH -> "Type an equation or attach a photo/PDF…"
    AssistantMode.CODING -> "Describe a feature, bug, or paste code…"
    AssistantMode.RESEARCH -> "Ask a topic for web-grounded research…"
}

private fun greetingFor(language: AgniLanguage): String = when (language) {
    AgniLanguage.BENGALI -> "স্বাগতম! আজ কী তৈরি বা শিখতে চান?"
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
