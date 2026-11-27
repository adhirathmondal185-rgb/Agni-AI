package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CustomEndpointConfig
import com.example.model.AgniLanguage
import com.example.model.AssistantMode
import com.example.model.FeedbackCategory
import com.example.model.MemoryCategory
import com.example.model.MemoryEntry
import com.example.model.ModelOption
import com.example.model.UiState
import com.example.model.UploadedFileRecord
import com.example.model.UsageMetrics
import com.example.model.UserFeedback
import com.example.model.UserSettings
import com.example.ui.theme.AgniAmber
import com.example.ui.theme.EmeraldSuccess
import com.example.viewmodel.CustomConnectionTestState

@Composable
fun WorkspaceHubScreen(
    usage: UsageMetrics,
    filesState: UiState<List<UploadedFileRecord>>,
    onBack: () -> Unit,
    onLaunchMode: (AssistantMode) -> Unit,
    onDeleteFileRecord: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenTopBar(title = "Workspace Hub & Files", onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .widthIn(max = 840.dp)
                .align(Alignment.CenterHorizontally)
        ) {
            item {
                Text(
                    text = "LIVE USAGE TELEMETRY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    UsageStatCard("Messages", usage.totalMessages.toString(), Modifier.weight(1f))
                    UsageStatCard("Images", usage.totalImagesAnalyzed.toString(), Modifier.weight(1f))
                    UsageStatCard("PDFs", usage.totalPdfsAnalyzed.toString(), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    UsageStatCard("Voice Inputs", usage.totalVoiceInputs.toString(), Modifier.weight(1f))
                    UsageStatCard("Translations", usage.totalTranslations.toString(), Modifier.weight(1f))
                    UsageStatCard("Est. Tokens", usage.estimatedTokens.toString(), Modifier.weight(1f))
                }
            }

            item {
                Text(
                    text = "SPECIALIST STUDIOS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistantMode.entries.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pair.forEach { mode ->
                                Surface(
                                    onClick = { onLaunchMode(mode) },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("hub_launch_${mode.key}")
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Icon(
                                            imageVector = modeIcon(mode),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(text = mode.label, style = MaterialTheme.typography.titleSmall)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = mode.subtitle,
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
            }

            item {
                Text(
                    text = "ANALYZED IMAGES & PDF LIBRARY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            when (filesState) {
                is UiState.Loading -> {
                    item { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                }
                is UiState.Error -> {
                    item {
                        Text(
                            text = filesState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                is UiState.Success -> {
                    if (filesState.data.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No images or PDFs analyzed yet. Attach an image or PDF in chat to see its AI summary archived here.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(filesState.data, key = { it.id }) { file ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (file.mimeType == "application/pdf") {
                                                    Icons.Rounded.PictureAsPdf
                                                } else {
                                                    Icons.Rounded.Image
                                                },
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.fileName,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${file.mimeType} · ${formatBytes(file.sizeBytes)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (!file.analysisSummary.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = file.analysisSummary,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    IconButton(onClick = { onDeleteFileRecord(file.id) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete file record",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryScreen(
    memoriesState: UiState<List<MemoryEntry>>,
    memoryEnabled: Boolean,
    onBack: () -> Unit,
    onToggleGlobalMemory: (Boolean) -> Unit,
    onAddMemory: (String, MemoryCategory) -> Unit,
    onToggleMemory: (MemoryEntry) -> Unit,
    onDeleteMemory: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    var newFact by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(MemoryCategory.PREFERENCE) }

    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenTopBar(title = "Agni Memory & Context", onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .widthIn(max = 840.dp)
                .align(Alignment.CenterHorizontally)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Use Memory in AI Chats", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "You control every saved fact. Active memories personalize responses across sessions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = memoryEnabled, onCheckedChange = onToggleGlobalMemory)
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Add Persistent Memory", style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MemoryCategory.entries.forEach { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat.label) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = newFact,
                            onValueChange = { if (it.length <= 500) newFact = it },
                            label = { Text("What should Agni AI remember?") },
                            placeholder = { Text("e.g., Explain coding concepts in Bengali + English with Python examples") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("memory_fact_input")
                        )
                        Button(
                            onClick = {
                                if (newFact.isNotBlank()) {
                                    onAddMemory(newFact, selectedCategory)
                                    newFact = ""
                                }
                            },
                            enabled = newFact.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("save_memory_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Memory")
                        }
                    }
                }
            }

            when (memoriesState) {
                is UiState.Loading -> item { CircularProgressIndicator() }
                is UiState.Error -> item {
                    Text(memoriesState.message, color = MaterialTheme.colorScheme.error)
                }
                is UiState.Success -> {
                    if (memoriesState.data.isEmpty()) {
                        item {
                            Text(
                                text = "No memories stored yet. Add preferences above so Agni AI adapts to your style.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(memoriesState.data, key = { it.id }) { mem ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = MemoryCategory.fromKey(mem.category).label.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = mem.fact, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Switch(
                                        checked = mem.active,
                                        onCheckedChange = { onToggleMemory(mem) }
                                    )
                                    IconButton(onClick = { onDeleteMemory(mem.id) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete memory"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsAndFeedbackScreen(
    userDisplayName: String,
    userEmail: String?,
    settings: UserSettings,
    modelCatalog: List<ModelOption>,
    customEndpointConfig: CustomEndpointConfig,
    customTestState: CustomConnectionTestState,
    feedbackState: UiState<List<UserFeedback>>,
    onBack: () -> Unit,
    onUpdateTheme: (String) -> Unit,
    onSelectLanguage: (AgniLanguage) -> Unit,
    onSelectModel: (String) -> Unit,
    onSaveCustomEndpoint: (String, String, String, String) -> Unit,
    onTestCustomEndpoint: (String, String, String, String) -> Unit,
    onClearCustomEndpoint: () -> Unit,
    onToggleVoiceAutoSend: (Boolean) -> Unit,
    onToggleMemory: (Boolean) -> Unit,
    onToggleWebResearch: (Boolean) -> Unit,
    onSubmitFeedback: (Int, FeedbackCategory, String) -> Unit,
    onClearAllChats: () -> Unit,
    onClearAllMemories: () -> Unit,
    onSwitchAccount: () -> Unit,
    onSignOut: () -> Unit
) {
    BackHandler(onBack = onBack)

    var feedbackRating by remember { mutableIntStateOf(5) }
    var feedbackCategory by remember { mutableStateOf(FeedbackCategory.ACCURACY) }
    var feedbackComment by remember { mutableStateOf("") }
    var confirmClearChats by remember { mutableStateOf(false) }
    var confirmClearMemory by remember { mutableStateOf(false) }

    var customLabel by remember(customEndpointConfig.displayLabel) { mutableStateOf(customEndpointConfig.displayLabel) }
    var customUrl by remember(customEndpointConfig.endpointUrl) { mutableStateOf(customEndpointConfig.endpointUrl) }
    var customModelName by remember(customEndpointConfig.modelName) { mutableStateOf(customEndpointConfig.modelName) }
    var customApiKey by remember(customEndpointConfig.apiKey) { mutableStateOf(customEndpointConfig.apiKey) }
    var showApiKey by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenTopBar(title = "Settings, Models & Feedback", onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .widthIn(max = 840.dp)
                .align(Alignment.CenterHorizontally)
        ) {
            // Account Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column {
                            Text(text = userDisplayName, style = MaterialTheme.typography.titleMedium)
                            if (!userEmail.isNullOrBlank()) {
                                Text(
                                    text = userEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "Google Authenticated · Private Firestore Workspace",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSuccess
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onSwitchAccount,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_switch_account_button")
                            ) {
                                Text(stringResource(R.string.switch_account))
                            }
                            OutlinedButton(
                                onClick = onSignOut,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sign_out_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.sign_out))
                            }
                        }
                    }
                }
            }

            // Appearance (Dark / Light / System)
            item {
                SectionCard(title = "Appearance & Theme") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("dark" to "Dark Navy", "light" to "Light Crisp", "system" to "System").forEach { (key, label) ->
                            FilterChip(
                                selected = settings.themeMode == key,
                                onClick = { onUpdateTheme(key) },
                                label = { Text(label, maxLines = 1, softWrap = false) },
                                modifier = Modifier.testTag("theme_chip_$key")
                            )
                        }
                    }
                }
            }

            // Assistant Behavior Toggles
            item {
                SectionCard(title = "AI Capabilities & Preferences") {
                    SettingsToggleRow(
                        title = "Web Research Grounding",
                        subtitle = "Use live 2026 web search across Chat, Research & Learn modes.",
                        checked = settings.webResearchEnabled,
                        onCheckedChange = onToggleWebResearch
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsToggleRow(
                        title = "Persistent AI Memory",
                        subtitle = "Inject your active memory entries into chat prompts.",
                        checked = settings.memoryEnabled,
                        onCheckedChange = onToggleMemory
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsToggleRow(
                        title = "Voice Auto-Send",
                        subtitle = "Automatically send prompt immediately after voice dictation completes.",
                        checked = settings.voiceAutoSend,
                        onCheckedChange = onToggleVoiceAutoSend
                    )
                }
            }

            // Default Language Quick Picker
            item {
                SectionCard(title = "Default Language") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AgniLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = settings.preferredLanguage.equals(lang.displayName, ignoreCase = true),
                                onClick = { onSelectLanguage(lang) },
                                label = { Text("${lang.displayName} (${lang.nativeName})") }
                            )
                        }
                    }
                }
            }

            // Custom AGNI Fine-Tuned / FastAPI Open-Source Endpoint Configurator
            item {
                SectionCard(title = stringResource(R.string.custom_model_section_title)) {
                    Text(
                        text = stringResource(R.string.custom_model_section_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (customEndpointConfig.isConfigured) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = EmeraldSuccess.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudDone,
                                    contentDescription = null,
                                    tint = EmeraldSuccess,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Connected: ${customEndpointConfig.displayLabel} (${customEndpointConfig.modelName})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldSuccess,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = customLabel,
                        onValueChange = { if (it.length <= 48) customLabel = it },
                        label = { Text("Model Display Name") },
                        placeholder = { Text("Agni Fine-Tuned v1") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_model_label_input")
                    )
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        label = { Text("HTTPS Endpoint URL (FastAPI / OpenAI-Compatible)") },
                        placeholder = { Text("https://your-tunnel.trycloudflare.com/v1/chat/completions") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_model_url_input")
                    )
                    OutlinedTextField(
                        value = customModelName,
                        onValueChange = { if (it.length <= 128) customModelName = it },
                        label = { Text("Model ID") },
                        placeholder = { Text("agni-ai-v1") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_model_id_input")
                    )
                    OutlinedTextField(
                        value = customApiKey,
                        onValueChange = { customApiKey = it },
                        label = { Text("Bearer Token / API Key (Optional, stays on device)") },
                        placeholder = { Text("Leave blank if your FastAPI server has no auth") },
                        singleLine = true,
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (showApiKey) "Hide token" else "Show token"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_model_key_input")
                    )

                    when (customTestState) {
                        is CustomConnectionTestState.Idle -> Unit
                        is CustomConnectionTestState.Testing -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "Testing HTTPS connection to your custom model...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        is CustomConnectionTestState.Success -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = EmeraldSuccess.copy(alpha = 0.14f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Connected in ${customTestState.latencyMs} ms",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = EmeraldSuccess
                                        )
                                        Text(
                                            text = customTestState.preview,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        is CustomConnectionTestState.Error -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ErrorOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = customTestState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onTestCustomEndpoint(customLabel, customUrl, customModelName, customApiKey)
                            },
                            enabled = customUrl.isNotBlank() && customTestState !is CustomConnectionTestState.Testing,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("custom_model_test_button")
                        ) {
                            Text("Test")
                        }
                        Button(
                            onClick = {
                                onSaveCustomEndpoint(customLabel, customUrl, customModelName, customApiKey)
                            },
                            enabled = customUrl.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("custom_model_save_button")
                        ) {
                            Text("Save & Use")
                        }
                    }
                    if (customEndpointConfig.isConfigured) {
                        TextButton(
                            onClick = onClearCustomEndpoint,
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("custom_model_clear_button")
                        ) {
                            Text("Disconnect Custom Endpoint", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Replaceable Model Router Status
            item {
                SectionCard(title = "Modular Model Router") {
                    Text(
                        text = "Switch seamlessly between Firebase AI Logic models and your own self-hosted AGNI Fine-Tuned / FastAPI endpoint.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    modelCatalog.forEach { model ->
                        val active = settings.defaultModelId == model.modelKey
                        Surface(
                            onClick = { onSelectModel(model.modelKey) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Memory,
                                    contentDescription = null,
                                    tint = if (model.isLiveReady) MaterialTheme.colorScheme.primary else AgniAmber
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(model.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "${model.provider} · ${if (model.isLiveReady) "Live" else "Configure above"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (model.isLiveReady) EmeraldSuccess else AgniAmber
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // User Feedback Form
            item {
                SectionCard(title = "Share User Feedback") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        (1..5).forEach { star ->
                            IconButton(onClick = { feedbackRating = star }) {
                                Icon(
                                    imageVector = if (star <= feedbackRating) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                    contentDescription = "Rate $star stars",
                                    tint = AgniAmber
                                )
                            }
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FeedbackCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = feedbackCategory == cat,
                                onClick = { feedbackCategory = cat },
                                label = { Text(cat.label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = feedbackComment,
                        onValueChange = { if (it.length <= 2000) feedbackComment = it },
                        label = { Text("Tell us how Agni AI can improve") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("feedback_comment_input")
                    )
                    Button(
                        onClick = {
                            if (feedbackComment.isNotBlank()) {
                                onSubmitFeedback(feedbackRating, feedbackCategory, feedbackComment)
                                feedbackComment = ""
                            }
                        },
                        enabled = feedbackComment.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag("submit_feedback_button")
                    ) {
                        Text("Submit Feedback")
                    }

                    if (feedbackState is UiState.Success && feedbackState.data.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = "YOUR RECENT FEEDBACK (${feedbackState.data.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        feedbackState.data.take(3).forEach { fb ->
                            Text(
                                text = "★${fb.rating} [${FeedbackCategory.fromKey(fb.category).label}] ${fb.comment}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Data Privacy & Control
            item {
                SectionCard(title = "Privacy & Stored Data Control") {
                    Text(
                        text = "Your chats, messages, files, settings, and memories are strictly isolated to your Google account in Firestore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { confirmClearChats = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear All Chats")
                        }
                        OutlinedButton(
                            onClick = { confirmClearMemory = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear Memory")
                        }
                    }
                }
            }
        }
    }

    if (confirmClearChats) {
        AlertDialog(
            onDismissRequest = { confirmClearChats = false },
            title = { Text("Delete all chat history?") },
            text = { Text("This permanently deletes all saved conversations and messages from your private Firestore workspace.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearChats = false
                    onClearAllChats()
                }) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearChats = false }) { Text("Cancel") }
            }
        )
    }

    if (confirmClearMemory) {
        AlertDialog(
            onDismissRequest = { confirmClearMemory = false },
            title = { Text("Clear all saved memories?") },
            text = { Text("This removes all persistent facts stored in your Agni Memory collection.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearMemory = false
                    onClearAllMemories()
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearMemory = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SubScreenTopBar(title: String, onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("subscreen_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to chat"
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = title, style = MaterialTheme.typography.titleLarge)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun UsageStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
