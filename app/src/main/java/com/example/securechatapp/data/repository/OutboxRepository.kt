package com.example.securechatapp.data.repository

import com.example.securechatapp.core.crypto.AttachmentCryptoEngine
import com.example.securechatapp.core.crypto.EncryptedAttachmentDescriptor
import com.example.securechatapp.crypto.engine.CryptoEngine
import com.example.securechatapp.crypto.engine.nowIso
import com.example.securechatapp.data.local.db.SecureChatDatabase
import com.example.securechatapp.data.local.db.decodeMessagePreviewJson
import com.example.securechatapp.data.local.db.decodeAttachmentsJson
import com.example.securechatapp.data.local.db.encodeMessagePreviewJson
import com.example.securechatapp.data.local.db.encodeAttachmentsJson
import com.example.securechatapp.data.local.entity.PendingMessageOutboxEntity
import com.example.securechatapp.domain.model.AttachmentItem
import com.example.securechatapp.domain.model.ChatMessage
import com.example.securechatapp.domain.model.MessagePreview
import com.example.securechatapp.domain.model.MessageSendPhase
import com.example.securechatapp.domain.model.MessageSendStatus
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

data class PendingOutgoingMessage(
    val localMessageId: Int,
    val conversationId: Int,
    val recipientUserId: Int,
    val clientMessageUuid: String,
    val plainText: String,
    val replyToMessageId: Int?,
    val replyPreview: MessagePreview?,
    val localAttachmentUris: List<String>,
    val attachmentIds: List<Int>,
    val attachmentDescriptors: List<EncryptedAttachmentDescriptor>,
    val createdAt: String,
    val status: MessageSendStatus,
    val sendPhase: MessageSendPhase?,
    val sendProgress: Int?,
    val errorMessage: String? = null,
)

