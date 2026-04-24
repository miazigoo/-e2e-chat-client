package com.example.securechatapp.ui.screens.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.example.securechatapp.domain.model.ChatMessage
import com.example.securechatapp.domain.model.MessageSendStatus
import com.example.securechatapp.ui.theme.TgDarkIncomingBubble
import com.example.securechatapp.ui.theme.TgDarkOutgoingBubble
import com.example.securechatapp.ui.theme.TgIncomingBubble
import com.example.securechatapp.ui.theme.TgOutgoingBubble

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    msg: ChatMessage,
    groupPosition: MessageGroupPosition,
    isDeleting: Boolean,
    onDeleteLocal: () -> Unit,
    onDeleteGlobal: () -> Unit,
    onAttachmentsClick: () -> Unit,
    onRetrySend: () -> Unit = {},
    onRemovePending: () -> Unit = {},
) {
    val dark = isSystemInDarkTheme()
    val bubbleColor = when {
        msg.isMine && dark -> TgDarkOutgoingBubble
        msg.isMine && !dark -> TgOutgoingBubble
        !msg.isMine && dark -> TgDarkIncomingBubble
        else -> TgIncomingBubble
    }

    var menuExpanded by remember { mutableStateOf(false) }

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
        "Вложение"
    } else {
        msg.text
    }

    val showTail = groupPosition == MessageGroupPosition.SINGLE || groupPosition == MessageGroupPosition.END

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = bottomPadding),
        horizontalArrangement = if (msg.isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!msg.isMine && showTail) {
            BubbleTail(
                color = bubbleColor,
                isMine = false,
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth(0.82f),
        ) {
            Surface(
                shape = bubbleShape(
                    isMine = msg.isMine,
                    groupPosition = groupPosition,
                ),
                color = bubbleColor,
                tonalElevation = 1.dp,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { menuExpanded = true },
                    )
                    .animateContentSize(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    AnimatedVisibility(
                        visible = msg.hasAttachments,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Column {
                            TextButton(
                                onClick = onAttachmentsClick,
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(
                                    text = attachmentLabel(msg),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }

                            if (bodyText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                    }

                    if (bodyText.isNotBlank()) {
                        Text(
                            text = bodyText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    if (msg.sendStatus == MessageSendStatus.FAILED && !msg.errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = msg.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatMessageTime(msg.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (msg.isMine) {
                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = statusText(msg),
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor(msg),
                            )
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
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

                    if (msg.isMine) {
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

        if (msg.isMine && showTail) {
            BubbleTail(
                color = bubbleColor,
                isMine = true,
            )
        }
    }
}

@Composable
private fun BubbleTail(
    color: androidx.compose.ui.graphics.Color,
    isMine: Boolean,
) {
    Canvas(
        modifier = Modifier
            .width(10.dp)
            .height(14.dp),
    ) {
        val path = Path().apply {
            if (isMine) {
                moveTo(0f, size.height)
                lineTo(size.width, size.height)
                lineTo(size.width, 0f)
                quadraticBezierTo(
                    x1 = size.width * 0.25f,
                    y1 = size.height * 0.35f,
                    x2 = 0f,
                    y2 = size.height,
                )
            } else {
                moveTo(0f, 0f)
                lineTo(0f, size.height)
                lineTo(size.width, size.height)
                quadraticBezierTo(
                    x1 = size.width * 0.75f,
                    y1 = size.height * 0.35f,
                    x2 = 0f,
                    y2 = 0f,
                )
            }
            close()
        }
        drawPath(
            path = path,
            color = color,
        )
    }
}

private fun attachmentLabel(msg: ChatMessage): String {
    return when (msg.attachments.size) {
        0 -> "📎 Открыть вложения"
        1 -> "📎 ${msg.attachments.first().fileName}"
        else -> "📎 Вложения (${msg.attachments.size})"
    }
}

private fun statusText(msg: ChatMessage): String {
    return when (msg.sendStatus) {
        MessageSendStatus.SENDING -> "⏳"
        MessageSendStatus.FAILED -> "⚠"
        MessageSendStatus.SENT -> when {
            msg.readAt != null -> "✓✓"
            msg.deliveredAt != null -> "✓✓"
            else -> "✓"
        }
    }
}

@Composable
private fun statusColor(msg: ChatMessage) = when (msg.sendStatus) {
    MessageSendStatus.SENDING -> MaterialTheme.colorScheme.onSurfaceVariant
    MessageSendStatus.FAILED -> MaterialTheme.colorScheme.error
    MessageSendStatus.SENT -> {
        if (msg.readAt != null) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
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
                RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 10.dp, bottomEnd = 6.dp)
            MessageGroupPosition.MIDDLE ->
                RoundedCornerShape(topStart = 18.dp, topEnd = 12.dp, bottomStart = 10.dp, bottomEnd = 6.dp)
            MessageGroupPosition.END ->
                RoundedCornerShape(topStart = 18.dp, topEnd = 12.dp, bottomStart = 18.dp, bottomEnd = 6.dp)
        }
    } else {
        when (groupPosition) {
            MessageGroupPosition.SINGLE ->
                RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 18.dp)
            MessageGroupPosition.START ->
                RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 10.dp)
            MessageGroupPosition.MIDDLE ->
                RoundedCornerShape(topStart = 12.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 10.dp)
            MessageGroupPosition.END ->
                RoundedCornerShape(topStart = 12.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 18.dp)
        }
    }
}
