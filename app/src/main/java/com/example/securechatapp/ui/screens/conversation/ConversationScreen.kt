package com.example.securechatapp.ui.screens.conversation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.securechatapp.ui.viewmodel.ConversationViewModel
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

private const val INITIAL_VISIBLE_ROWS = 40
private const val VISIBLE_ROWS_STEP = 30

@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var message by remember { mutableStateOf("") }
    var pendingAttachmentUri by remember { mutableStateOf<Uri?>(null) }
    var pendingAttachmentName by remember { mutableStateOf<String?>(null) }
    var visibleRowsCount by remember { mutableStateOf(INITIAL_VISIBLE_ROWS) }
    var previousTotalRows by remember { mutableStateOf(0) }

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

    val subtitle = remember(state.messages, state.protectionMode) {
        buildConversationSubtitle(state.messages)
    }

    val boundedVisibleRowsCount = min(
        visibleRowsCount,
        max(conversationRows.size, INITIAL_VISIBLE_ROWS),
    )
    val visibleRows = remember(conversationRows, boundedVisibleRowsCount) {
        if (conversationRows.size <= boundedVisibleRowsCount) {
            conversationRows
        } else {
            conversationRows.takeLast(boundedVisibleRowsCount)
        }
    }
    val hiddenRowsCount = (conversationRows.size - visibleRows.size).coerceAtLeast(0)

    val isAtBottom by remember(listState, visibleRows) {
        derivedStateOf {
            if (visibleRows.isEmpty()) {
                true
            } else {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= visibleRows.lastIndex - 1
            }
        }
    }

    val shouldLoadMore by remember(listState, hiddenRowsCount) {
        derivedStateOf {
            hiddenRowsCount > 0 && listState.firstVisibleItemIndex <= 2
        }
    }

    val showJumpToBottom by remember(isAtBottom, state.messages) {
        derivedStateOf {
            !isAtBottom && state.messages.isNotEmpty()
        }
    }

    LaunchedEffect(conversationRows.size) {
        if (conversationRows.size < previousTotalRows) {
            visibleRowsCount = max(INITIAL_VISIBLE_ROWS, visibleRowsCount)
        }

        val shouldStickToBottom = previousTotalRows == 0 || isAtBottom
        previousTotalRows = conversationRows.size

        if (shouldStickToBottom && visibleRows.isNotEmpty()) {
            listState.animateScrollToItem(visibleRows.lastIndex)
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            visibleRowsCount = min(
                visibleRowsCount + VISIBLE_ROWS_STEP,
                max(conversationRows.size, visibleRowsCount),
            )
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
                title = if (state.title.isBlank()) "Диалог" else state.title,
                subtitle = subtitle,
                onBack = onBack,
                onRefresh = viewModel::refreshConversation,
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
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                if (hiddenRowsCount > 0) {
                    item("load_more") {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            InlineActionChip(
                                text = "Показать более ранние сообщения ($hiddenRowsCount)",
                                onClick = {
                                    visibleRowsCount = min(
                                        visibleRowsCount + VISIBLE_ROWS_STEP,
                                        conversationRows.size,
                                    )
                                },
                            )
                        }
                    }
                }

                if (state.isLoading && state.messages.isEmpty()) {
                    item("loading") {
                        ConversationLoadingSkeleton()
                    }
                }

                if (!state.isLoading && state.messages.isEmpty()) {
                    item("empty") {
                        EmptyConversationHint(
                            text = "Начни диалог первым сообщением",
                            actionLabel = "Обновить чат",
                            onActionClick = viewModel::refreshConversation,
                        )
                    }
                }

                items(
                    items = visibleRows,
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
                            )
                        }
                    }
                }
            }

            ConversationComposer(
                message = message,
                attachmentName = pendingAttachmentName,
                onMessageChange = { message = it },
                onAttachClick = { attachmentPicker.launch("*/*") },
                onClearAttachment = {
                    pendingAttachmentUri = null
                    pendingAttachmentName = null
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
                sendEnabled = message.isNotBlank() || pendingAttachmentUri != null,
            )
        }

        JumpToBottomButton(
            visible = showJumpToBottom,
            onClick = {
                if (visibleRows.isNotEmpty()) {
                    coroutineScope.launch {
                        listState.animateScrollToItem(visibleRows.lastIndex)
                    }
                }
            },
        )

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
    }
}
