package com.example.securechatapp.ui.screens.conversation

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import com.example.securechatapp.domain.model.MessagePreview

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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
    ) {
        androidx.compose.foundation.layout.Column(
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
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Button(
                        onClick = onAttachClick,
                        enabled = inputEnabled,
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

                Box {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Button(
                            onClick = { emojiExpanded = true },
                            enabled = inputEnabled && !isUploading,
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.fillMaxSize(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
                        placeholder = { Text(placeholder) },
                        enabled = inputEnabled && !isUploading,
                        minLines = 1,
                        maxLines = 4,
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
