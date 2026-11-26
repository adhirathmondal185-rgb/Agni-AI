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
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AssistantMode
import com.example.model.ChatSession
import com.example.model.UiState
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
    onNavigate: (AgniDestination) -> Unit,
    onSwitchAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Brand Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_agni_logo_fg_1795283955599),
                    contentDescription = stringResource(R.string.cd_agni_emblem),
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("AGNI AI", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary New Chat CTA
            Button(
                onClick = onNewChat,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sidebar_new_chat_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.new_chat), style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Workspace Navigation Items
            SidebarNavItem(
                icon = Icons.Rounded.ChatBubbleOutline,
                label = "AI Chat",
                selected = currentDestination == AgniDestination.CHAT,
                testTag = "nav_chat",
                onClick = { onNavigate(AgniDestination.CHAT) }
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

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(10.dp))

            // Chat History Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search history…", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "CHAT HISTORY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            // Synced Chat History List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                when (chatsState) {
                    is UiState.Success -> {
                        val filtered = if (searchQuery.isBlank()) {
                            chatsState.data
                        } else {
                            chatsState.data.filter {
                                it.title.contains(searchQuery, ignoreCase = true) ||
                                    it.lastMessagePreview.contains(searchQuery, ignoreCase = true)
                            }
                        }
                        if (filtered.isEmpty()) {
                            item {
                                Text(
                                    text = if (searchQuery.isBlank()) "No conversations yet." else "No matching chats.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        } else {
                            items(filtered, key = { it.id }) { chat ->
                                val isActive = chat.id == activeChatId && currentDestination == AgniDestination.CHAT
                                Surface(
                                    onClick = { onSelectChat(chat) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = if (isActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                                    else null,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = chat.title,
                                                style = MaterialTheme.typography.labelLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${AssistantMode.fromKey(chat.mode).shortLabel} · ${chat.language}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                        IconButton(
                                            onClick = { onTogglePin(chat) },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PushPin,
                                                contentDescription = if (chat.pinned) "Unpin chat" else "Pin chat",
                                                tint = if (chat.pinned) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteChat(chat.id) },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "Delete chat",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is UiState.Error -> {
                        item {
                            Text(
                                text = chatsState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    is UiState.Loading -> {
                        item {
                            Text(
                                text = "Syncing history…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
