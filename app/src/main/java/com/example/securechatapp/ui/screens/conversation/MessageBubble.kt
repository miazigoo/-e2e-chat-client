package com.example.securechatapp.ui.screens.conversation

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.securechatapp.domain.model.AttachmentItem
import com.example.securechatapp.domain.model.ChatMessage
import com.example.securechatapp.domain.model.MessagePreview
import com.example.securechatapp.domain.model.MessageSendStatus
import com.example.securechatapp.ui.theme.SecureChatTheme
import com.example.securechatapp.ui.viewmodel.InlineAttachmentPreviewUi

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    msg: ChatMessage,
    forceMine: Boolean = false,
    groupPosition: MessageGroupPosition,
    isHighlighted: Boolean = false,
    isDeleting: Boolean,
    onDeleteLocal: () -> Unit,
    onDeleteGlobal: () -> Unit,
    onAttachmentsClick: () -> Unit,
    inlineAttachmentPreviews: Map<Int, InlineAttachmentPreviewUi> = emptyMap(),
    onRequestInlineImagePreview: (AttachmentItem) -> Unit = {},
    onInlineImageClick: (AttachmentItem) -> Unit = {},
    onRetrySend: () -> Unit = {},
    onRemovePending: () -> Unit = {},
    onSetReaction: (String) -> Unit = {},
    onRemoveReaction: () -> Unit = {},
    onPinMessage: () -> Unit = {},
    onReplyMessage: () -> Unit = {},
    onForwardMessage: () -> Unit = {},
) {
    val isMine = msg.isMine || forceMine
    val dark = isSystemInDarkTheme()
    val extraColors = SecureChatTheme.extras
    val bubbleColor = when {
        isMine && dark -> extraColors.outgoingBubble
        isMine && !dark -> extraColors.outgoingBubble
        !isMine && dark -> extraColors.incomingBubble
        else -> extraColors.incomingBubble
    }
    val bubbleTextColor = if (isMine) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val bubbleMetaColor = if (isMine) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val bubbleBorder = when {
        isHighlighted -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
        isMine -> null
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var reactionPickerExpanded by remember { mutableStateOf(false) }
    val myReaction = remember(msg.reactions) {
        msg.reactions.firstOrNull { it.me }?.reaction
    }

    val topPadding = when (groupPosition) {
        MessageGroupPosition.SINGLE,
        MessageGroupPosition.START -> 8.dp
        MessageGroupPosition.MIDDLE,
        MessageGroupPosition.END -> 2.dp
    }

    val bottomPadding = when (groupPosition) {
        MessageGroupPosition.SINGLE,
        MessageGroupPosition.END -> 8.dp
        MessageGroupPosition.START,
        MessageGroupPosition.MIDDLE -> 2.dp
    }

    val bodyText = if (msg.hasAttachments && msg.text == "[attachment]") {
        summarizeAttachments(msg.attachments)
    } else {
        msg.text
    }
    val imageAttachments = remember(msg.attachments) {
        msg.attachments.filter { it.isImage }
    }
    val nonImageAttachments = remember(msg.attachments) {
        msg.attachments.filter { !it.isImage }
    }
    val shouldShowAttachmentButton = msg.hasAttachments && msg.attachments.isEmpty()
    val shouldShowBodyText = bodyText.isNotBlank() && !(
            msg.text == "[attachment]" && msg.attachments.isNotEmpty()
            )

    LaunchedEffect(imageAttachments) {
        imageAttachments.forEach(onRequestInlineImagePreview)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = bottomPadding),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Box {
            Surface(
                shape = bubbleShape(
                    isMine = isMine,
                    groupPosition = groupPosition,
                ),
                color = bubbleColor,
                border = bubbleBorder,
                tonalElevation = 1.dp,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { menuExpanded = true },
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    msg.forwardPreview?.let { preview ->
                        MessageContextPreview(
                            label = "↪ Переслано",
                            preview = preview,
                            textColor = bubbleTextColor,
                            borderColor = bubbleMetaColor.copy(alpha = 0.35f),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    msg.replyPreview?.let { preview ->
                        MessageContextPreview(
                            label = "↩ Ответ",
                            preview = preview,
                            textColor = bubbleTextColor,
                            borderColor = bubbleMetaColor.copy(alpha = 0.35f),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (imageAttachments.isNotEmpty()) {
                        InlineImageAttachmentsBlock(
                            attachments = imageAttachments,
                            previews = inlineAttachmentPreviews,
                            onImageClick = onInlineImageClick,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (nonImageAttachments.isNotEmpty()) {
                        InlineFileAttachmentsBlock(
                            attachments = nonImageAttachments,
                            onClick = onAttachmentsClick,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (shouldShowAttachmentButton) {
                        TextButton(
                            onClick = onAttachmentsClick,
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(
                                text = "📎 Открыть вложения",
                                style = MaterialTheme.typography.bodySmall,
                                color = bubbleTextColor,
                                modifier = Modifier.alpha(0.9f),
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    if (shouldShowBodyText) {
                        Text(
                            text = bodyText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = bubbleTextColor,
                        )
                    }

                    if (msg.reactions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            msg.reactions.take(3).forEach { reaction ->
                                CompactReactionChip(
                                    text = "${reaction.reaction} ${reaction.count}",
                                    selected = reaction.me,
                                    onClick = {
                                        if (reaction.me) onRemoveReaction() else onSetReaction(reaction.reaction)
                                    },
                                )
                            }

                            val hiddenCount = (msg.reactions.size - 3).coerceAtLeast(0)
                            if (hiddenCount > 0) {
                                CompactReactionChip(
                                    text = "+$hiddenCount",
                                    selected = false,
                                    onClick = {},
                                )
                            }
                        }
                    }

                    if (
                        msg.sendStatus == MessageSendStatus.FAILED &&
                        !msg.errorMessage.isNullOrBlank()
                    ) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = msg.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatMessageTime(msg.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = bubbleMetaColor,
                        )

                        if (isMine) {
                            Spacer(modifier = Modifier.width(6.dp))

                            val statusText = when (msg.sendStatus) {
                                MessageSendStatus.SENDING -> "🕓"
                                MessageSendStatus.FAILED -> "!"
                                MessageSendStatus.SENT -> when {
                                    msg.readAt != null -> "✓✓"
                                    msg.deliveredAt != null -> "✓✓"
                                    else -> "✓"
                                }
                            }

                            val statusColor = when (msg.sendStatus) {
                                MessageSendStatus.SENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                                MessageSendStatus.FAILED -> MaterialTheme.colorScheme.error
                                MessageSendStatus.SENT -> {
                                    if (msg.readAt != null) {
                                        Color(0xFF4FC3F7)
                                    } else if (msg.deliveredAt != null) {
                                        bubbleMetaColor.copy(alpha = 0.95f)
                                    } else {
                                        bubbleMetaColor
                                    }
                                }
                            }

                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelMedium,
                                color = statusColor,
                            )
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                if (msg.messageId > 0) {
                    DropdownMenuItem(
                        text = { Text("Реакции") },
                        onClick = {
                            menuExpanded = false
                            reactionPickerExpanded = true
                        },
                    )
                }

                if (msg.reactions.any { it.me }) {
                    DropdownMenuItem(
                        text = { Text("Убрать реакцию") },
                        onClick = {
                            menuExpanded = false
                            onRemoveReaction()
                        },
                    )
                }

                if (msg.messageId > 0) {
                    DropdownMenuItem(
                        text = { Text("Ответить") },
                        onClick = {
                            menuExpanded = false
                            onReplyMessage()
                        },
                    )

                    DropdownMenuItem(
                        text = { Text("Переслать") },
                        onClick = {
                            menuExpanded = false
                            onForwardMessage()
                        },
                    )

                    DropdownMenuItem(
                        text = { Text("Закрепить") },
                        onClick = {
                            menuExpanded = false
                            onPinMessage()
                        },
                    )
                }

                if (msg.messageId < 0) {
                    if (msg.sendStatus == MessageSendStatus.FAILED) {
                        DropdownMenuItem(
                            text = { Text("Повторить") },
                            onClick = {
                                menuExpanded = false
                                onRetrySend()
                            },
                        )
                    }

                    DropdownMenuItem(
                        text = { Text("Удалить") },
                        onClick = {
                            menuExpanded = false
                            onRemovePending()
                        },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(if (isDeleting) "Обработка..." else "Скрыть у себя") },
                        onClick = {
                            menuExpanded = false
                            if (!isDeleting) onDeleteLocal()
                        },
                    )

                    if (isMine) {
                        DropdownMenuItem(
                            text = { Text(if (isDeleting) "Обработка..." else "Удалить у всех") },
                            onClick = {
                                menuExpanded = false
                                if (!isDeleting) onDeleteGlobal()
                            },
                        )
                    }
                }
            }
        }
    }

    if (reactionPickerExpanded) {
        ReactionPickerDialog(
            currentReaction = myReaction,
            onDismiss = { reactionPickerExpanded = false },
            onSelectReaction = { reaction ->
                reactionPickerExpanded = false
                onSetReaction(reaction)
            },
            onRemoveReaction = {
                reactionPickerExpanded = false
                onRemoveReaction()
            },
        )
    }
}

@Composable
private fun InlineImageAttachmentsBlock(
    attachments: List<AttachmentItem>,
    previews: Map<Int, InlineAttachmentPreviewUi>,
    onImageClick: (AttachmentItem) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        attachments.forEach { attachment ->
            val preview = previews[attachment.attachmentId]
            InlineImageAttachmentCard(
                attachment = attachment,
                preview = preview,
                onClick = { onImageClick(attachment) },
            )
        }
    }
}

@Composable
private fun InlineFileAttachmentsBlock(
    attachments: List<AttachmentItem>,
    onClick: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        attachments.forEach { attachment ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onClick),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AttachmentFileIcon(
                        fileName = attachment.fileName,
                        mimeType = attachment.mimeType,
                        contentDescription = attachment.fileName,
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = attachment.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = describeAttachment(attachment),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Text(
                        text = "Открыть",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineImageAttachmentCard(
    attachment: AttachmentItem,
    preview: InlineAttachmentPreviewUi?,
    onClick: () -> Unit,
) {
    val imageBitmap = remember(preview?.imageBytes) {
        preview?.imageBytes?.let { bytes ->
            runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            when {
                imageBitmap != null -> {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = attachment.fileName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }

                !preview?.imageUrl.isNullOrBlank() -> {
                    AsyncImage(
                        model = preview?.imageUrl,
                        contentDescription = attachment.fileName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }

                preview?.hasError == true -> {
                    Text(
                        text = "Не удалось загрузить превью",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = if (preview?.isLoading == true) {
                                "Загружаем изображение..."
                            } else {
                                "Готовим превью..."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageContextPreview(
    label: String,
    preview: MessagePreview,
    textColor: Color,
    borderColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = textColor.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.78f),
            )
            Text(
                text = previewDisplayText(preview),
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.92f),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun CompactReactionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onClick,
            )
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun bubbleShape(
    isMine: Boolean,
    groupPosition: MessageGroupPosition,
): RoundedCornerShape {
    return if (isMine) {
        when (groupPosition) {
            MessageGroupPosition.SINGLE ->
                RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp)
            MessageGroupPosition.START ->
                RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 8.dp, bottomEnd = 6.dp)
            MessageGroupPosition.MIDDLE ->
                RoundedCornerShape(topStart = 18.dp, topEnd = 10.dp, bottomStart = 8.dp, bottomEnd = 6.dp)
            MessageGroupPosition.END ->
                RoundedCornerShape(topStart = 18.dp, topEnd = 10.dp, bottomStart = 18.dp, bottomEnd = 6.dp)
        }
    } else {
        when (groupPosition) {
            MessageGroupPosition.SINGLE ->
                RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 18.dp)
            MessageGroupPosition.START ->
                RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 8.dp)
            MessageGroupPosition.MIDDLE ->
                RoundedCornerShape(topStart = 10.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 8.dp)
            MessageGroupPosition.END ->
                RoundedCornerShape(topStart = 10.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 18.dp)
        }
    }
}

private fun previewDisplayText(
    preview: MessagePreview,
): String {
    return preview.text.ifBlank {
        if (preview.hasAttachments) {
            "Вложение"
        } else {
            "Сообщение"
        }
    }
}

private fun summarizeAttachments(
    attachments: List<AttachmentItem>,
): String {
    if (attachments.isEmpty()) return "Вложение"
    if (attachments.size == 1) return attachments.first().fileName.ifBlank { "Вложение" }
    val firstName = attachments.first().fileName.ifBlank { "Вложение" }
    val remaining = attachments.size - 1
    return "$firstName и ещё $remaining ${attachmentCountWord(remaining)}"
}

private fun describeAttachment(
    attachment: AttachmentItem,
): String {
    val parts = buildList {
        when {
            attachment.mimeType?.startsWith("audio/") == true -> add("аудио")
            attachment.mimeType?.startsWith("video/") == true -> add("видео")
            attachment.mimeType?.isNotBlank() == true -> add(attachment.mimeType)
        }
        add(formatAttachmentSize(attachment.fileSize))
    }
    return parts.joinToString(" • ")
}

private fun formatAttachmentSize(
    bytes: Long,
): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) {
        "${value.toLong()} ${units[unitIndex]}"
    } else {
        String.format("%.1f %s", value, units[unitIndex])
    }
}

private fun attachmentCountWord(
    count: Int,
): String {
    val mod10 = count % 10
    val mod100 = count % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "файл"
        mod10 in 2..4 && mod100 !in 12..14 -> "файла"
        else -> "файлов"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReactionPickerDialog(
    currentReaction: String?,
    onDismiss: () -> Unit,
    onSelectReaction: (String) -> Unit,
    onRemoveReaction: () -> Unit,
) {
    val reactions = remember {
        listOf(
            "👍", "❤️", "🔥", "😂", "😮", "😢",
            "👏", "🎉", "🤔", "👀", "🙏", "💯",
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Реакции")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (currentReaction.isNullOrBlank()) {
                        "Выбери реакцию для сообщения"
                    } else {
                        "Текущая реакция: $currentReaction"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    reactions.forEach { reaction ->
                        FilterChip(
                            selected = currentReaction == reaction,
                            onClick = {
                                if (currentReaction == reaction) {
                                    onRemoveReaction()
                                } else {
                                    onSelectReaction(reaction)
                                }
                            },
                            label = {
                                Text(reaction)
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
        dismissButton = {
            if (!currentReaction.isNullOrBlank()) {
                TextButton(onClick = onRemoveReaction) {
                    Text("Убрать")
                }
            }
        },
    )
}
