package com.example.securechatapp.ui.screens.conversation

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.securechatapp.data.files.AttachmentLocalState
import com.example.securechatapp.domain.model.AttachmentItem
import com.example.securechatapp.ui.viewmodel.InlineAttachmentPreviewUi
import java.util.Locale

@Composable
fun MessageAttachmentsDialog(
    attachments: List<AttachmentItem>,
    attachmentLocalStates: Map<Int, AttachmentLocalState>,
    inlineAttachmentPreviews: Map<Int, InlineAttachmentPreviewUi>,
    isLoading: Boolean,
    downloadingAttachmentId: Int?,
    onDismiss: () -> Unit,
    onAttachmentClick: (AttachmentItem) -> Unit,
    onRequestImagePreview: (AttachmentItem) -> Unit,
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
                            val localState = attachmentLocalStates[attachment.attachmentId]
                                ?: AttachmentLocalState.NOT_DOWNLOADED

                            AttachmentRow(
                                attachment = attachment,
                                localState = localState,
                                preview = inlineAttachmentPreviews[attachment.attachmentId],
                                isDownloading = downloadingAttachmentId == attachment.attachmentId,
                                onClick = {
                                    if (attachment.canDownload) {
                                        onAttachmentClick(attachment)
                                    }
                                },
                                onRequestImagePreview = onRequestImagePreview,
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
    attachment: AttachmentItem,
    localState: AttachmentLocalState,
    preview: InlineAttachmentPreviewUi?,
    isDownloading: Boolean,
    onClick: () -> Unit,
    onRequestImagePreview: (AttachmentItem) -> Unit,
) {
    LaunchedEffect(attachment.attachmentId, attachment.canDownload, attachment.isImage) {
        if (attachment.isImage && attachment.canDownload) {
            onRequestImagePreview(attachment)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = attachment.canDownload &&
                        localState != AttachmentLocalState.DOWNLOADING &&
                        !isDownloading,
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (attachment.isImage && attachment.canDownload) {
                AttachmentThumbnail(
                    attachment = attachment,
                    preview = preview,
                )
            } else {
                AttachmentFileIcon(
                    fileName = attachment.fileName,
                    mimeType = attachment.mimeType,
                    contentDescription = attachment.fileName,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.fileName,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = buildAttachmentSubtitle(
                        attachment = attachment,
                        localState = localState,
                        isDownloading = isDownloading,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun AttachmentThumbnail(
    attachment: AttachmentItem,
    preview: InlineAttachmentPreviewUi?,
) {
    val imageBitmap = remember(preview?.imageBytes) {
        preview?.imageBytes?.let { bytes ->
            runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }

    Surface(
        modifier = Modifier
            .size(56.dp)
            .clip(MaterialTheme.shapes.small),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
    ) {
        Box(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                imageBitmap != null -> {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = attachment.fileName,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                    )
                }

                !preview?.imageUrl.isNullOrBlank() -> {
                    AsyncImage(
                        model = preview?.imageUrl,
                        contentDescription = attachment.fileName,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                    )
                }

                preview?.hasError == true -> {
                    Text(
                        text = "⚠",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

private fun buildAttachmentSubtitle(
    attachment: AttachmentItem,
    localState: AttachmentLocalState,
    isDownloading: Boolean,
): String {
    if (!attachment.canDownload) return "Файл недоступен"
    if (isDownloading || localState == AttachmentLocalState.DOWNLOADING) return "Скачивается..."
    if (localState == AttachmentLocalState.DOWNLOADED) return "Уже скачан • нажми, чтобы открыть"
    if (localState == AttachmentLocalState.FAILED) return "Ошибка загрузки • нажми, чтобы повторить"

    val parts = mutableListOf<String>()

    if (attachment.isImage) {
        parts += "нажми, чтобы открыть"
    }

    when {
        attachment.isImage -> parts += "изображение"
        attachment.mimeType?.startsWith("video/") == true -> parts += "видео"
        attachment.mimeType?.startsWith("audio/") == true -> parts += "аудио"
        attachment.mimeType?.isNotBlank() == true -> parts += attachment.mimeType
    }

    parts += formatFileSize(attachment.fileSize)

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
