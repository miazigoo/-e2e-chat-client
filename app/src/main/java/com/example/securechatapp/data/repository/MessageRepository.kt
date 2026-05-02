package com.example.securechatapp.data.repository

import android.util.Log
import com.example.securechatapp.core.crypto.AttachmentCryptoEngine
import com.example.securechatapp.core.crypto.EncryptedAttachmentDescriptor
import com.example.securechatapp.core.crypto.SecureMessagePayloadV1
import com.example.securechatapp.crypto.engine.CryptoEngine
import com.example.securechatapp.crypto.sharedsecret.ConversationSharedSecretMissingException
import com.example.securechatapp.crypto.sharedsecret.ConversationSharedSecretCrypto
import com.example.securechatapp.crypto.signal.PersistentSignalProtocolStore
import com.example.securechatapp.crypto.signal.SignalCiphertextType
import com.example.securechatapp.crypto.signal.SignalMessageCryptoEngine
import com.example.securechatapp.crypto.signal.SignalRemoteAddress
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
import com.example.securechatapp.data.remote.dto.MessageDevicePayloadRequestDto
import com.example.securechatapp.data.remote.dto.SendMessageRequestDto
import com.example.securechatapp.data.remote.dto.SendMessageResponseDto
import com.example.securechatapp.data.remote.dto.SetMessageReactionRequestDto
import com.example.securechatapp.domain.repository.SignalPreKeyRepository
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

