package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Face3
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AgniLanguage
import com.example.model.AssistantMode
import com.example.model.ModelOption
import com.example.ui.theme.AgniAmber
import com.example.ui.theme.EmeraldSuccess
import com.example.viewmodel.AgniDestination
import com.example.viewmodel.ChatComposerState
import com.example.voice.AssistantVoiceGender

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AgniMoreOptionsSheet(
    composer: ChatComposerState,
    activeModel: ModelOption,
    onDismiss: () -> Unit,
    onSelectVoiceGender: (AssistantVoiceGender) -> Unit,
    onToggleAutoSpeak: (Boolean) -> Unit,
    onTestVoice: (AssistantVoiceGender) -> Unit,
    onNewChat: () -> Unit,
    onDeleteCurrentChat: () -> Unit,
    onAttachImage: () -> Unit,
    onAttachPdf: () -> Unit,
    onOpenMediaStudio: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onOpenLanguagePicker: () -> Unit,
    onOpenTranslationTargetPicker: () -> Unit,
    onSelectMode: (AssistantMode) -> Unit,
    onToggleTransliteration: (Boolean) -> Unit,
    onNavigateDestination: (AgniDestination) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Agni AI Tools & Voice",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // 1. Male / Female Assistant Voice Control Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Assistant Voice (ছেলে / মেয়ে ভয়েস)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Auto-speaks replies in ${composer.selectedLanguage.nativeName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = composer.autoSpeakReplies,
                            onCheckedChange = onToggleAutoSpeak,
                            modifier = Modifier.testTag("auto_speak_switch")
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        VoiceGenderOptionCard(
                            icon = Icons.Rounded.Face3,
                            title = "মেয়ে ভয়েস",
                            subtitle = "Sweet Female",
                            selected = composer.voiceGender == AssistantVoiceGender.FEMALE,
                            testTag = "select_female_voice_button",
                            onClick = {
                                onSelectVoiceGender(AssistantVoiceGender.FEMALE)
                                onTestVoice(AssistantVoiceGender.FEMALE)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        VoiceGenderOptionCard(
                            icon = Icons.Rounded.Face,
                            title = "ছেলে ভয়েস",
                            subtitle = "Male Voice",
                            selected = composer.voiceGender == AssistantVoiceGender.MALE,
                            testTag = "select_male_voice_button",
                            onClick = {
                                onSelectVoiceGender(AssistantVoiceGender.MALE)
                                onTestVoice(AssistantVoiceGender.MALE)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedButton(
                        onClick = { onTestVoice(composer.voiceGender) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_assistant_voice_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ভয়েস টেস্ট করুন · Test ${composer.voiceGender.englishLabel} Voice")
                    }
                }
            }

            // 2. Quick Actions Grid (New Chat, Studio, Image, PDF)
            Text(
                text = "QUICK ACTIONS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    QuickActionTile(
                        icon = Icons.Rounded.Add,
                        label = "New Chat",
                        testTag = "more_new_chat_button",
                        onClick = {
                            onDismiss()
                            onNewChat()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionTile(
                        icon = Icons.Rounded.PhotoLibrary,
                        label = "Photo & Video",
                        testTag = "more_media_studio_button",
                        onClick = {
                            onDismiss()
                            onOpenMediaStudio()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    QuickActionTile(
                        icon = Icons.Rounded.Image,
                        label = "Attach Image",
                        testTag = "attach_image_button",
                        onClick = {
                            onDismiss()
                            onAttachImage()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionTile(
                        icon = Icons.Rounded.PictureAsPdf,
                        label = "Attach PDF",
                        testTag = "attach_pdf_button",
                        onClick = {
                            onDismiss()
                            onAttachPdf()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    QuickActionTile(
                        icon = Icons.Rounded.History,
                        label = "চ্যাট হিস্টোরি",
                        testTag = "more_chat_history_button",
                        onClick = {
                            onDismiss()
                            onNavigateDestination(AgniDestination.HISTORY)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (composer.activeChatId != null) {
                        Surface(
                            onClick = {
                                onDismiss()
                                onDeleteCurrentChat()
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("more_delete_current_chat_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "এই চ্যাট ডিলিট করুন",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(19.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "চ্যাট ডিলিট",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 3. AI Model & Language Rows
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    MenuSettingRow(
                        icon = Icons.Rounded.Memory,
                        title = "AI Model",
                        value = activeModel.displayName,
                        testTag = "model_selector_pill",
                        onClick = {
                            onDismiss()
                            onOpenModelPicker()
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MenuSettingRow(
                        icon = Icons.Rounded.Language,
                        title = "Language",
                        value = "${composer.selectedLanguage.displayName} (${composer.selectedLanguage.nativeName})",
                        testTag = "language_selector_button",
                        onClick = {
                            onDismiss()
                            onOpenLanguagePicker()
                        }
                    )
                }
            }

            // 4. Specialist Modes
            Text(
                text = "ASSISTANT MODE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistantMode.entries.forEach { mode ->
                    val selected = mode == composer.selectedMode
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectMode(mode) },
                        label = { Text(mode.shortLabel) },
                        leadingIcon = {
                            if (mode == AssistantMode.CHAT) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_agni_flame),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = modeIcon(mode),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
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

            if (composer.selectedMode == AssistantMode.TRANSLATION) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Surface(
                            onClick = {
                                onDismiss()
                                onOpenTranslationTargetPicker()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SwapHoriz,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Target: ${composer.translationTarget.nativeName}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Phonetics", style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = composer.includeTransliteration,
                                onCheckedChange = onToggleTransliteration
                            )
                        }
                    }
                }
            }

            // 5. Workspace Shortcuts
            Text(
                text = "WORKSPACE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    MenuSettingRow(
                        icon = Icons.Rounded.History,
                        title = "চ্যাট হিস্টোরি (Chat History)",
                        value = "",
                        testTag = "more_nav_history",
                        onClick = {
                            onDismiss()
                            onNavigateDestination(AgniDestination.HISTORY)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MenuSettingRow(
                        icon = Icons.Rounded.Dashboard,
                        title = "Hub, Usage & Files",
                        value = "",
                        testTag = "more_nav_hub",
                        onClick = {
                            onDismiss()
                            onNavigateDestination(AgniDestination.HUB)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MenuSettingRow(
                        icon = Icons.Rounded.AccountTree,
                        title = "System Architecture",
                        value = "",
                        testTag = "more_nav_architecture",
                        onClick = {
                            onDismiss()
                            onNavigateDestination(AgniDestination.ARCHITECTURE)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MenuSettingRow(
                        icon = Icons.Rounded.Psychology,
                        title = "Memory & Context",
                        value = "",
                        testTag = "more_nav_memory",
                        onClick = {
                            onDismiss()
                            onNavigateDestination(AgniDestination.MEMORY)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MenuSettingRow(
                        icon = Icons.Rounded.Settings,
                        title = "Settings, Custom AI & Feedback",
                        value = "",
                        testTag = "more_nav_settings",
                        onClick = {
                            onDismiss()
                            onNavigateDestination(AgniDestination.SETTINGS)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun VoiceGenderOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier.testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MenuSettingRow(
    icon: ImageVector,
    title: String,
    value: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (value.isNotBlank()) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgniModelPickerSheet(
    modelCatalog: List<ModelOption>,
    selectedModelKey: String,
    onSelectModel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
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
                text = "Switch models anytime without changing your workflow.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            modelCatalog.forEach { option ->
                val isSelected = option.modelKey == selectedModelKey
                Surface(
                    onClick = {
                        onSelectModel(option.modelKey)
                        onDismiss()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgniLanguagePickerSheet(
    pickingTranslationTarget: Boolean,
    selectedLanguage: AgniLanguage,
    translationTarget: AgniLanguage,
    onSelect: (AgniLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
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
                        lang == translationTarget
                    } else {
                        lang == selectedLanguage
                    }
                    Surface(
                        onClick = {
                            onSelect(lang)
                            onDismiss()
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
