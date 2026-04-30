package com.example.securechatapp.ui.screens.conversation

import android.content.Context
import android.app.Activity
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import com.example.securechatapp.ui.components.BrandedSkeletonBlock
import com.example.securechatapp.ui.components.BrandedSkeletonLines
import com.example.securechatapp.domain.model.MessagePreview
import com.example.securechatapp.ui.picker.SystemDocumentPickerActivity
import com.example.securechatapp.ui.picker.SystemDocumentPickerBus
import com.example.securechatapp.ui.viewmodel.ConversationViewModel

private data class PendingAttachmentUi(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
) {
    val isImage: Boolean
        get() = mimeType?.startsWith("image/") == true
}

@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var message by remember { mutableStateOf("") }
    var pendingAttachments by remember { mutableStateOf<List<PendingAttachmentUi>>(emptyList()) }
    var previewingPendingAttachment by remember { mutableStateOf<PendingAttachmentUi?>(null) }
    var showSharedSecretSettings by remember { mutableStateOf(false) }
    var highlightedMessageId by remember { mutableStateOf<Int?>(null) }

    val listState = rememberLazyListState()

    BackHandler(onBack = onBack)

    val activity = context as? Activity

    LaunchedEffect(Unit) {
        SystemDocumentPickerBus.results.collectLatest { result ->
            if (result.requestKey != SystemDocumentPickerActivity.REQUEST_ATTACHMENTS) {
                return@collectLatest
            }

            val newItems = result.uris.map(Uri::parse).map { selectedUri ->
                PendingAttachmentUi(
                    uri = selectedUri,
                    displayName = resolveAttachmentDisplayName(context, selectedUri)
                        ?: selectedUri.lastPathSegment
                        ?: "Файл",
                    mimeType = resolveAttachmentMimeType(context, selectedUri),
                )
            }
            pendingAttachments = (pendingAttachments + newItems).distinctBy { it.uri.toString() }
        }
    }

    val conversationRows = remember(state.messages) {
        buildConversationRows(state.messages)
    }
    val messageRowIndexById = remember(conversationRows) {
        conversationRows.mapIndexedNotNull { index, row ->
            (row as? ConversationRow.MessageItem)?.message?.messageId?.let { it to index }
        }.toMap()
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

    val lastMessageAnchor = remember(state.messages) {
        state.messages.lastOrNull()?.let { "${it.messageId}_${it.createdAt}" }
    }
    var initialScrollDone by rememberSaveable { mutableStateOf(false) }
    val isNearBottom by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val totalItems = layout.totalItemsCount
            if (totalItems == 0) return@derivedStateOf true
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf true
            lastVisible >= totalItems - 3
        }
    }

    LaunchedEffect(lastMessageAnchor, conversationRows.size) {
        if (conversationRows.isEmpty()) return@LaunchedEffect

        if (!initialScrollDone) {
            listState.scrollToItem(conversationRows.lastIndex)
            initialScrollDone = true
            return@LaunchedEffect
        }

        if (isNearBottom) {
            listState.animateScrollToItem(conversationRows.lastIndex)
        }
    }

    LaunchedEffect(highlightedMessageId) {
        if (highlightedMessageId != null) {
            delay(2200)
            highlightedMessageId = null
        }
    }

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        delay(3000)
        viewModel.dismissError(error)
    }

    LaunchedEffect(state.info) {
        val info = state.info ?: return@LaunchedEffect
        delay(3000)
        viewModel.dismissInfo(info)
    }

    LaunchedEffect(state.scrollToMessageId, conversationRows.size) {
        val targetMessageId = state.scrollToMessageId ?: return@LaunchedEffect
        val targetIndex = messageRowIndexById[targetMessageId] ?: return@LaunchedEffect
        listState.scrollToItem(targetIndex)
        highlightedMessageId = targetMessageId
        viewModel.onScrollToMessageHandled()
    }

    LaunchedEffect(listState, state.anchoredMessageId) {
        if (state.anchoredMessageId == null) return@LaunchedEffect

        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            Triple(
                listState.firstVisibleItemIndex,
                lastVisible,
                listState.layoutInfo.totalItemsCount,
            )
        }.collect { (firstVisible, lastVisible, totalItems) ->
            if (totalItems <= 0) return@collect
            if (firstVisible <= 2) {
                viewModel.loadOlderMessages()
            }
            if (lastVisible >= totalItems - 3) {
                viewModel.loadNewerMessages()
            }
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
                Banner(
                    text = it,
                    isError = true,
                    onDismiss = viewModel::dismissError,
                )
            }

            state.info?.let {
                Banner(
                    text = it,
                    onDismiss = viewModel::dismissInfo,
                )
            }

            conversationBlockedReason?.let {
                Banner(text = it, isError = state.isConversationPurged)
            }

            state.pinnedMessage?.let { pinned ->
                PinnedMessageHeader(
                    preview = pinned,
                    onClick = viewModel::openPinnedMessageWindow,
                    onUnpin = viewModel::unpinMessage,
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                state = listState,
                verticalArrangement = Arrangement.Bottom,
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
                                forceMine = state.isSavedMessages,
                                groupPosition = row.groupPosition,
                                isHighlighted = highlightedMessageId == row.message.messageId,
                                isDeleting = state.deletingMessageIds.contains(row.message.messageId),
                                onDeleteLocal = { viewModel.deleteMessageLocal(row.message.messageId) },
                                onDeleteGlobal = { viewModel.deleteMessageGlobal(row.message.messageId) },
                                onAttachmentsClick = {
                                    viewModel.showMessageAttachments(row.message)
                                },
                                inlineAttachmentPreviews = state.inlineAttachmentPreviews,
                                onRequestInlineImagePreview = viewModel::ensureInlineImagePreview,
                                onInlineImageClick = viewModel::previewInlineImageAttachment,
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
                                onReplyMessage = {
                                    viewModel.startReply(row.message)
                                },
                                onForwardMessage = {
                                    viewModel.openForwardPicker(row.message)
                                },
                            )
                        }
                    }
                }
            }

            if (pendingAttachments.isNotEmpty()) {
                PendingAttachmentsBar(
                    items = pendingAttachments,
                    onPreview = { previewingPendingAttachment = it },
                    onRemove = { uri ->
                        pendingAttachments = pendingAttachments.filterNot { it.uri == uri }
                        if (previewingPendingAttachment?.uri == uri) {
                            previewingPendingAttachment = null
                        }
                    },
                    onClearAll = {
                        pendingAttachments = emptyList()
                        previewingPendingAttachment = null
                    },
                )
            }

            ConversationComposer(
                message = message,
                onMessageChange = { message = it },
                replyPreview = state.replyingTo,
                onCancelReply = viewModel::cancelReply,
                onAttachClick = {
                    if (conversationBlockedReason == null && activity != null) {
                        activity.startActivity(
                            SystemDocumentPickerActivity.createIntent(
                                activity = activity,
                                mimeTypes = arrayOf("*/*"),
                                allowMultiple = true,
                                requestKey = SystemDocumentPickerActivity.REQUEST_ATTACHMENTS,
                            )
                        )
                    }
                },
                onSendClick = {
                    val textToSend = message
                    val attachmentsToSend = pendingAttachments.map { it.uri }

                    viewModel.sendMessage(
                        text = textToSend,
                        attachmentUris = attachmentsToSend,
                    ) {
                        message = ""
                        pendingAttachments = emptyList()
                    }
                },
                isUploading = state.isUploadingAttachment,
                inputEnabled = conversationBlockedReason == null,
                placeholder = composerPlaceholder,
                sendEnabled = message.isNotBlank() || pendingAttachments.isNotEmpty(),
            )
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

        previewingPendingAttachment?.takeIf { it.isImage }?.let { attachment ->
            PendingAttachmentPreviewDialog(
                attachment = attachment,
                onDismiss = { previewingPendingAttachment = null },
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

        if (state.forwardingMessageId != null || state.isLoadingForwardTargets) {
            ForwardMessageDialog(
                conversations = state.forwardTargets,
                isLoading = state.isLoadingForwardTargets,
                isForwarding = state.isForwardingMessage,
                onDismiss = viewModel::dismissForwardPicker,
                onForwardToConversation = viewModel::forwardSelectedMessage,
            )
        }

    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedMessageHeader(
    preview: MessagePreview,
    onClick: () -> Unit,
    onUnpin: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 1.dp,
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = { menuExpanded = true },
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 34.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF3B82F6)),
                )

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(10.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Сообщение закреплено",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF3B82F6),
                    )
                    Text(
                        text = messagePreviewText(preview),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Снять закреп") },
                    onClick = {
                        menuExpanded = false
                        onUnpin()
                    },
                )
            }
        }
    }
}

