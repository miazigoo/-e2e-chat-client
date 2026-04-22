package com.example.securechatapp.data.repository

import com.example.securechatapp.core.crypto.DevCryptoEngine
import com.example.securechatapp.data.local.preferences.SessionLocalDataSource
import com.example.securechatapp.data.remote.api.ChatBackendApi
import com.example.securechatapp.data.remote.dto.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import retrofit2.HttpException

@Singleton
class BackendRepository @Inject constructor(
    private val api: ChatBackendApi,
    private val sessionStore: SessionLocalDataSource,
    private val crypto: DevCryptoEngine,
    private val json: Json,
) {
    data class ConversationUi(
        val conversationId: Int,
        val title: String,
        val peerUserId: Int,
        val peerNickname: String,
        val unreadCount: Int,
        val lastMessagePreview: String,
        val updatedAt: String? = null,
    )

    data class MessageUi(
        val messageId: Int,
        val text: String,
        val isMine: Boolean,
        val createdAt: String,
        val deliveredAt: String? = null,
        val readAt: String? = null,
        val hasAttachments: Boolean = false,
    )

    data class AttachmentUi(
        val attachmentId: Int,
        val fileName: String,
        val mimeType: String?,
        val fileSize: Long,
        val canDownload: Boolean = true,
    ) {
        val isImage: Boolean
            get() = mimeType?.startsWith("image/") == true
    }

    data class AttachmentDownloadInfo(
        val attachmentId: Int,
        val downloadUrl: String,
        val fileName: String,
        val mimeType: String?,
    )

    private suspend fun <T> safe(block: suspend () -> T): T {
        try {
            return block()
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string()
            val parsed = body?.let {
                runCatching { json.decodeFromString(ApiErrorEnvelopeDto.serializer(), it) }.getOrNull()
            }

            val fallbackMessage = buildString {
                append(parsed?.error?.message ?: e.message().orEmpty().ifBlank { "HTTP ${e.code()}" })
                if (!body.isNullOrBlank() && parsed == null) {
                    append(": ")
                    append(body)
                }
            }

            throw IllegalStateException(fallbackMessage)
        }
    }

    suspend fun ensureDeviceUuid(): String = sessionStore.getOrCreateDeviceUuid()

    suspend fun listConversations(): List<ConversationUi> {
        return safe { api.listConversations().data }.items.map {
            ConversationUi(
                conversationId = it.conversationId,
                title = it.title ?: (it.peer.nickname ?: "User ${it.peer.userId}"),
                peerUserId = it.peer.userId,
                peerNickname = it.peer.nickname ?: "user_${it.peer.userId}",
                unreadCount = it.unreadCount,
                lastMessagePreview = buildConversationPreview(it),
                updatedAt = it.lastMessage?.serverReceivedAt ?: it.updatedAt,
            )
        }
    }

    suspend fun searchUsers(query: String): List<UserSearchItemDto> {
        return safe { api.searchUsers(query.trim()).data }.items
    }

    suspend fun createConversation(userId: Int): Int {
        return safe {
            api.createConversation(
                CreateConversationRequestDto(recipientUserId = userId)
            ).data
        }.conversationId
    }

    suspend fun getConversation(conversationId: Int): GetConversationResponseDto {
        return safe { api.getConversation(conversationId).data }
    }

    suspend fun listMessages(
        conversationId: Int,
        peerUserId: Int,
    ): List<MessageUi> {
        return safe { api.listMessages(conversationId = conversationId).data }
            .items
            .sortedBy { it.messageId }
            .map {
                MessageUi(
                    messageId = it.messageId,
                    text = crypto.decryptToPlainText(it.ciphertext),
                    isMine = it.senderUserId != peerUserId,
                    createdAt = it.serverReceivedAt,
                    deliveredAt = it.deliveredAt,
                    readAt = it.readAt,
                    hasAttachments = it.hasAttachments,
                )
            }
    }

    suspend fun markIncomingMessagesAsDelivered(
        messages: List<MessageUi>,
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
        messages: List<MessageUi>,
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
    ) {
        val finalText = when {
            plainText.isNotBlank() -> plainText
            attachmentIds.isNotEmpty() -> "[attachment]"
            else -> error("Нельзя отправить пустое сообщение")
        }

        val encrypted = crypto.encryptPlainText(finalText)

        safe {
            api.sendMessage(
                SendMessageRequestDto(
                    conversationId = conversationId,
                    recipientUserId = recipientUserId,
                    messageUuid = UUID.randomUUID().toString(),
                    ciphertext = encrypted.ciphertext,
                    nonce = encrypted.nonce,
                    clientCreatedAt = crypto.nowIso(),
                    attachmentIds = attachmentIds.distinct(),
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

    suspend fun heartbeat(): String? {
        return runCatching {
            safe { api.sendHeartbeat().data }.lastSeenAt
        }.getOrNull()
    }

    suspend fun updateFcmToken(token: String?) {
        runCatching {
            safe {
                api.updateFcmToken(
                    UpdateFcmTokenRequestDto(fcmToken = token)
                ).data
            }
        }
    }

    suspend fun logoutSession() {
        runCatching {
            safe { api.logoutSession().data }
        }
        sessionStore.clearSession(keepDeviceUuid = true)
    }

    suspend fun revokeCurrentDevice() {
        runCatching {
            safe { api.revokeCurrentDevice().data }
        }
        sessionStore.clearSession(keepDeviceUuid = true)
    }

    private fun buildConversationPreview(item: ConversationListItemDto): String {
        val last = item.lastMessage ?: return "Нет сообщений"

        val isMine = last.senderUserId != item.peer.userId

        val body = when {
            last.hasAttachments && last.messageType == "file" -> "📎 Вложение"
            last.hasAttachments -> "📎 Сообщение с вложением"
            last.messageType == "service" -> "Сервисное сообщение"
            else -> "Сообщение"
        }

        return if (isMine) "Вы: $body" else body
    }

    suspend fun listMessageAttachments(
        messageId: Int,
    ): List<AttachmentUi> {
        return safe { api.listMessageAttachments(messageId).data }
            .items
            .map { item ->
                AttachmentUi(
                    attachmentId = item.attachmentId,
                    fileName = item.encryptedFileName
                        ?.takeIf { it.isNotBlank() }
                        ?: fallbackAttachmentName(
                            attachmentId = item.attachmentId,
                            mimeType = item.mimeHint,
                        ),
                    mimeType = item.mimeHint,
                    fileSize = item.fileSize,
                    canDownload = item.deletedAt == null,
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
}
