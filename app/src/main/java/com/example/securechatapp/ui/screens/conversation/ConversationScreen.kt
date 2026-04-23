package com.example.securechatapp.ui.screens.conversation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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

    val subtitle = remember(state.messages) {
        buildConversationSubtitle(state.messages)
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
                title = if (state.title.isBlank()) "Диалог" else state.title,
                subtitle = subtitle,
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
                                }
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