@Composable
private fun PendingAttachmentsBar(
    items: List<PendingAttachmentUi>,
    onPreview: (PendingAttachmentUi) -> Unit,
    onRemove: (Uri) -> Unit,
    onClearAll: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "📎 Выбрано файлов: ${items.size}",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (items.size > 1) {
                    TextButton(onClick = onClearAll) {
                        Text("Очистить")
                    }
                }
            }

            items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.isImage) {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = item.displayName,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onPreview(item) },
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("📄")
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = item.displayName,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (item.isImage) "Нажмите, чтобы посмотреть" else "Файл готов к отправке",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    TextButton(
                        onClick = { onRemove(item.uri) },
                    ) {
                        Text("Убрать")
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingAttachmentPreviewDialog(
    attachment: PendingAttachmentUi,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.96f),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Закрыть", color = Color.White)
                    }

                    Text(
                        text = attachment.displayName,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                AsyncImage(
                    model = attachment.uri,
                    contentDescription = attachment.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

private fun resolveAttachmentDisplayName(
    context: Context,
    uri: Uri,
): String? {
    return context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (columnIndex >= 0 && cursor.moveToFirst()) {
            cursor.getString(columnIndex)
        } else {
            null
        }
    }
}

private fun resolveAttachmentMimeType(
    context: Context,
    uri: Uri,
): String? = context.contentResolver.getType(uri)

private fun messagePreviewText(preview: MessagePreview): String {
    return preview.text.ifBlank {
        if (preview.hasAttachments) {
            "📎 Вложение"
        } else {
            "Сообщение"
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
private fun ForwardMessageDialog(
    conversations: List<com.example.securechatapp.domain.model.ConversationListItem>,
    isLoading: Boolean,
    isForwarding: Boolean,
    onDismiss: () -> Unit,
    onForwardToConversation: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!isForwarding) onDismiss()
        },
        title = {
            Text("Переслать сообщение")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isLoading) {
                    Text("Обновляю список чатов…")
                }

                when {
                    conversations.isEmpty() -> {
                        Text("Нет доступных чатов для пересылки.")
                    }

                    else -> {
                        conversations.forEach { conversation ->
                            Button(
                                onClick = {
                                    onForwardToConversation(conversation.conversationId)
                                },
                                enabled = !isForwarding,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = conversation.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = if (conversation.isSavedMessages) {
                                            "Избранное"
                                        } else {
                                            conversation.lastMessagePreview
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isForwarding,
            ) {
                Text(if (isForwarding) "Пересылаю…" else "Закрыть")
            }
        },
    )
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
