package com.example.securechatapp.data.repository

import com.example.securechatapp.core.crypto.AttachmentCryptoEngine
import com.example.securechatapp.core.crypto.EncryptedAttachmentDescriptor
import com.example.securechatapp.core.crypto.SecureMessagePayloadV1
import com.example.securechatapp.crypto.engine.CryptoEngine
import com.example.securechatapp.crypto.sharedsecret.ConversationSharedSecretMissingException
import com.example.securechatapp.crypto.sharedsecret.ConversationSharedSecretCrypto
import com.example.securechatapp.crypto.engine.decryptToPlainText
import com.example.securechatapp.crypto.engine.encryptPlainText
import com.example.securechatapp.crypto.engine.nowIso
import com.example.securechatapp.data.remote.api.ChatBackendApi
import com.example.securechatapp.data.remote.dto.DeleteMessagesRequestDto
import com.example.securechatapp.data.remote.dto.DeleteMessagesResponseDto
import com.example.securechatapp.data.remote.dto.ForwardMessagesRequestDto
import com.example.securechatapp.data.remote.dto.ForwardMessagesResponseDto
import com.example.securechatapp.data.remote.dto.MarkDeliveredRequestDto
import com.example.securechatapp.data.remote.dto.MarkReadRequestDto
import com.example.securechatapp.data.remote.dto.SendMessageRequestDto
import com.example.securechatapp.data.remote.dto.SendMessageResponseDto
import com.example.securechatapp.data.remote.dto.SetMessageReactionRequestDto
import com.example.securechatapp.domain.model.AttachmentItem
import com.example.securechatapp.domain.model.ChatMessage
import com.example.securechatapp.domain.model.MessagePreview
import com.example.securechatapp.domain.model.MessageReactionSummary
import com.example.securechatapp.domain.model.MessageSendStatus
import com.example.securechatapp.domain.model.SharedMessagesPage
import com.example.securechatapp.domain.model.SharedTabCounts
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class MessageRepository @Inject constructor(
    private val api: ChatBackendApi,
    private val crypto: CryptoEngine,
    private val attachmentCryptoEngine: AttachmentCryptoEngine,
    private val sharedSecretCrypto: ConversationSharedSecretCrypto,
    json: Json,
) : BaseApiRepository(json) {

    private val jsonParser = json

    data class MessageWindowPage(
        val messages: List<ChatMessage>,
        val anchorMessageId: Int? = null,
        val beforeId: Int? = null,
        val beforeCursor: String? = null,
        val afterId: Int? = null,
        val afterCursor: String? = null,
    )

    suspend fun listMessages(
        conversationId: Int,
        peerUserId: Int,
    ): List<ChatMessage> {
        return listMessageWindow(
            conversationId = conversationId,
            peerUserId = peerUserId,
        ).messages
    }

    suspend fun listMessageWindow(
        conversationId: Int,
        peerUserId: Int,
        anchorId: Int? = null,
        beforeId: Int? = null,
        beforeCursor: String? = null,
        afterId: Int? = null,
        afterCursor: String? = null,
        limit: Int = 100,
    ): MessageWindowPage {
        val conversation = safe { api.getConversation(conversationId).data }
        val conversationUuid = conversation.conversationUuid
        val forceMine = conversation.isSavedMessages

        val response = safe {
            api.listMessages(
                conversationId = conversationId,
                anchorId = anchorId,
                beforeId = beforeId,
                beforeCursor = beforeCursor,
                afterId = afterId,
                afterCursor = afterCursor,
                limit = limit,
            ).data
        }

        return MessageWindowPage(
            messages = response.items
                .sortedBy { it.messageId }
                .map { dto ->
                    dto.toDomainMessage(
                        conversationUuid = conversationUuid,
                        peerUserId = peerUserId,
                        forceMine = forceMine,
                        decoder = ::decodeEncryptedMessagePayload,
                    )
                },
            anchorMessageId = response.anchorMessageId,
            beforeId = response.beforeId,
            beforeCursor = response.beforeCursor,
            afterId = response.afterId,
            afterCursor = response.afterCursor,
        )
    }

    suspend fun searchMessages(
        conversationId: Int,
        peerUserId: Int,
        query: String,
    ): List<ChatMessage> {
        val conversation = safe { api.getConversation(conversationId).data }
        val conversationUuid = conversation.conversationUuid
        val forceMine = conversation.isSavedMessages
        return safe {
            api.searchMessages(
                conversationId = conversationId,
                query = query.trim(),
            ).data
        }.items.map { dto ->
            dto.toDomainMessage(
                conversationUuid = conversationUuid,
                peerUserId = peerUserId,
                forceMine = forceMine,
                decoder = ::decodeEncryptedMessagePayload,
            )
        }
    }

    suspend fun listSharedMessages(
        conversationId: Int,
        peerUserId: Int,
        tab: String,
    ): SharedMessagesPage {
        val conversation = safe { api.getConversation(conversationId).data }
        val conversationUuid = conversation.conversationUuid
        val forceMine = conversation.isSavedMessages
        val data = safe {
            api.listSharedMessages(
                conversationId = conversationId,
                tab = tab,
            ).data
        }
        return SharedMessagesPage(
            conversationId = data.conversationId,
            tab = data.tab,
            counts = SharedTabCounts(
                media = data.counts.media,
                links = data.counts.links,
                files = data.counts.files,
            ),
            items = data.items.map { dto ->
                dto.toDomainMessage(
                    conversationUuid = conversationUuid,
                    peerUserId = peerUserId,
                    forceMine = forceMine,
                    decoder = ::decodeEncryptedMessagePayload,
                )
            },
        )
    }

    suspend fun markIncomingMessagesAsDelivered(
        messages: List<ChatMessage>,
    ): Int {
        val undeliveredIncoming = messages
            .filter {
                !it.isMine &&
                        it.deliveredAt == null &&
                        it.sendStatus == MessageSendStatus.SENT &&
                        it.messageId > 0
            }
            .distinctBy { it.messageId }

        undeliveredIncoming.forEach { message ->
            safe {
                api.markDelivered(
                    messageId = message.messageId,
                    body = MarkDeliveredRequestDto(deliveredAt = crypto.nowIso()),
                )
            }
        }

        return undeliveredIncoming.size
    }

    suspend fun markIncomingMessagesAsRead(
        messages: List<ChatMessage>,
    ): Int {
        val unreadIncoming = messages
            .filter {
                !it.isMine &&
                        it.readAt == null &&
                        it.sendStatus == MessageSendStatus.SENT &&
                        it.messageId > 0
            }
            .distinctBy { it.messageId }

        unreadIncoming.forEach { message ->
            safe {
                api.markRead(
                    messageId = message.messageId,
                    body = MarkReadRequestDto(readAt = crypto.nowIso()),
                )
            }
        }

        return unreadIncoming.size
    }

    suspend fun sendMessage(
        conversationId: Int,
        recipientUserId: Int,
        plainText: String,
        replyToMessageId: Int? = null,
        attachmentIds: List<Int> = emptyList(),
        attachmentDescriptors: List<EncryptedAttachmentDescriptor> = emptyList(),
        messageUuid: String = UUID.randomUUID().toString(),
    ): SendMessageResponseDto {
        val normalizedDescriptors = if (attachmentDescriptors.isNotEmpty()) {
            attachmentDescriptors
                .filter { it.attachmentId > 0 }
                .distinctBy { it.attachmentId }
        } else {
            attachmentIds
                .distinct()
                .map { attachmentId ->
                    EncryptedAttachmentDescriptor(
                        attachmentId = attachmentId,
                    )
                }
        }

        val distinctAttachmentIds = normalizedDescriptors
            .map { it.attachmentId }
            .distinct()

        if (plainText.isBlank() && distinctAttachmentIds.isEmpty()) {
            error("Нельзя отправить пустое сообщение")
        }

        val conversation = safe { api.getConversation(conversationId).data }
        val conversationUuid = conversation.conversationUuid

        if (conversation.isPurged) {
            error("Чат был удалён на сервере")
        }

        if (!conversation.isActive) {
            error("Чат недоступен для отправки сообщений")
        }

        val payloadJson = buildEncryptedMessagePayload(
            plainText = plainText,
            attachmentDescriptors = normalizedDescriptors,
        )

        val sharedSecretPayload = sharedSecretCrypto.encryptIfEnabled(
            conversationUuid = conversationUuid,
            plainText = payloadJson,
        )
        if (conversation.sharedSecretEnabled && sharedSecretPayload == null) {
            error("Для этого чата нужно включить дополнительное шифрование на этом устройстве")
        }

        val legacyEncrypted = if (sharedSecretPayload == null) {
            crypto.encryptPlainText(payloadJson)
        } else {
            null
        }
        val ciphertext = sharedSecretPayload?.ciphertext ?: legacyEncrypted?.ciphertext.orEmpty()
        val nonce = sharedSecretPayload?.nonce ?: legacyEncrypted?.nonce.orEmpty()

        val messageType = if (distinctAttachmentIds.isNotEmpty() && plainText.isBlank()) {
            "file"
        } else {
            "text"
        }

        return safe {
            api.sendMessage(
                SendMessageRequestDto(
                    conversationId = conversationId,
                    recipientUserId = recipientUserId,
                    messageUuid = messageUuid,
                    messageType = messageType,
                    ciphertext = ciphertext,
                    nonce = nonce,
                    encryptionMode = if (sharedSecretPayload != null) "signal_plus_shared_secret" else "signal",
                    aadHash = sharedSecretPayload?.fingerprint,
                    clientCreatedAt = crypto.nowIso(),
                    replyToMessageId = replyToMessageId,
                    attachmentIds = distinctAttachmentIds,
                )
            ).data
        }
    }

    suspend fun forwardMessages(
        conversationId: Int,
        recipientUserId: Int,
        messageIds: List<Int>,
    ): ForwardMessagesResponseDto {
        return safe {
            api.forwardMessages(
                ForwardMessagesRequestDto(
                    conversationId = conversationId,
                    recipientUserId = recipientUserId,
                    messageIds = messageIds.distinct(),
                    clientCreatedAt = crypto.nowIso(),
                )
            ).data
        }
    }



    suspend fun setMessageReaction(
        messageId: Int,
        reaction: String,
    ) {
        safe {
            api.setMessageReaction(
                messageId = messageId,
                body = SetMessageReactionRequestDto(reaction = reaction),
            )
        }
    }

    suspend fun deleteMessageReaction(
        messageId: Int,
    ) {
        safe {
            api.deleteMessageReaction(messageId = messageId)
        }
    }

    suspend fun deleteMessagesLocal(
        conversationId: Int,
        messageIds: List<Int>,
    ): DeleteMessagesResponseDto {
        return safe {
            api.deleteLocalMessages(
                DeleteMessagesRequestDto(
                    conversationId = conversationId,
                    messageIds = messageIds.distinct(),
                )
            ).data
        }
    }

    suspend fun deleteMessagesGlobal(
        conversationId: Int,
        messageIds: List<Int>,
    ): DeleteMessagesResponseDto {
        return safe {
            api.deleteGlobalMessages(
                DeleteMessagesRequestDto(
                    conversationId = conversationId,
                    messageIds = messageIds.distinct(),
                )
            ).data
        }
    }

    internal data class DecodedMessagePayload(
        val text: String,
        val attachments: List<AttachmentItem>,
    )

    private fun buildEncryptedMessagePayload(
        plainText: String,
        attachmentDescriptors: List<EncryptedAttachmentDescriptor>,
    ): String {
        val payload = SecureMessagePayloadV1(
            text = plainText.takeIf { it.isNotBlank() },
            attachments = attachmentDescriptors.distinctBy { it.attachmentId },
        )

        return jsonParser.encodeToString(
            SecureMessagePayloadV1.serializer(),
            payload,
        )
    }

    private fun decodeEncryptedMessagePayload(
        conversationUuid: String,
        ciphertext: String,
        encryptionMode: String,
    ): DecodedMessagePayload {
        val decrypted = try {
            if (encryptionMode == "signal_plus_shared_secret" || ciphertext.startsWith("ss1:")) {
                sharedSecretCrypto.decryptIfNeeded(
                    conversationUuid = conversationUuid,
                    ciphertext = ciphertext,
                )
            } else {
                crypto.decryptToPlainText(ciphertext)
            }
        } catch (e: ConversationSharedSecretMissingException) {
            return DecodedMessagePayload(
                text = "🔐 ${e.message}",
                attachments = emptyList(),
            )
        }

        val payload = runCatching {
            jsonParser.decodeFromString(
                SecureMessagePayloadV1.serializer(),
                decrypted,
            )
        }.getOrNull()

        if (payload == null || payload.schema != SecureMessagePayloadV1.SCHEMA) {
            return DecodedMessagePayload(
                text = decrypted,
                attachments = emptyList(),
            )
        }

        val attachments = payload.attachments
            .map { descriptor -> descriptorToAttachmentUi(descriptor) }
            .distinctBy { it.attachmentId }

        val text = payload.text
            ?.takeIf { it.isNotBlank() }
            ?: if (attachments.isNotEmpty()) "[attachment]" else ""

        return DecodedMessagePayload(
            text = text,
            attachments = attachments,
        )
    }

    private fun descriptorToAttachmentUi(
        descriptor: EncryptedAttachmentDescriptor,
    ): AttachmentItem {
        val decryptedFileName = decryptAttachmentFileName(descriptor)
        val fallbackName = fallbackAttachmentName(
            attachmentId = descriptor.attachmentId,
            mimeType = descriptor.mimeType,
        )

        return AttachmentItem(
            attachmentId = descriptor.attachmentId,
            fileName = decryptedFileName?.takeIf { it.isNotBlank() } ?: fallbackName,
            mimeType = descriptor.mimeType,
            fileSize = descriptor.fileSize,
            canDownload = true,
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
}

private fun com.example.securechatapp.data.remote.dto.MessageItemDto.toDomainMessage(
    conversationUuid: String,
    peerUserId: Int,
    forceMine: Boolean,
    decoder: (String, String, String) -> MessageRepository.DecodedMessagePayload,
): ChatMessage {
    val decoded = decoder(conversationUuid, ciphertext, encryptionMode)
    return ChatMessage(
        messageId = messageId,
        messageUuid = messageUuid,
        text = decoded.text,
        isMine = forceMine || senderUserId != peerUserId,
        createdAt = serverReceivedAt,
        clientCreatedAt = clientCreatedAt,
        deliveredAt = deliveredAt,
        readAt = readAt,
        expiresAt = expiresAt,
        messageType = messageType,
        hasAttachments = hasAttachments || decoded.attachments.isNotEmpty(),
        attachmentIds = decoded.attachments.map { it.attachmentId }.distinct(),
        attachments = decoded.attachments,
        sendStatus = MessageSendStatus.SENT,
        errorMessage = null,
        reactions = reactions.map { reaction ->
            MessageReactionSummary(
                reaction = reaction.reaction,
                count = reaction.count,
                me = reaction.me,
            )
        },
        replyToMessageId = replyToMessageId,
        forwardFromMessageId = forwardFromMessageId,
        replyPreview = replyPreview?.toDomainPreview(conversationUuid, decoder),
        forwardPreview = forwardPreview?.toDomainPreview(conversationUuid, decoder),
    )
}

private fun com.example.securechatapp.data.remote.dto.MessagePreviewDto.toDomainPreview(
    conversationUuid: String,
    decoder: (String, String, String) -> MessageRepository.DecodedMessagePayload,
): MessagePreview {
    val decoded = decoder(
        conversationUuid,
        ciphertext,
        if (ciphertext.startsWith("ss1:")) "signal_plus_shared_secret" else "signal",
    )
    return MessagePreview(
        messageId = messageId,
        messageUuid = messageUuid,
        senderUserId = senderUserId,
        messageType = messageType,
        text = decoded.text,
        hasAttachments = hasAttachments,
        clientCreatedAt = clientCreatedAt,
    )
}
