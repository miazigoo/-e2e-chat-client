package com.example.securechatapp.ui.screens.conversation

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.securechatapp.domain.model.MessagePreview
import com.example.securechatapp.ui.theme.SecureChatTheme

@Composable
fun ConversationComposer(
    message: String,
    onMessageChange: (String) -> Unit,
    replyPreview: MessagePreview?,
    onCancelReply: () -> Unit,
    onAttachClick: () -> Unit,
    onSendClick: () -> Unit,
    isUploading: Boolean,
    inputEnabled: Boolean,
    placeholder: String,
    sendEnabled: Boolean,
) {
    var emojiExpanded by remember { mutableStateOf(false) }
    val quickEmojis = remember { listOf("😀", "😂", "🔥", "❤️", "👍", "🎉", "😮", "😢") }
    val extraColors = SecureChatTheme.extras

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
        color = extraColors.topBar,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            replyPreview?.let { preview ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "↩ ${previewDisplayText(preview)}",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )

                        TextButton(onClick = onCancelReply) {
                            Text("Отмена")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
                        ) {
                            Button(
                                onClick = onAttachClick,
                                enabled = inputEnabled,
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.fillMaxSize(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
                                    contentColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Text("📎")
                            }
                        }

                        OutlinedTextField(
                            value = message,
                            onValueChange = onMessageChange,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(placeholder) },
                            enabled = inputEnabled && !isUploading,
                            minLines = 1,
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                disabledBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                            ),
                        )

                        Box {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
                            ) {
                                Button(
                                    onClick = { emojiExpanded = true },
                                    enabled = inputEnabled && !isUploading,
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.fillMaxSize(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
                                        contentColor = MaterialTheme.colorScheme.primary,
                                    ),
                                ) {
                                    Text("😊")
                                }
                            }

                            DropdownMenu(
                                expanded = emojiExpanded,
                                onDismissRequest = { emojiExpanded = false },
                            ) {
                                quickEmojis.forEach { emoji ->
                                    DropdownMenuItem(
                                        text = { Text(emoji) },
                                        onClick = {
                                            emojiExpanded = false
                                            val suffix = if (message.isBlank() || message.endsWith(" ")) emoji else " $emoji"
                                            onMessageChange(message + suffix)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onSendClick,
                    enabled = inputEnabled && sendEnabled && !isUploading,
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
}

private fun previewDisplayText(
    preview: MessagePreview,
): String {
    return preview.text.ifBlank {
        if (preview.hasAttachments) "Вложение" else "Сообщение"
    }
}
