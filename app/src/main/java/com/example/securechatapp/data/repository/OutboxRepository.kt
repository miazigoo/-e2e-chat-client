package com.example.securechatapp.data.repository

import com.example.securechatapp.core.crypto.AttachmentCryptoEngine
import com.example.securechatapp.core.crypto.EncryptedAttachmentDescriptor
import com.example.securechatapp.crypto.engine.CryptoEngine
import com.example.securechatapp.crypto.engine.nowIso
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
    val attemptCount: Int = 0,
    val nextAttemptAtEpochMillis: Long = 0L,
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
            attemptCount = 0,
            lastAttemptAtEpochMillis = null,
            nextAttemptAtEpochMillis = 0L,
        )

        dao.upsert(entity)
        return localMessageId
    }

    suspend fun getPendingMessage(
        localMessageId: Int,
    ): PendingOutgoingMessage? {
        return dao.getByLocalMessageId(localMessageId)?.toPending()
    }

    suspend fun listDueMessages(
        limit: Int = DEFAULT_DRAIN_BATCH_SIZE,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): List<PendingOutgoingMessage> {
        return dao.listDueByStatus(
            status = STATUS_QUEUED,
            nowEpochMillis = nowEpochMillis,
            limit = limit,
        ).map { it.toPending() }
    }

    suspend fun listDueMessages(
        conversationId: Int,
        limit: Int = DEFAULT_DRAIN_BATCH_SIZE,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): List<PendingOutgoingMessage> {
        return dao.listDueByConversationAndStatus(
            conversationId = conversationId,
            status = STATUS_QUEUED,
            nowEpochMillis = nowEpochMillis,
            limit = limit,
        ).map { it.toPending() }
    }

    suspend fun getNextAttemptDelayMillis(
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Long? {
        val nextAttemptAt = dao.getNextAttemptAt(STATUS_QUEUED) ?: return null
        return (nextAttemptAt - nowEpochMillis).coerceAtLeast(0L)
    }

    suspend fun markSending(
        localMessageId: Int,
    ) {
        dao.updateStatus(
            localMessageId = localMessageId,
            status = STATUS_SENDING,
            errorMessage = null,
            lastAttemptAtEpochMillis = System.currentTimeMillis(),
        )
    }

    suspend fun scheduleRetryOrMarkFailed(
        pending: PendingOutgoingMessage,
        errorMessage: String?,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val nextAttemptCount = pending.attemptCount + 1
        val exhausted = nextAttemptCount >= MAX_ATTEMPTS
        val nextDelayMillis = if (exhausted) {
            0L
        } else {
            calculateBackoffDelayMillis(nextAttemptCount)
        }

        dao.updateRetryState(
            localMessageId = pending.localMessageId,
            status = if (exhausted) STATUS_FAILED else STATUS_QUEUED,
            errorMessage = errorMessage,
            attemptCount = nextAttemptCount,
            lastAttemptAtEpochMillis = nowEpochMillis,
            nextAttemptAtEpochMillis = if (exhausted) nowEpochMillis else nowEpochMillis + nextDelayMillis,
        )

        return !exhausted
    }

    suspend fun requeueFailedMessage(
        localMessageId: Int,
    ) {
        dao.resetForManualRetry(localMessageId)
    }

    suspend fun requeueSendingMessages(
        conversationId: Int,
    ) {
        dao.requeueSendingForConversation(conversationId)
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
            attachmentIds = attachmentIdsCsv
                .split(",")
                .mapNotNull { it.trim().takeIf(String::isNotBlank)?.toIntOrNull() },
            attachmentDescriptors = decodeAttachmentDescriptors(attachmentDescriptorsJson),
            createdAt = createdAt,
            status = status.toMessageSendStatus(),
            errorMessage = errorMessage,
            attemptCount = attemptCount,
            nextAttemptAtEpochMillis = nextAttemptAtEpochMillis,
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


    private fun calculateBackoffDelayMillis(
        attemptCount: Int,
    ): Long {
        val exponent = (attemptCount - 1).coerceIn(0, MAX_BACKOFF_EXPONENT)
        val multiplier = 1L shl exponent
        return (INITIAL_BACKOFF_MILLIS * multiplier).coerceAtMost(MAX_BACKOFF_MILLIS)
    }

    private companion object {
        const val STATUS_QUEUED = "queued"
        const val STATUS_SENDING = "sending"
        const val STATUS_FAILED = "failed"
        const val DEFAULT_DRAIN_BATCH_SIZE = 25
        const val MAX_ATTEMPTS = 10
        const val MAX_BACKOFF_EXPONENT = 9
        const val INITIAL_BACKOFF_MILLIS = 5_000L
        const val MAX_BACKOFF_MILLIS = 60 * 60 * 1000L
    }
}
