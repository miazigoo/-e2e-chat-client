package com.example.securechatapp.ui.screens.conversation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.securechatapp.ui.components.BrandedSkeletonBlock
import com.example.securechatapp.ui.components.BrandedSkeletonLines
import com.example.securechatapp.ui.viewmodel.ConversationViewModel

@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    var message by remember { mutableStateOf("") }
    var pendingAttachmentUri by remember { mutableStateOf<Uri?>(null) }
    var pendingAttachmentName by remember { mutableStateOf<String?>(null) }
    var showSharedSecretSettings by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    BackHandler(onBack = onBack)

    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        pendingAttachmentUri = uri
        pendingAttachmentName = uri?.lastPathSegment ?: "attachment"
    }

    val conversationRows = remember(state.messages) {
        buildConversationRows(state.messages)
    }
    val conversationBlockedReason = remember(
        state.isConversationPurged,
        state.isConversationActive,
        state.sharedSecretEnabled,
        state.localSharedSecretEnabled,
    ) {
        when {
            state.isConversationPurged -> "Чат удалён на сервере. Отправка и загрузка файлов недоступны."
            !state.isConversationActive -> "Чат деактивирован на сервере. Доступен только просмотр истории."
            state.sharedSecretEnabled && !state.localSharedSecretEnabled -> "Для отправки сообщений в этом чате включите дополнительное шифрование на этом устройстве."
            else -> null
        }
    }
    val composerPlaceholder = remember(conversationBlockedReason) {
        conversationBlockedReason ?: "Сообщение"
    }

    val subtitle = remember(
        state.messages,
        state.sharedSecretEnabled,
        state.localSharedSecretEnabled,
        state.peerSharedSecretEnabled,
    ) {
        val base = buildConversationSubtitle(state.messages)
        when {
            state.sharedSecretEnabled && state.localSharedSecretEnabled -> "🔐 $base"
            state.sharedSecretEnabled -> "🔒 нужен токен · $base"
            state.peerSharedSecretEnabled -> "🔓 собеседник включил доп. шифрование · $base"
            else -> base
        }
    }

    LaunchedEffect(conversationRows.size) {
        if (conversationRows.isNotEmpty()) {
            listState.animateScrollToItem(conversationRows.lastIndex)
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
                title = when {
                    state.title.isNotBlank() -> state.title
                    state.isSavedMessages -> "Избранное"
                    else -> "Диалог"
                },
                subtitle = subtitle,
                onBack = onBack,
                onLogout = { viewModel.logout(onLoggedOut) },
                onSharedSecretClick = { showSharedSecretSettings = true },
                isLoggingOut = state.isLoggingOut,
                sharedSecretEnabled = state.sharedSecretEnabled,
                localSharedSecretEnabled = state.localSharedSecretEnabled,
            )

            state.error?.let {
                Banner(text = it, isError = true)
            }

            state.info?.let {
                Banner(text = it)
            }

            conversationBlockedReason?.let {
                Banner(text = it, isError = state.isConversationPurged)
            }

            state.pinnedMessage?.let { pinned ->
                Banner(
                    text = buildString {
                        append("📌 ")
                        append(
                            pinned.text.ifBlank {
                                if (pinned.hasAttachments) "Закреплено вложение" else "Закреплённое сообщение"
                            }
                        )
                    }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                state = listState,
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                if (state.isLoading && state.messages.isEmpty()) {
                    item {
                        ConversationSkeletonState()
                    }
                }

                if (!state.isLoading && state.messages.isEmpty()) {
                    item {
                        EmptyConversationHint("Начни диалог первым сообщением")
                    }
                }

                items(
                    items = conversationRows,
                    key = { it.key },
                ) { row ->
                    when (row) {
                        is ConversationRow.DateSeparator -> {
                            DateSeparatorChip(text = row.label)
                        }

                        is ConversationRow.MessageItem -> {
                            MessageBubble(
                                msg = row.message,
                                groupPosition = row.groupPosition,
                                isDeleting = state.deletingMessageIds.contains(row.message.messageId),
                                onDeleteLocal = { viewModel.deleteMessageLocal(row.message.messageId) },
                                onDeleteGlobal = { viewModel.deleteMessageGlobal(row.message.messageId) },
                                onAttachmentsClick = {
                                    viewModel.showMessageAttachments(row.message)
                                },
                                onRetrySend = {
                                    viewModel.retryFailedMessage(row.message.messageId)
                                },
                                onRemovePending = {
                                    viewModel.removePendingMessage(row.message.messageId)
                                },
                                onSetReaction = { reaction ->
                                    viewModel.setMessageReaction(row.message.messageId, reaction)
                                },
                                onRemoveReaction = {
                                    viewModel.removeMessageReaction(row.message.messageId)
                                },
                                onPinMessage = {
                                    viewModel.pinMessage(row.message.messageId)
                                },
                            )
                        }
                    }
                }
            }

            pendingAttachmentName?.let { name ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
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
                onAttachClick = {
                    if (conversationBlockedReason == null) {
                        attachmentPicker.launch("*/*")
                    }
                },
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
                inputEnabled = conversationBlockedReason == null,
                placeholder = composerPlaceholder,
                sendEnabled = message.isNotBlank() || pendingAttachmentUri != null,
            )

            if (state.pinnedMessage != null) {
                TextButton(
                    onClick = viewModel::unpinMessage,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 10.dp, bottom = 4.dp),
                ) {
                    Text("Снять закреп")
                }
            }
        }

        if (state.attachmentSheetMessageId != null) {
            MessageAttachmentsDialog(
                attachments = state.selectedMessageAttachments,
                attachmentLocalStates = state.attachmentLocalStates,
                isLoading = state.isLoadingAttachments,
                downloadingAttachmentId = state.downloadingAttachmentId,
                onDismiss = viewModel::dismissMessageAttachments,
                onAttachmentClick = { attachment ->
                    viewModel.onAttachmentSelected(attachment)
                }
            )
        }

        if (state.isLoadingImagePreview || state.imagePreviewUrl != null || state.imagePreviewBytes != null) {
            ImagePreviewDialog(
                fileName = state.imagePreviewFileName ?: "Изображение",
                imageUrl = state.imagePreviewUrl,
                imageBytes = state.imagePreviewBytes,
                isLoading = state.isLoadingImagePreview,
                isDownloading = state.downloadingAttachmentId == state.imagePreviewAttachmentId,
                onDismiss = viewModel::dismissImagePreview,
                onDownload = viewModel::downloadCurrentPreview,
            )
        }

        if (showSharedSecretSettings) {
            SharedSecretSettingsDialog(
                enabledOnServer = state.sharedSecretEnabled,
                enabledLocally = state.localSharedSecretEnabled,
                fingerprint = state.sharedSecretFingerprint,
                localFingerprint = state.localSharedSecretFingerprint,
                peerEnabled = state.peerSharedSecretEnabled,
                onDismiss = { showSharedSecretSettings = false },
                onEnable = { token ->
                    viewModel.enableSharedSecret(token)
                    showSharedSecretSettings = false
                },
                onDisable = {
                    viewModel.disableSharedSecret()
                    showSharedSecretSettings = false
                },
            )
        }

    }
}

