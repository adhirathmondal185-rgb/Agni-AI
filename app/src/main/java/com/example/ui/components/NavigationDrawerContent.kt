package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AssistantMode
import com.example.model.ChatSession
import com.example.model.UiState
import com.example.ui.screens.formatChatTimestamp
import com.example.viewmodel.AgniDestination

@Composable
fun AgniSidebarContent(
    userDisplayName: String,
    userEmail: String?,
    currentDestination: AgniDestination,
    activeChatId: String?,
    searchQuery: String,
    chatsState: UiState<List<ChatSession>>,
    onSearchQueryChange: (String) -> Unit,
    onNewChat: () -> Unit,
    onSelectChat: (ChatSession) -> Unit,
    onTogglePin: (ChatSession) -> Unit,
    onDeleteChat: (String) -> Unit,
    onClearAllChats: () -> Unit,
    onNavigate: (AgniDestination) -> Unit,
    onSwitchAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    var chatPendingDelete by remember { mutableStateOf<ChatSession?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }

    val totalChats = (chatsState as? UiState.Success)?.data?.size ?: 0

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Fixed Top Brand Header + New Chat CTA
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_agni_logo_fg_1795283955599),
                    contentDescription = stringResource(R.string.cd_agni_emblem),
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("AGNI AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onNewChat,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(vertical = 11.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sidebar_new_chat_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("নতুন চ্যাট · New Chat", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Unified Scrollable Area: Chat History FIRST (so it's always spacious and readable on every phone),
            // followed by Workspace navigation links.
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Search Bar
                item(key = "history_search") {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("চ্যাট হিস্টোরি খুঁজুন…", style = MaterialTheme.typography.bodySmall) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Clear search",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_search_input")
                    )
                }

                // Chat History Header + Full View / Clear All actions
                item(key = "history_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, start = 2.dp)
                    ) {
                        Text(
                            text = "চ্যাট হিস্টোরি ($totalChats)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { onNavigate(AgniDestination.HISTORY) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("sidebar_view_all_history_button")
                            ) {
                                Text("সব দেখুন", style = MaterialTheme.typography.labelSmall)
                            }
                            if (totalChats > 0) {
                                IconButton(
                                    onClick = { confirmClearAll = true },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("sidebar_clear_all_chats_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteSweep,
                                        contentDescription = "সব চ্যাট মুছুন",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Synced Chat History Items
                when (chatsState) {
                    is UiState.Success -> {
                        val filtered = if (searchQuery.isBlank()) {
                            chatsState.data
                        } else {
                            chatsState.data.filter {
                                it.title.contains(searchQuery, ignoreCase = true) ||
                                    it.lastMessagePreview.contains(searchQuery, ignoreCase = true) ||
                                    it.language.contains(searchQuery, ignoreCase = true)
                            }
                        }
                        if (filtered.isEmpty()) {
                            item(key = "history_empty") {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (searchQuery.isBlank()) {
                                            "এখনো কোনো চ্যাট নেই। প্রশ্ন করলেই এখানে হিস্টোরি সেভ হবে।"
                                        } else {
                                            "কোনো চ্যাট পাওয়া যায়নি।"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        } else {
                            items(filtered, key = { "chat_${it.id}" }) { chat ->
                                val isActive = chat.id == activeChatId && currentDestination == AgniDestination.CHAT
                                Surface(
                                    onClick = { onSelectChat(chat) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("sidebar_chat_item_${chat.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = chat.title.ifBlank { "New Conversation" },
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (chat.lastMessagePreview.isNotBlank()) {
                                                Text(
                                                    text = chat.lastMessagePreview,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            val timeText = formatChatTimestamp(chat.updatedAt ?: chat.createdAt)
                                            Text(
                                                text = buildString {
                                                    append(AssistantMode.fromKey(chat.mode).shortLabel)
                                                    append(" · ")
                                                    append(chat.language)
                                                    if (timeText.isNotBlank()) {
                                                        append(" · ")
                                                        append(timeText)
                                                    }
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = { onTogglePin(chat) },
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PushPin,
                                                contentDescription = if (chat.pinned) "Unpin chat" else "Pin chat",
                                                tint = if (chat.pinned) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { chatPendingDelete = chat },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .testTag("sidebar_delete_chat_${chat.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "চ্যাট ডিলিট করুন",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is UiState.Error -> {
                        item(key = "history_error") {
                            Text(
                                text = chatsState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    is UiState.Loading -> {
                        item(key = "history_loading") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "হিস্টোরি লোড হচ্ছে…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Workspace Navigation Section
                item(key = "workspace_divider") {
                    Column {
                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "WORKSPACE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                item(key = "nav_items") {
                    Column {
                        SidebarNavItem(
                            icon = Icons.Rounded.ChatBubbleOutline,
                            label = "AI Chat",
                            selected = currentDestination == AgniDestination.CHAT,
                            testTag = "nav_chat",
                            onClick = { onNavigate(AgniDestination.CHAT) }
                        )
                        SidebarNavItem(
                            icon = Icons.Rounded.History,
                            label = "চ্যাট হিস্টোরি (History)",
                            selected = currentDestination == AgniDestination.HISTORY,
                            testTag = "nav_history",
                            onClick = { onNavigate(AgniDestination.HISTORY) }
                        )
                        SidebarNavItem(
                            icon = Icons.Rounded.PhotoLibrary,
                            label = stringResource(R.string.nav_media_studio),
                            selected = currentDestination == AgniDestination.MEDIA_STUDIO,
                            testTag = "nav_media_studio",
                            onClick = { onNavigate(AgniDestination.MEDIA_STUDIO) }
                        )
                        SidebarNavItem(
                            icon = Icons.Rounded.Dashboard,
                            label = "Hub, Usage & Files",
                            selected = currentDestination == AgniDestination.HUB,
                            testTag = "nav_hub",
                            onClick = { onNavigate(AgniDestination.HUB) }
                        )
                        SidebarNavItem(
                            icon = Icons.Rounded.AccountTree,
                            label = stringResource(R.string.nav_architecture),
                            selected = currentDestination == AgniDestination.ARCHITECTURE,
                            testTag = "nav_architecture",
                            onClick = { onNavigate(AgniDestination.ARCHITECTURE) }
                        )
                        SidebarNavItem(
                            icon = Icons.Rounded.Psychology,
                            label = "Memory & Context",
                            selected = currentDestination == AgniDestination.MEMORY,
                            testTag = "nav_memory",
                            onClick = { onNavigate(AgniDestination.MEMORY) }
                        )
                        SidebarNavItem(
                            icon = Icons.Rounded.Settings,
                            label = "Settings & Feedback",
                            selected = currentDestination == AgniDestination.SETTINGS,
                            testTag = "nav_settings",
                            onClick = { onNavigate(AgniDestination.SETTINGS) }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))

            // Footer User Identity + Quick Switch Account
            Surface(
                onClick = { onNavigate(AgniDestination.SETTINGS) },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userDisplayName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!userEmail.isNullOrBlank()) {
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Surface(
                        onClick = onSwitchAccount,
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.testTag("sidebar_switch_account_button")
                    ) {
                        Text(
                            text = stringResource(R.string.switch_account),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }

    val pending = chatPendingDelete
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { chatPendingDelete = null },
            title = { Text("চ্যাট ডিলিট করবেন?") },
            text = { Text("\"${pending.title.take(60)}\" চ্যাটটি স্থায়ীভাবে মুছে যাবে।") },
            confirmButton = {
                TextButton(
                    onClick = {
                        chatPendingDelete = null
                        onDeleteChat(pending.id)
                    }
                ) {
                    Text("ডিলিট করুন", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { chatPendingDelete = null }) {
                    Text("বাতিল")
                }
            }
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("সব চ্যাট হিস্টোরি মুছবেন?") },
            text = { Text("আপনার সব সেভ করা কথোপকথন ও মেসেজ স্থায়ীভাবে মুছে যাবে।") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearAll = false
                        onClearAllChats()
                    }
                ) {
                    Text("সব ডিলিট করুন", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text("বাতিল")
                }
            }
        )
    }
}

@Composable
private fun SidebarNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
