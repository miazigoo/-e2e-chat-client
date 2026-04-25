package com.example.securechatapp.ui.screens.conversation

import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.unit.dp
import com.example.securechatapp.domain.model.ChatMessage
import com.example.securechatapp.domain.model.MessageSendStatus
import com.example.securechatapp.ui.theme.SecureChatTheme

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
    onSetReaction: (String) -> Unit = {},
    onRemoveReaction: () -> Unit = {},
    onPinMessage: () -> Unit = {},
) {
    val dark = isSystemInDarkTheme()
    val extraColors = SecureChatTheme.extras
    val bubbleColor = when {
        msg.isMine && dark -> extraColors.outgoingBubble
        msg.isMine && !dark -> extraColors.outgoingBubble
        !msg.isMine && dark -> extraColors.incomingBubble
        else -> extraColors.incomingBubble
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = bottomPadding),
        horizontalArrangement = if (msg.isMine) Arrangement.End else Arrangement.Start,
    ) {
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
                    if (msg.hasAttachments) {
                        TextButton(
                            onClick = onAttachmentsClick,
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(
                                text = "📎 Открыть вложения",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    Text(
                        text = bodyText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    if (msg.reactions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            msg.reactions.take(5).forEach { reaction ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (reaction.me) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    onClick = {
                                        if (reaction.me) {
                                            onRemoveReaction()
                                        } else {
                                            onSetReaction(reaction.reaction)
                                        }
                                    },
                                ) {
                                    Text(
                                        text = "${reaction.reaction} ${reaction.count}",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
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

                            val statusText = when (msg.sendStatus) {
                                MessageSendStatus.SENDING -> "⏳"
                                MessageSendStatus.FAILED -> "⚠"
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
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                }
                            }

                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
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
                listOf("👍", "❤️", "😂", "🔥", "😮", "😢").forEach { reaction ->
                    DropdownMenuItem(
                        text = { Text("$reaction Реакция") },
                        onClick = {
                            menuExpanded = false
                            onSetReaction(reaction)
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
