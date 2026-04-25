package com.example.securechatapp.data.repository

import com.example.securechatapp.data.remote.api.ChatBackendApi
import com.example.securechatapp.domain.model.AttachmentDownloadInfo
import com.example.securechatapp.domain.model.AttachmentItem
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class AttachmentRepository @Inject constructor(
    private val api: ChatBackendApi,
    json: Json,
) : BaseApiRepository(json) {

    suspend fun listMessageAttachments(
        messageId: Int,
    ): List<AttachmentItem> {
        return safe { api.listMessageAttachments(messageId).data }
            .items
            .map { item ->
                AttachmentItem(
                    attachmentId = item.attachmentId,
                    fileName = item.encryptedFileName
                        ?.takeIf { it.isNotBlank() }
                        ?: fallbackAttachmentName(
                            attachmentId = item.attachmentId,
                            mimeType = item.mimeHint,
                        ),
                    mimeType = item.mimeHint,
                    fileSize = item.fileSize,
                    canDownload = isAttachmentDownloadable(
                        uploadStatus = item.uploadStatus,
                        deletedAt = item.deletedAt,
                    ),
                )
            }
    }

    suspend fun getAttachmentDownloadInfo(
        attachmentId: Int,
    ): AttachmentDownloadInfo? {
        val data = safe { api.getAttachmentMetadata(attachmentId).data }

        val url = data.downloadUrl
        if (!data.canDownload || url.isNullOrBlank()) return null

        val fileName = data.encryptedFileName
            ?.takeIf { it.isNotBlank() }
            ?: fallbackAttachmentName(
                attachmentId = data.attachmentId,
                mimeType = data.mimeHint,
            )

        return AttachmentDownloadInfo(
            attachmentId = data.attachmentId,
            downloadUrl = url,
            fileName = fileName,
            mimeType = data.mimeHint,
        )
    }

    private fun fallbackAttachmentName(
        attachmentId: Int,
        mimeType: String?,
    ): String {
        return when {
            mimeType?.startsWith("image/") == true -> "image_$attachmentId"
            mimeType?.startsWith("video/") == true -> "video_$attachmentId"
            mimeType?.startsWith("audio/") == true -> "audio_$attachmentId"
            else -> "attachment_$attachmentId"
        }
    }

    private fun isAttachmentDownloadable(
        uploadStatus: String,
        deletedAt: String?,
    ): Boolean {
        if (deletedAt != null) return false

        return when (uploadStatus.lowercase(Locale.ROOT)) {
            "uploaded",
            "linked",
                -> true

            else -> false
        }
    }
}
