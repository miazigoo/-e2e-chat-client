package com.example.securechatapp.ui.screens.conversation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.securechatapp.data.repository.BackendRepository
import com.example.securechatapp.ui.theme.TgChatWallpaper
import com.example.securechatapp.ui.theme.TgDarkChatWallpaper
import com.example.securechatapp.ui.theme.TgDarkIncomingBubble
import com.example.securechatapp.ui.theme.TgDarkOutgoingBubble
import com.example.securechatapp.ui.theme.TgIncomingBubble
import com.example.securechatapp.ui.theme.TgOutgoingBubble
import com.example.securechatapp.ui.theme.TgTopBarDark
import com.example.securechatapp.ui.theme.TgTopBarLight
import com.example.securechatapp.ui.viewmodel.ChatsViewModel
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Composable
fun ConversationScreen(
    conversationId: Int,
    viewModel: ChatsViewModel,
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    var message by remember { mutableStateOf("") }
    var pendingAttachmentUri by remember { mutableStateOf<Uri?>(null) }
    var pendingAttachmentName by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    BackHandler(onBack = onBack)

    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        pendingAttachmentUri = uri
        pendingAttachmentName = uri?.lastPathSegment ?: "attachment"
    }

    LaunchedEffect(conversationId) {
        if (state.activeConversationId != conversationId) {
            viewModel.openConversation(conversationId)
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        TelegramChatWallpaper()

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            ConversationTopBar(
                title = if (state.activeConversationTitle.isBlank()) "Диалог" else state.activeConversationTitle,
                onBack = onBack,
                onLogout = { viewModel.logout(onLoggedOut) },
                isLoggingOut = state.isLoggingOut,
            )

            state.error?.let {
                Banner(text = it, isError = true)
            }

            state.info?.let {
                Banner(text = it)
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                if (state.isLoading && state.messages.isEmpty()) {
                    item {
                        EmptyConversationHint("Загрузка сообщений...")
                    }
                }

                if (!state.isLoading && state.messages.isEmpty()) {
                    item {
                        EmptyConversationHint("Начни диалог первым сообщением")
                    }
                }

                items(
                    items = state.messages,
                    key = { it.messageId },
                ) { msg ->
                    MessageBubble(
                        msg = msg,
                        isDeleting = state.deletingMessageIds.contains(msg.messageId),
                        onDeleteLocal = { viewModel.deleteMessageLocal(msg.messageId) },
                        onDeleteGlobal = { viewModel.deleteMessageGlobal(msg.messageId) },
                    )
                }
            }

            pendingAttachmentName?.let { name ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 1.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "📎 $name",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        TextButton(
                            onClick = {
                                pendingAttachmentUri = null
                                pendingAttachmentName = null
                            }
                        ) {
                            Text("Убрать")
                        }
                    }
                }
            }

            ConversationComposer(
                message = message,
                onMessageChange = { message = it },
                onAttachClick = { attachmentPicker.launch("*/*") },
                onSendClick = {
                    val textToSend = message
                    val attachmentToSend = pendingAttachmentUri

                    viewModel.sendMessage(
                        text = textToSend,
                        attachmentUri = attachmentToSend,
                    ) {
                        message = ""
                        pendingAttachmentUri = null
                        pendingAttachmentName = null
                    }
                },
                isUploading = state.isUploadingAttachment,
                sendEnabled = message.isNotBlank() || pendingAttachmentUri != null,
            )
        }
    }
}

@Composable
private fun TelegramChatWallpaper() {
    val dark = isSystemInDarkTheme()
    val base = if (dark) TgDarkChatWallpaper else TgChatWallpaper
    val accent1 = MaterialTheme.colorScheme.primary.copy(alpha = if (dark) 0.10f else 0.07f)
    val accent2 = MaterialTheme.colorScheme.secondary.copy(alpha = if (dark) 0.07f else 0.05f)
    val accent3 = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (dark) 0.10f else 0.06f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(base),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-70).dp, y = (-40).dp)
                .size(220.dp)
                .clip(CircleShape)
                .background(accent1)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 80.dp, y = (-40).dp)
                .size(250.dp)
                .clip(CircleShape)
                .background(accent2)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-30).dp, y = 60.dp)
                .size(180.dp)
                .clip(CircleShape)
                .background(accent3)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 50.dp, y = 40.dp)
                .size(160.dp)
                .clip(CircleShape)
                .background(accent1)
        )
    }
}

@Composable
private fun ConversationTopBar(
    title: String,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    isLoggingOut: Boolean,
) {
    val dark = isSystemInDarkTheme()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = if (dark) TgTopBarDark else TgTopBarLight,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("←")
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title.trim().removePrefix("@").firstOrNull()?.uppercase() ?: "?",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "secure chat • online",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TextButton(onClick = onLogout) {
                Text(if (isLoggingOut) "..." else "Выйти")
            }
        }
    }
}

@Composable
private fun ConversationComposer(
    message: String,
    onMessageChange: (String) -> Unit,
    onAttachClick: () -> Unit,
    onSendClick: () -> Unit,
    isUploading: Boolean,
    sendEnabled: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Button(
                    onClick = onAttachClick,
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.fillMaxSize(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text("📎")
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
            ) {
                OutlinedTextField(
                    value = message,
                    onValueChange = onMessageChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Сообщение") },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    ),
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onSendClick,
                enabled = sendEnabled && !isUploading,
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(46.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(if (isUploading) "…" else "➤")
            }
        }
    }
}

@Composable
private fun MessageBubble(
    msg: BackendRepository.MessageUi,
    isDeleting: Boolean,
    onDeleteLocal: () -> Unit,
    onDeleteGlobal: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val bubbleColor = when {
        msg.isMine && dark -> TgDarkOutgoingBubble
        msg.isMine && !dark -> TgOutgoingBubble
        !msg.isMine && dark -> TgDarkIncomingBubble
        else -> TgIncomingBubble
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isMine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (msg.isMine) 18.dp else 6.dp,
                bottomEnd = if (msg.isMine) 6.dp else 18.dp,
            ),
            color = bubbleColor,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(0.82f),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (msg.hasAttachments) {
                    Text(
                        text = "📎 attachment",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val statusColor = when {
                        msg.readAt != null -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Text(
                        text = formatMessageTime(msg.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (msg.isMine) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                msg.readAt != null -> "✓✓"
                                msg.deliveredAt != null -> "✓✓"
                                else -> "✓"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextButton(
                        onClick = onDeleteLocal,
                        contentPadding = PaddingValues(0.dp),
                        enabled = !isDeleting,
                    ) {
                        Text(
                            text = if (isDeleting) "..." else "Скрыть",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    if (msg.isMine) {
                        TextButton(
                            onClick = onDeleteGlobal,
                            contentPadding = PaddingValues(0.dp),
                            enabled = !isDeleting,
                        ) {
                            Text(
                                text = if (isDeleting) "..." else "Удалить",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Banner(
    text: String,
    isError: Boolean = false,
) {
    val bg = if (isError) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    }
    val content = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = bg,
    ) {
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun EmptyConversationHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

private fun formatMessageTime(raw: String): String {
    return runCatching {
        OffsetDateTime.parse(raw)
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault(raw)
}