@Singleton
class MessageRepository @Inject constructor(
    private val api: ChatBackendApi,
    private val crypto: CryptoEngine,
    private val attachmentCryptoEngine: AttachmentCryptoEngine,
    private val sharedSecretCrypto: ConversationSharedSecretCrypto,
    private val signalPreKeyRepository: SignalPreKeyRepository,
    private val signalMessageCryptoEngine: SignalMessageCryptoEngine,
    private val signalStore: PersistentSignalProtocolStore,
    private val decryptedMessagePayloadCacheRepository: DecryptedMessagePayloadCacheRepository,
    json: Json,
) : BaseApiRepository(json) {

    private val jsonParser = json
    private val logTag = "MessageRepository"

    data class MessageWindowPage(
        val messages: List<ChatMessage>,
        val anchorMessageId: Int? = null,
        val beforeId: Int? = null,
        val beforeCursor: String? = null,
        val afterId: Int? = null,
        val afterCursor: String? = null,
    )

    data class DeliveryStatusUpdate(
        val messageId: Int,
        val timestamp: String,
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
        val decodeContext = buildDecodeContext(response.items)

        return MessageWindowPage(
            messages = response.items
                .sortedBy { it.messageId }
                .map { dto ->
                    dto.toDomainMessage(
                        conversationUuid = conversationUuid,
                        peerUserId = peerUserId,
                        forceMine = forceMine,
                        decoder = { envelope ->
                            decodeEncryptedMessagePayload(
                                envelope = envelope,
                                decodeContext = decodeContext,
                            )
                        },
                    )
                }.also {
                    persistDecodedPayloads(decodeContext)
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
        }.let { response ->
            val decodeContext = buildDecodeContext(response.items)
            response.items.map { dto ->
                dto.toDomainMessage(
                    conversationUuid = conversationUuid,
                    peerUserId = peerUserId,
                    forceMine = forceMine,
                    decoder = { envelope ->
                        decodeEncryptedMessagePayload(
                            envelope = envelope,
                            decodeContext = decodeContext,
                        )
                    },
                )
            }.also {
                persistDecodedPayloads(decodeContext)
            }
        }
    }

    suspend fun listSharedMessages(
        conversationId: Int,
        peerUserId: Int,
        tab: String,
        tagId: Int? = null,
        beforeMessageId: Int? = null,
    ): SharedMessagesPage {
        val conversation = safe { api.getConversation(conversationId).data }
        val conversationUuid = conversation.conversationUuid
        val forceMine = conversation.isSavedMessages
        val data = safe {
            api.listSharedMessages(
                conversationId = conversationId,
                tab = tab,
                tagId = tagId,
                beforeMessageId = beforeMessageId,
            ).data
        }
        val decodeContext = buildDecodeContext(data.items)
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
                    decoder = { envelope ->
                        decodeEncryptedMessagePayload(
                            envelope = envelope,
                            decodeContext = decodeContext,
                        )
                    },
                )
            }.also {
                persistDecodedPayloads(decodeContext)
            },
        )
    }

    suspend fun markIncomingMessagesAsDelivered(
        messages: List<ChatMessage>,
    ): List<DeliveryStatusUpdate> {
        val undeliveredIncoming = messages
            .filter {
                !it.isMine &&
                        it.deliveredAt == null &&
                        it.sendStatus == MessageSendStatus.SENT &&
                        it.messageId > 0
            }
            .distinctBy { it.messageId }

        val updates = mutableListOf<DeliveryStatusUpdate>()
        undeliveredIncoming.forEach { message ->
            val deliveredAt = crypto.nowIso()
            safe {
                api.markDelivered(
                    messageId = message.messageId,
                    body = MarkDeliveredRequestDto(deliveredAt = deliveredAt),
                )
            }
            updates += DeliveryStatusUpdate(
                messageId = message.messageId,
                timestamp = deliveredAt,
            )
        }

        return updates
    }

    suspend fun markIncomingMessagesAsRead(
        messages: List<ChatMessage>,
    ): List<DeliveryStatusUpdate> {
        val unreadIncoming = messages
            .filter {
                !it.isMine &&
                        it.readAt == null &&
                        it.sendStatus == MessageSendStatus.SENT &&
                        it.messageId > 0
            }
            .distinctBy { it.messageId }

        val updates = mutableListOf<DeliveryStatusUpdate>()
        unreadIncoming.forEach { message ->
            val readAt = crypto.nowIso()
            safe {
                api.markRead(
                    messageId = message.messageId,
                    body = MarkReadRequestDto(readAt = readAt),
                )
            }
            updates += DeliveryStatusUpdate(
                messageId = message.messageId,
                timestamp = readAt,
            )
        }

        return updates
    }

    suspend fun sendMessage(
        conversationId: Int,
        recipientUserId: Int,
        plainText: String,
        replyToMessageId: Int? = null,
        attachmentIds: List<Int> = emptyList(),
        attachmentTagIds: List<Int> = emptyList(),
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

        val transportPlainText = sharedSecretPayload?.ciphertext ?: payloadJson
        val legacyEncrypted = if (sharedSecretPayload == null) {
            crypto.encryptPlainText(payloadJson)
        } else {
            null
        }
        val ciphertext = sharedSecretPayload?.ciphertext ?: legacyEncrypted?.ciphertext.orEmpty()
        val nonce = sharedSecretPayload?.nonce ?: legacyEncrypted?.nonce.orEmpty()
        val devicePayloads = if (conversation.isSavedMessages) {
            emptyList()
        } else {
            buildRecipientDevicePayloads(
                recipientUserId = recipientUserId,
                transportPlainText = transportPlainText,
            )
        }

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
                    attachmentTagIds = if (distinctAttachmentIds.isEmpty()) {
                        emptyList()
                    } else {
                        attachmentTagIds.distinct()
                    },
                    devicePayloads = devicePayloads,
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

    internal data class MessageCipherEnvelope(
        val messageId: Int? = null,
        val messageUuid: String? = null,
        val conversationUuid: String,
        val senderUserId: Int,
        val senderDeviceId: Int? = null,
        val ciphertext: String,
        val ciphertextVersion: Int? = null,
        val encryptionMode: String,
    )

    private data class DecodeContext(
        val cachedPayloadsByMessageId: Map<Int, DecryptedMessagePayloadCacheRepository.CachedDecryptedMessagePayload>,
        val refreshedPayloadsByMessageId: MutableMap<Int, DecryptedMessagePayloadCacheRepository.CachedDecryptedMessagePayload> = linkedMapOf(),
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

    private suspend fun buildRecipientDevicePayloads(
        recipientUserId: Int,
        transportPlainText: String,
    ): List<MessageDevicePayloadRequestDto> {
        return when (val bundlesResult = signalPreKeyRepository.getBundlesForUser(recipientUserId)) {
            is com.example.securechatapp.core.result.AppResult.Success -> {
                bundlesResult.data
                    .distinctBy { it.deviceId }
                    .map { bundle ->
                        val encrypted = signalMessageCryptoEngine.encrypt(
                            remoteAddress = SignalRemoteAddress(
                                userId = recipientUserId,
                                deviceId = bundle.deviceId,
                            ),
                            remoteBundle = bundle,
                            plainText = transportPlainText,
                        )
                        MessageDevicePayloadRequestDto(
                            deviceId = bundle.deviceId,
                            ciphertext = encrypted.ciphertext,
                            ciphertextVersion = encrypted.type.wireValue,
                            nonce = SIGNAL_DEVICE_PAYLOAD_NONCE,
                            aadHash = null,
                        )
                    }
            }

            is com.example.securechatapp.core.result.AppResult.Error -> {
                error(
                    bundlesResult.message.ifBlank {
                        "Не удалось получить ключи устройств собеседника"
                    }
                )
            }
        }
    }

    private suspend fun decodeEncryptedMessagePayload(
        envelope: MessageCipherEnvelope,
        decodeContext: DecodeContext,
    ): DecodedMessagePayload {
        val cachedPayload = envelope.messageId
            ?.let(decodeContext.cachedPayloadsByMessageId::get)
        val decrypted = try {
            decryptTransportPayload(envelope)
        } catch (e: ConversationSharedSecretMissingException) {
            cachedPayload?.let { return it.toDecodedPayload() }
            return DecodedMessagePayload(
                text = "🔐 ${e.message}",
                attachments = emptyList(),
            )
        } catch (e: Exception) {
            cachedPayload?.let { return it.toDecodedPayload() }
            Log.w(
                logTag,
                "Failed to decrypt message payload for messageId=${envelope.messageId}, " +
                        "senderUserId=${envelope.senderUserId}, senderDeviceId=${envelope.senderDeviceId}, " +
                        "ciphertextVersion=${envelope.ciphertextVersion}, encryptionMode=${envelope.encryptionMode}; showing placeholder instead",
                e,
            )
            return DecodedMessagePayload(
                text = UNDECRYPTABLE_MESSAGE_PLACEHOLDER,
                attachments = emptyList(),
            )
        }

        val payload = runCatching {
            jsonParser.decodeFromString(
                SecureMessagePayloadV1.serializer(),
                decrypted,
            )
        }.getOrNull()

        val decodedPayload = if (payload == null || payload.schema != SecureMessagePayloadV1.SCHEMA) {
            DecodedMessagePayload(
                text = decrypted,
                attachments = emptyList(),
            )
        } else {
            val attachments = payload.attachments
                .map { descriptor -> descriptorToAttachmentUi(descriptor) }
                .distinctBy { it.attachmentId }

            val text = payload.text
                ?.takeIf { it.isNotBlank() }
                ?: if (attachments.isNotEmpty()) "[attachment]" else ""

            DecodedMessagePayload(
                text = text,
                attachments = attachments,
            )
        }

        cacheDecodedPayload(
            envelope = envelope,
            payload = decodedPayload,
            decodeContext = decodeContext,
        )
        return decodedPayload
    }

    private fun decryptTransportPayload(
        envelope: MessageCipherEnvelope,
    ): String {
        val transportPlainText = when (envelope.ciphertextVersion) {
            SignalCiphertextType.SIGNAL.wireValue,
            SignalCiphertextType.PREKEY.wireValue,
            -> decryptSignalCiphertext(envelope)

            else -> {
                if (
                    envelope.encryptionMode == "signal_plus_shared_secret" ||
                    envelope.ciphertext.startsWith("ss1:")
                ) {
                    envelope.ciphertext
                } else {
                    crypto.decryptToPlainText(envelope.ciphertext)
                }
            }
        }

        return if (
            envelope.encryptionMode == "signal_plus_shared_secret" ||
            transportPlainText.startsWith("ss1:")
        ) {
            sharedSecretCrypto.decryptIfNeeded(
                conversationUuid = envelope.conversationUuid,
                ciphertext = transportPlainText,
            )
        } else {
            transportPlainText
        }
    }

    private fun decryptSignalCiphertext(
        envelope: MessageCipherEnvelope,
    ): String {
        val ciphertextVersion = envelope.ciphertextVersion
            ?: error("Signal ciphertext version is required")
        val signalType = SignalCiphertextType.fromWireValue(ciphertextVersion)
        val candidateDeviceIds = buildSenderDeviceCandidates(envelope)

        if (candidateDeviceIds.isEmpty()) {
            error("Сервер не передал sender_device_id, а локальная Signal-сессия для этого пользователя ещё не известна")
        }

        var lastError: Throwable? = null
        candidateDeviceIds.forEach { deviceId ->
            runCatching {
                runBlocking {
                    signalMessageCryptoEngine.decrypt(
                        remoteAddress = SignalRemoteAddress(
                            userId = envelope.senderUserId,
                            deviceId = deviceId,
                        ),
                        ciphertext = envelope.ciphertext,
                        type = signalType,
                    )
                }
            }.onSuccess { return it }
                .onFailure {
                Log.w(
                    logTag,
                    "Signal decrypt failed for messageId=${envelope.messageId} with senderDeviceId=$deviceId",
                    it,
                )
                lastError = it
            }
        }

        throw lastError ?: IllegalStateException("Signal message decryption failed")
    }

    private fun buildSenderDeviceCandidates(
        envelope: MessageCipherEnvelope,
    ): List<Int> {
        val explicitDeviceId = envelope.senderDeviceId
        if (explicitDeviceId != null && explicitDeviceId > 0) {
            return listOf(explicitDeviceId)
        }

        return signalStore
            .getSubDeviceSessions("user:${envelope.senderUserId}")
            .distinct()
            .sorted()
    }

    private suspend fun buildDecodeContext(
        items: List<com.example.securechatapp.data.remote.dto.MessageItemDto>,
    ): DecodeContext {
        val messageIds = buildSet {
            items.forEach { item ->
                if (item.messageId > 0) {
                    add(item.messageId)
                }
                item.replyPreview?.messageId?.takeIf { it > 0 }?.let(::add)
                item.forwardPreview?.messageId?.takeIf { it > 0 }?.let(::add)
            }
        }
        return DecodeContext(
            cachedPayloadsByMessageId = decryptedMessagePayloadCacheRepository.getByMessageIds(messageIds),
        )
    }

    private suspend fun persistDecodedPayloads(
        decodeContext: DecodeContext,
    ) {
        decryptedMessagePayloadCacheRepository.upsertAll(
            decodeContext.refreshedPayloadsByMessageId.values,
        )
    }

    private fun cacheDecodedPayload(
        envelope: MessageCipherEnvelope,
        payload: DecodedMessagePayload,
        decodeContext: DecodeContext,
    ) {
        val messageId = envelope.messageId?.takeIf { it > 0 } ?: return
        decodeContext.refreshedPayloadsByMessageId[messageId] =
            DecryptedMessagePayloadCacheRepository.CachedDecryptedMessagePayload(
                messageId = messageId,
                messageUuid = envelope.messageUuid,
                text = payload.text,
                attachments = payload.attachments,
                updatedAt = crypto.nowIso(),
            )
    }

    private fun DecryptedMessagePayloadCacheRepository.CachedDecryptedMessagePayload.toDecodedPayload():
            DecodedMessagePayload {
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

    private companion object {
        const val UNDECRYPTABLE_MESSAGE_PLACEHOLDER =
            "Сообщение не удалось расшифровать на этом устройстве"
        const val SIGNAL_DEVICE_PAYLOAD_NONCE = "signal"
    }
}

private suspend fun com.example.securechatapp.data.remote.dto.MessageItemDto.toDomainMessage(
    conversationUuid: String,
    peerUserId: Int,
    forceMine: Boolean,
    decoder: suspend (MessageRepository.MessageCipherEnvelope) -> MessageRepository.DecodedMessagePayload,
): ChatMessage {
    val decoded = decoder(
        MessageRepository.MessageCipherEnvelope(
            messageId = messageId,
            messageUuid = messageUuid,
            conversationUuid = conversationUuid,
            senderUserId = senderUserId,
            senderDeviceId = senderDeviceId,
            ciphertext = ciphertext,
            ciphertextVersion = ciphertextVersion,
            encryptionMode = encryptionMode,
        )
    )
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

private suspend fun com.example.securechatapp.data.remote.dto.MessagePreviewDto.toDomainPreview(
    conversationUuid: String,
    decoder: suspend (MessageRepository.MessageCipherEnvelope) -> MessageRepository.DecodedMessagePayload,
): MessagePreview {
    val decoded = decoder(
        MessageRepository.MessageCipherEnvelope(
            messageId = messageId,
            messageUuid = messageUuid,
            conversationUuid = conversationUuid,
            senderUserId = senderUserId,
            senderDeviceId = senderDeviceId,
            ciphertext = ciphertext,
            ciphertextVersion = ciphertextVersion,
            encryptionMode = if (ciphertext.startsWith("ss1:")) "signal_plus_shared_secret" else "signal",
        )
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
