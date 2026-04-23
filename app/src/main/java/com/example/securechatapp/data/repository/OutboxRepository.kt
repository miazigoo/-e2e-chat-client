package com.example.securechatapp.data.repository

import com.example.securechatapp.core.crypto.AttachmentCryptoEngine
import com.example.securechatapp.core.crypto.DevCryptoEngine
import com.example.securechatapp.core.crypto.EncryptedAttachmentDescriptor
import com.example.securechatapp.data.local.db.SecureChatDatabase
import com.example.securechatapp.data.local.db.decodeAttachmentsJson
import com.example.securechatapp.data.local.db.encodeAttachmentsJson
import com.example.securechatapp.data.local.entity.PendingMessageOutboxEntity
import com.example.securechatapp.domain.model.AttachmentItem
import com.example.securechatapp.domain.model.ChatMessage
import com.example.securechatapp.domain.model.MessageSendStatus
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class PendingOutgoingMessage(
    val localMessageId: Int,
    val conversationId: Int,
    val recipientUserId: Int,
    val clientMessageUuid: String,
    val plainText: String,
    val attachmentIds: List<Int>,
    val attachmentDescriptors: List<EncryptedAttachmentDescriptor>,
    val createdAt: String,
    val status: MessageSendStatus,
    val errorMessage: String? = null,
)

