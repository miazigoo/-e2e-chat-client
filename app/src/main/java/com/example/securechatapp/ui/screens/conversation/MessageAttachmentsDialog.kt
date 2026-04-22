package com.example.securechatapp.ui.screens.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.securechatapp.data.repository.BackendRepository
import java.util.Locale

@Composable
fun MessageAttachmentsDialog(
    attachments: List<BackendRepository.AttachmentUi>,
    isLoading: Boolean,
    downloadingAttachmentId: Int?,
    onDismiss: () -> Unit,
    onAttachmentClick: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Вложения")
        },
        text = {
            when {
                isLoading -> {
                    Text(
                        text = "Загрузка вложений...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                attachments.isEmpty() -> {
                    Text(
                        text = "У сообщения нет доступных вложений",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        attachments.forEach { attachment ->
                            AttachmentRow(
                                attachment = attachment,
                                isDownloading = downloadingAttachmentId == attachment.attachmentId,
                                onClick = {
                                    if (attachment.canDownload) {
                                        onAttachmentClick(attachment.attachmentId)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

@Composable
private fun AttachmentRow(
    attachment: BackendRepository.AttachmentUi,
    isDownloading: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = attachment.canDownload && !isDownloading, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = "📎",
                modifier = Modifier.padding(end = 10.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.fileName,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = buildAttachmentSubtitle(
                        mimeType = attachment.mimeType,
                        fileSize = attachment.fileSize,
                        canDownload = attachment.canDownload,
                        isDownloading = isDownloading,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun buildAttachmentSubtitle(
    mimeType: String?,
    fileSize: Long,
    canDownload: Boolean,
    isDownloading: Boolean,
): String {
    if (!canDownload) return "Файл недоступен"
    if (isDownloading) return "Скачиваем..."

    val parts = mutableListOf<String>()

    mimeType?.takeIf { it.isNotBlank() }?.let { parts += it }
    parts += formatFileSize(fileSize)

    return parts.joinToString(" • ")
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"

    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0

    return when {
        bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.2f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
        else -> "$bytes B"
    }
}
