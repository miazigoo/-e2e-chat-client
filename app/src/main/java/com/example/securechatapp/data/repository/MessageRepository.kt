package com.example.securechatapp.data.repository

import com.example.securechatapp.core.crypto.AttachmentCryptoEngine
import com.example.securechatapp.core.crypto.DevCryptoEngine
import com.example.securechatapp.core.crypto.EncryptedAttachmentDescriptor
import com.example.securechatapp.core.crypto.SecureMessagePayloadV1
import com.example.securechatapp.data.remote.api.ChatBackendApi
import com.example.securechatapp.data.remote.dto.DeleteMessagesRequestDto
import com.example.securechatapp.data.remote.dto.DeleteMessagesResponseDto
import com.example.securechatapp.data.remote.dto.MarkDeliveredRequestDto
import com.example.securechatapp.data.remote.dto.MarkReadRequestDto
import com.example.securechatapp.data.remote.dto.SendMessageRequestDto
import com.example.securechatapp.domain.model.AttachmentItem
import com.example.securechatapp.domain.model.ChatMessage
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class MessageRepository @Inject constructor(
    private val api: ChatBackendApi,
    private val crypto: DevCryptoEngine,
    private val attachmentCryptoEngine: AttachmentCryptoEngine,
    json: Json,
) : BaseApiRepository(json) {

    private val jsonParser = json

    suspend fun listMessages(
        conversationId: Int,
        peerUserId: Int,
    ): List<ChatMessage> {
        return safe { api.listMessages(conversationId = conversationId).data }
            .items
            .sortedBy { it.messageId }
            .map { dto ->
                val decoded = decodeEncryptedMessagePayload(dto.ciphertext)

                ChatMessage(
                    messageId = dto.messageId,
                    text = decoded.text,
                    isMine = dto.senderUserId != peerUserId,
                    createdAt = dto.serverReceivedAt,
                    deliveredAt = dto.deliveredAt,
                    readAt = dto.readAt,
                    hasAttachments = dto.hasAttachments || decoded.attachments.isNotEmpty(),
                    attachmentIds = decoded.attachments.map { it.attachmentId }.distinct(),
                    attachments = decoded.attachments,
                )
            }
    }

    suspend fun markIncomingMessagesAsDelivered(
        messages: List<ChatMessage>,
    ): Int {
        val undeliveredIncoming = messages
            .filter { !it.isMine && it.deliveredAt == null }
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
            .filter { !it.isMine && it.readAt == null }
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
        attachmentIds: List<Int> = emptyList(),
        attachmentDescriptors: List<EncryptedAttachmentDescriptor> = emptyList(),
    ) {
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

        val payloadJson = buildEncryptedMessagePayload(
            plainText = plainText,
            attachmentDescriptors = normalizedDescriptors,
        )

        val encrypted = crypto.encryptPlainText(payloadJson)

        val messageType = if (distinctAttachmentIds.isNotEmpty() && plainText.isBlank()) {
            "file"
        } else {
            "text"
        }

        safe {
            api.sendMessage(
                SendMessageRequestDto(
                    conversationId = conversationId,
                    recipientUserId = recipientUserId,
                    messageUuid = UUID.randomUUID().toString(),
                    messageType = messageType,
                    ciphertext = encrypted.ciphertext,
                    nonce = encrypted.nonce,
                    clientCreatedAt = crypto.nowIso(),
                    attachmentIds = distinctAttachmentIds,
                )
            )
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

    private data class DecodedMessagePayload(
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
        ciphertext: String,
    ): DecodedMessagePayload {
        val decrypted = crypto.decryptToPlainText(ciphertext)

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

        if (ciphertextBase64.isNullOrBlank() || keyBase64.isNullOrBlank() || nonceBase64.isNullOrBlank()) {
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