@Singleton
class OutboxRepository @Inject constructor(
    database: SecureChatDatabase,
    private val json: Json,
    private val crypto: DevCryptoEngine,
    private val attachmentCryptoEngine: AttachmentCryptoEngine,
) {
    private val dao = database.pendingMessageOutboxDao()

    fun observePendingMessages(
        conversationId: Int,
    ): Flow<List<ChatMessage>> {
        return dao.observeForConversation(conversationId).map { entities ->
            entities.map { entity -> entity.toDomain() }
        }
    }

    suspend fun enqueuePendingMessage(
        conversationId: Int,
        recipientUserId: Int,
        plainText: String,
        attachmentIds: List<Int>,
        attachmentDescriptors: List<EncryptedAttachmentDescriptor>,
    ): Int {
        val localMessageId = buildLocalMessageId()
        val createdAt = crypto.nowIso()
        val clientMessageUuid = UUID.randomUUID().toString()

        val attachmentPreviews = attachmentDescriptors.map { descriptor ->
            descriptorToPreviewAttachment(descriptor)
        }

        val entity = PendingMessageOutboxEntity(
            localMessageId = localMessageId,
            conversationId = conversationId,
            recipientUserId = recipientUserId,
            clientMessageUuid = clientMessageUuid,
            plainText = plainText,
            attachmentIdsCsv = attachmentIds.distinct().joinToString(","),
            attachmentDescriptorsJson = encodeAttachmentDescriptors(attachmentDescriptors),
            attachmentPreviewJson = encodeAttachmentsJson(
                json = json,
                attachments = attachmentPreviews,
            ),
            hasAttachments = attachmentIds.isNotEmpty() || attachmentDescriptors.isNotEmpty(),
            createdAt = createdAt,
            status = STATUS_QUEUED,
            errorMessage = null,
        )

        dao.upsert(entity)
        return localMessageId
    }

    suspend fun getPendingMessage(
        localMessageId: Int,
    ): PendingOutgoingMessage? {
        return dao.getByLocalMessageId(localMessageId)?.toPending()
    }

    suspend fun listQueuedMessageIds(
        conversationId: Int,
    ): List<Int> {
        return dao.listByConversationAndStatus(
            conversationId = conversationId,
            status = STATUS_QUEUED,
        ).map { it.localMessageId }
    }

    suspend fun markSending(
        localMessageId: Int,
    ) {
        dao.updateStatus(
            localMessageId = localMessageId,
            status = STATUS_SENDING,
            errorMessage = null,
        )
    }

    suspend fun markFailed(
        localMessageId: Int,
        errorMessage: String?,
    ) {
        dao.updateStatus(
            localMessageId = localMessageId,
            status = STATUS_FAILED,
            errorMessage = errorMessage,
        )
    }

    suspend fun requeueFailedMessage(
        localMessageId: Int,
    ) {
        dao.updateStatus(
            localMessageId = localMessageId,
            status = STATUS_QUEUED,
            errorMessage = null,
        )
    }

    suspend fun requeueSendingMessages(
        conversationId: Int,
    ) {
        dao.requeueSendingForConversation(conversationId)
    }

    suspend fun deletePendingMessage(
        localMessageId: Int,
    ) {
        dao.deleteByLocalMessageId(localMessageId)
    }

    private fun descriptorToPreviewAttachment(
        descriptor: EncryptedAttachmentDescriptor,
    ): AttachmentItem {
        val fileName = decryptAttachmentFileName(descriptor)
            ?: fallbackAttachmentName(
                attachmentId = descriptor.attachmentId,
                mimeType = descriptor.mimeType,
            )

        return AttachmentItem(
            attachmentId = descriptor.attachmentId,
            fileName = fileName,
            mimeType = descriptor.mimeType,
            fileSize = descriptor.fileSize,
            canDownload = false,
            blobKeyBase64 = descriptor.blobKeyBase64.takeIf { it.isNotBlank() },
            blobNonceBase64 = descriptor.blobNonceBase64.takeIf { it.isNotBlank() },
            sha256EncryptedBlob = descriptor.sha256EncryptedBlob.takeIf { it.isNotBlank() },
        )
    }

    private fun decryptAttachmentFileName(
        descriptor: EncryptedAttachmentDescriptor,
    ): String? {
        val ciphertextBase64 = descriptor.encryptedFileName
        val keyBase64 = descriptor.fileNameKeyBase64
        val nonceBase64 = descriptor.fileNameNonceBase64

        if (
            ciphertextBase64.isNullOrBlank() ||
            keyBase64.isNullOrBlank() ||
            nonceBase64.isNullOrBlank()
        ) {
            return null
        }

        return runCatching {
            attachmentCryptoEngine.decryptText(
                ciphertextBase64 = ciphertextBase64,
                keyBase64 = keyBase64,
                nonceBase64 = nonceBase64,
            )
        }.getOrNull()
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

    private fun encodeAttachmentDescriptors(
        items: List<EncryptedAttachmentDescriptor>,
    ): String {
        return json.encodeToString(
            ListSerializer(EncryptedAttachmentDescriptor.serializer()),
            items,
        )
    }

    private fun decodeAttachmentDescriptors(
        raw: String,
    ): List<EncryptedAttachmentDescriptor> {
        return runCatching {
            json.decodeFromString(
                ListSerializer(EncryptedAttachmentDescriptor.serializer()),
                raw,
            )
        }.getOrDefault(emptyList())
    }

    private fun PendingMessageOutboxEntity.toPending(): PendingOutgoingMessage {
        return PendingOutgoingMessage(
            localMessageId = localMessageId,
            conversationId = conversationId,
            recipientUserId = recipientUserId,
            clientMessageUuid = clientMessageUuid,
            plainText = plainText,
            attachmentIds = attachmentIdsCsv
                .split(",")
                .mapNotNull { it.trim().takeIf(String::isNotBlank)?.toIntOrNull() },
            attachmentDescriptors = decodeAttachmentDescriptors(attachmentDescriptorsJson),
            createdAt = createdAt,
            status = status.toMessageSendStatus(),
            errorMessage = errorMessage,
        )
    }

    private fun PendingMessageOutboxEntity.toDomain(): ChatMessage {
        val attachments = decodeAttachmentsJson(
            json = json,
            raw = attachmentPreviewJson,
        )

        val displayText = plainText.takeIf { it.isNotBlank() }
            ?: if (hasAttachments) "[attachment]" else ""

        val attachmentIds = attachmentIdsCsv
            .split(",")
            .mapNotNull { it.trim().takeIf(String::isNotBlank)?.toIntOrNull() }

        return ChatMessage(
            messageId = localMessageId,
            messageUuid = clientMessageUuid,
            text = displayText,
            isMine = true,
            createdAt = createdAt,
            deliveredAt = null,
            readAt = null,
            hasAttachments = hasAttachments,
            attachmentIds = attachmentIds,
            attachments = attachments,
            sendStatus = status.toMessageSendStatus(),
            errorMessage = errorMessage,
        )
    }

    private fun buildLocalMessageId(): Int {
        val value = UUID.randomUUID().hashCode().absoluteValue.coerceAtLeast(1)
        return -value
    }

    private fun String.toMessageSendStatus(): MessageSendStatus {
        return when (this) {
            STATUS_FAILED -> MessageSendStatus.FAILED
            STATUS_SENDING,
            STATUS_QUEUED -> MessageSendStatus.SENDING
            else -> MessageSendStatus.SENDING
        }
    }

    private companion object {
        const val STATUS_QUEUED = "queued"
        const val STATUS_SENDING = "sending"
        const val STATUS_FAILED = "failed"
    }
}
