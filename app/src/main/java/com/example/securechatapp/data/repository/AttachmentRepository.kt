package com.example.securechatapp.data.repository

import com.example.securechatapp.data.remote.api.ChatBackendApi
import com.example.securechatapp.domain.model.MediaTag
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
            .map(::toDomainAttachment)
    }

    suspend fun listAttachmentsForMessages(
        messageIds: List<Int>,
    ): Map<Int, List<AttachmentItem>> {
        val normalizedMessageIds = messageIds.distinct().filter { it > 0 }
        if (normalizedMessageIds.isEmpty()) return emptyMap()

        return safe {
            api.listAttachmentsForMessages(
                body = com.example.securechatapp.data.remote.dto.BatchMessageAttachmentsRequestDto(
                    messageIds = normalizedMessageIds,
                ),
            ).data
        }.items.associate { group ->
            group.messageId to group.items.map(::toDomainAttachment)
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

    suspend fun setAttachmentTags(
        attachmentId: Int,
        tagIds: List<Int>,
    ): List<MediaTag> {
        val normalizedTagIds = tagIds.distinct().sorted()
        return runCatching {
            safe {
                api.setAttachmentMediaTags(
                    attachmentId = attachmentId,
                    body = com.example.securechatapp.data.remote.dto.SetAttachmentTagsRequestDto(
                        tagIds = normalizedTagIds,
                    ),
                ).data
            }.items.map(::toDomainMediaTag)
        }.getOrElse { error ->
            if (!error.isRecoverableNetworkUncertainty()) {
                throw error
            }

            val recovered = safe { api.getAttachmentMetadata(attachmentId).data }
                .mediaTags
                .map(::toDomainMediaTag)
            val recoveredIds = recovered.map { it.tagId }.sorted()
            if (recoveredIds == normalizedTagIds) {
                recovered
            } else {
                throw error
            }
        }
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

    private fun toDomainAttachment(
        item: com.example.securechatapp.data.remote.dto.AttachmentMetadataItemDto,
    ): AttachmentItem {
        return AttachmentItem(
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
            mediaTags = item.mediaTags.map(::toDomainMediaTag),
        )
    }

    private fun toDomainMediaTag(
        dto: com.example.securechatapp.data.remote.dto.MediaTagDto,
    ): MediaTag {
        return MediaTag(
            tagId = dto.tagId,
            conversationId = dto.conversationId,
            name = dto.name,
            color = dto.color,
            createdByUserId = dto.createdByUserId,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
        )
    }
}