@Composable
private fun ConversationSkeletonState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        repeat(4) { index ->
            val isMine = index % 2 == 0
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isMine) {
                    androidx.compose.foundation.layout.Arrangement.End
                } else {
                    androidx.compose.foundation.layout.Arrangement.Start
                },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(if (isMine) 0.72f else 0.8f),
                ) {
                    BrandedSkeletonBlock(
                        modifier = Modifier.fillMaxWidth(),
                        height = 64.dp,
                        cornerRadius = 22.dp,
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(6.dp))
                    BrandedSkeletonLines(
                        primaryWidthFraction = 0.42f,
                        secondaryWidthFraction = 0.28f,
                    )
                }
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(10.dp))
        }
    }
}


@Composable
private fun SharedSecretSettingsDialog(
    enabledOnServer: Boolean,
    enabledLocally: Boolean,
    fingerprint: String?,
    localFingerprint: String?,
    peerEnabled: Boolean,
    onDismiss: () -> Unit,
    onEnable: (String) -> Unit,
    onDisable: () -> Unit,
) {
    var token by remember { mutableStateOf("") }
    val canEnable = token.trim().length >= 8

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Доп. шифрование чата")
        },
        text = {
            Column {
                Text(
                    text = when {
                        enabledOnServer && enabledLocally -> "Включено для этого чата. Сервер хранит только fingerprint."
                        enabledOnServer -> "Включено на сервере, но на этом устройстве нужен токен чата."
                        else -> "Включите доп. слой поверх основного шифрования для этого чата."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )

                Text(
                    text = "Собеседник: ${if (peerEnabled) "включил" else "не включил"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )

                val shownFingerprint = localFingerprint ?: fingerprint
                if (!shownFingerprint.isNullOrBlank()) {
                    Text(
                        text = "Fingerprint: ${shownFingerprint.take(12)}…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Токен этого чата") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )

                Text(
                    text = "Токен не отправляется на сервер. Ключ выводится локально из token + conversation_uuid.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onEnable(token) },
                enabled = canEnable,
            ) {
                Text(if (enabledOnServer || enabledLocally) "Обновить токен" else "Включить")
            }
        },
        dismissButton = {
            Row {
                if (enabledOnServer || enabledLocally) {
                    TextButton(onClick = onDisable) {
                        Text("Выключить")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Закрыть")
                }
            }
        },
    )
}
