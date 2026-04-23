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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
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
                }
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
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