@Singleton
class OutboxRepository @Inject constructor(
    database: SecureChatDatabase,
    private val json: Json,
    private val crypto: CryptoEngine,
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
        replyToMessageId: Int?,
        replyPreview: MessagePreview?,
        localAttachmentUris: List<String>,
        attachmentPreviews: List<AttachmentItem>,
        attachmentIds: List<Int>,
        attachmentDescriptors: List<EncryptedAttachmentDescriptor>,
    ): Int {
        val localMessageId = buildLocalMessageId()
        val createdAt = crypto.nowIso()
        val clientMessageUuid = UUID.randomUUID().toString()

        val resolvedAttachmentPreviews = when {
            attachmentPreviews.isNotEmpty() -> attachmentPreviews
            attachmentDescriptors.isNotEmpty() -> attachmentDescriptors.map { descriptor ->
                descriptorToPreviewAttachment(descriptor)
            }
            else -> emptyList()
        }

        val entity = PendingMessageOutboxEntity(
            localMessageId = localMessageId,
            conversationId = conversationId,
            recipientUserId = recipientUserId,
            clientMessageUuid = clientMessageUuid,
            plainText = plainText,
            replyToMessageId = replyToMessageId,
            replyPreviewJson = encodeMessagePreviewJson(replyPreview),
            localAttachmentUrisJson = encodeLocalAttachmentUris(localAttachmentUris),
            attachmentIdsCsv = attachmentIds.distinct().joinToString(","),
            attachmentDescriptorsJson = encodeAttachmentDescriptors(attachmentDescriptors),
            attachmentPreviewJson = encodeAttachmentsJson(
                json = json,
                attachments = resolvedAttachmentPreviews,
            ),
            hasAttachments = localAttachmentUris.isNotEmpty() ||
                attachmentIds.isNotEmpty() ||
                attachmentDescriptors.isNotEmpty() ||
                resolvedAttachmentPreviews.isNotEmpty(),
            createdAt = createdAt,
            status = STATUS_QUEUED,
            sendPhase = when {
                localAttachmentUris.isNotEmpty() -> MessageSendPhase.UPLOADING.name
                else -> MessageSendPhase.SENDING.name
            },
            sendProgress = if (localAttachmentUris.isNotEmpty()) 0 else null,
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

    suspend fun listQueuedMessages(): List<PendingOutgoingMessage> {
        return dao.listByStatus(STATUS_QUEUED).map { it.toPending() }
    }

    suspend fun listQueuedMessages(
        conversationId: Int,
    ): List<PendingOutgoingMessage> {
        return dao.listByConversationAndStatus(
            conversationId = conversationId,
            status = STATUS_QUEUED,
        ).map { it.toPending() }
    }

    suspend fun markSending(
        localMessageId: Int,
        phase: MessageSendPhase,
        progress: Int? = null,
    ) {
        dao.updateStatus(
            localMessageId = localMessageId,
            status = STATUS_SENDING,
            sendPhase = phase.name,
            sendProgress = progress?.coerceIn(0, 100),
            errorMessage = null,
        )
    }

    suspend fun updateSendProgress(
        localMessageId: Int,
        phase: MessageSendPhase,
        progress: Int?,
    ) {
        val current = dao.getByLocalMessageId(localMessageId) ?: return
        dao.updateStatus(
            localMessageId = localMessageId,
            status = current.status,
            sendPhase = phase.name,
            sendProgress = progress?.coerceIn(0, 100),
            errorMessage = current.errorMessage,
        )
    }

    suspend fun markFailed(
        localMessageId: Int,
        errorMessage: String?,
    ) {
        dao.updateStatus(
            localMessageId = localMessageId,
            status = STATUS_FAILED,
            sendPhase = dao.getByLocalMessageId(localMessageId)?.sendPhase,
            sendProgress = dao.getByLocalMessageId(localMessageId)?.sendProgress,
            errorMessage = errorMessage,
        )
    }

    suspend fun requeueFailedMessage(
        localMessageId: Int,
    ) {
        dao.updateStatus(
            localMessageId = localMessageId,
            status = STATUS_QUEUED,
            sendPhase = dao.getByLocalMessageId(localMessageId)?.sendPhase,
            sendProgress = dao.getByLocalMessageId(localMessageId)?.sendProgress,
            errorMessage = null,
        )
    }

    suspend fun requeueSendingMessages(
        conversationId: Int,
    ) {
        dao.requeueSendingForConversation(conversationId)
    }

    suspend fun updatePreparedAttachments(
        localMessageId: Int,
        attachmentDescriptors: List<EncryptedAttachmentDescriptor>,
    ) {
        val attachmentIds = attachmentDescriptors.map { it.attachmentId }.distinct()
        val previews = attachmentDescriptors.map(::descriptorToPreviewAttachment)
        dao.updatePreparedAttachments(
            localMessageId = localMessageId,
            attachmentIdsCsv = attachmentIds.joinToString(","),
            attachmentDescriptorsJson = encodeAttachmentDescriptors(attachmentDescriptors),
            attachmentPreviewJson = encodeAttachmentsJson(
                json = json,
                attachments = previews,
            ),
            hasAttachments = attachmentIds.isNotEmpty(),
        )
    }

    suspend fun requeueAllSendingMessages() {
        dao.requeueAllSending()
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
            replyToMessageId = replyToMessageId,
            replyPreview = decodeMessagePreviewJson(replyPreviewJson),
            localAttachmentUris = decodeLocalAttachmentUris(localAttachmentUrisJson),
            attachmentIds = attachmentIdsCsv
                .split(",")
                .mapNotNull { it.trim().takeIf(String::isNotBlank)?.toIntOrNull() },
            attachmentDescriptors = decodeAttachmentDescriptors(attachmentDescriptorsJson),
            createdAt = createdAt,
            status = status.toMessageSendStatus(),
            sendPhase = sendPhase?.toMessageSendPhase(),
            sendProgress = sendProgress,
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
            replyToMessageId = replyToMessageId,
            replyPreview = decodeMessagePreviewJson(replyPreviewJson),
            attachmentIds = attachmentIds,
            attachments = attachments,
            sendStatus = status.toMessageSendStatus(),
            sendPhase = sendPhase?.toMessageSendPhase(),
            sendProgress = sendProgress,
            errorMessage = errorMessage,
        )
    }

    private fun buildLocalMessageId(): Int {
        val value = UUID.randomUUID().hashCode().absoluteValue.coerceAtLeast(1)
        return -value
    }

    private fun encodeLocalAttachmentUris(
        uris: List<String>,
    ): String {
        return json.encodeToString(
            ListSerializer(String.serializer()),
            uris.distinct(),
        )
    }

    private fun decodeLocalAttachmentUris(
        raw: String,
    ): List<String> {
        return runCatching {
            json.decodeFromString(
                ListSerializer(String.serializer()),
                raw,
            )
        }.getOrDefault(emptyList())
    }

    private fun String.toMessageSendStatus(): MessageSendStatus {
        return when (this) {
            STATUS_FAILED -> MessageSendStatus.FAILED
            STATUS_SENDING,
            STATUS_QUEUED -> MessageSendStatus.SENDING
            else -> MessageSendStatus.SENDING
        }
    }

    private fun String.toMessageSendPhase(): MessageSendPhase {
        return when (this) {
            MessageSendPhase.UPLOADING.name -> MessageSendPhase.UPLOADING
            else -> MessageSendPhase.SENDING
        }
    }

    private companion object {
        const val STATUS_QUEUED = "queued"
        const val STATUS_SENDING = "sending"
        const val STATUS_FAILED = "failed"
    }
}
