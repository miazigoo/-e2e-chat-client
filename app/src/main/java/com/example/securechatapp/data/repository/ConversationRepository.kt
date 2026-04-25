package com.example.securechatapp.data.repository

import com.example.securechatapp.data.remote.api.ChatBackendApi
import com.example.securechatapp.data.remote.dto.ConversationListItemDto
import com.example.securechatapp.data.remote.dto.CreateConversationRequestDto
import com.example.securechatapp.data.remote.dto.UpdateConversationSettingsRequestDto
import com.example.securechatapp.domain.model.ConversationDetails
import com.example.securechatapp.domain.model.ConversationListItem
import com.example.securechatapp.domain.model.MessagePreview
import com.example.securechatapp.domain.model.UserSafety
import com.example.securechatapp.domain.model.UserSearchItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class ConversationRepository @Inject constructor(
    private val api: ChatBackendApi,
    json: Json,
) : BaseApiRepository(json) {

    suspend fun listConversations(): List<ConversationListItem> {
        return safe { api.listConversations().data }.items.map {
            ConversationListItem(
                conversationId = it.conversationId,
                conversationUuid = it.conversationUuid,
                title = buildConversationTitle(it.title, it.isSavedMessages, it.peer.nickname, it.peer.userId),
                isSavedMessages = it.isSavedMessages,
                peerUserId = it.peer.userId,
                peerNickname = it.peer.nickname ?: "user_${it.peer.userId}",
                unreadCount = it.unreadCount,
                lastMessagePreview = buildConversationPreview(it),
                protectionMode = it.protectionMode,
                messageTtlDays = it.messageTtlDays,
                deleteAfterReadSeconds = it.deleteAfterReadSeconds,
                isActive = it.isActive,
                isPurged = it.isPurged,
                updatedAt = it.lastMessage?.serverReceivedAt ?: it.updatedAt,
                sharedSecretEnabled = it.sharedSecretEnabled,
                sharedSecretFingerprint = it.sharedSecretFingerprint,
                peerSharedSecretEnabled = it.peerSharedSecretEnabled,
                pinnedMessage = it.pinnedMessage?.toDomain(),
            )
        }
    }

    suspend fun searchUsers(query: String): List<UserSearchItem> {
        return safe { api.searchUsers(query.trim()).data }
            .items
            .map { dto ->
                UserSearchItem(
                    userId = dto.userId,
                    nickname = dto.nickname,
                )
            }
    }

    suspend fun createConversation(userId: Int): Int {
        return safe {
            api.createConversation(
                CreateConversationRequestDto(recipientUserId = userId)
            ).data
        }.conversationId
    }

    suspend fun getUserSafety(userId: Int): UserSafety {
        val data = safe { api.getUserSafety(userId).data }
        return UserSafety(
            userId = data.userId,
            nickname = data.nickname,
            canStartConversation = data.canStartConversation,
            isDeleted = data.isDeleted,
            pendingDeletion = data.pendingDeletion,
            hasActiveDevice = data.hasActiveDevice,
            supportsEncryptedChat = data.supportsEncryptedChat,
            safetyCodeAvailable = data.safetyCodeAvailable,
        )
    }

    suspend fun getConversation(conversationId: Int): ConversationDetails {
        val data = safe { api.getConversation(conversationId).data }
        return ConversationDetails(
            conversationId = data.conversationId,
            conversationUuid = data.conversationUuid,
            title = buildConversationTitle(data.title, data.isSavedMessages, null, data.peerUserId),
            isSavedMessages = data.isSavedMessages,
            peerUserId = data.peerUserId,
            protectionMode = data.protectionMode,
            messageTtlDays = data.messageTtlDays,
            deleteAfterReadSeconds = data.deleteAfterReadSeconds,
            sharedSecretEnabled = data.sharedSecretEnabled,
            sharedSecretFingerprint = data.sharedSecretFingerprint,
            peerSharedSecretEnabled = data.peerSharedSecretEnabled,
            isActive = data.isActive,
            isPurged = data.isPurged,
            pinnedMessage = data.pinnedMessage?.toDomain(),
        )
    }

    suspend fun updateSharedSecretSettings(
        conversationId: Int,
        enabled: Boolean,
        fingerprint: String?,
    ): ConversationDetails {
        safe {
            api.updateConversationSettings(
                conversationId = conversationId,
                body = UpdateConversationSettingsRequestDto(
                    sharedSecretEnabled = enabled,
                    sharedSecretFingerprint = fingerprint,
                ),
            ).data
        }

        return getConversation(conversationId)
    }

    suspend fun pinMessage(
        conversationId: Int,
        messageId: Int,
    ): ConversationDetails {
        safe {
            api.pinMessage(
                conversationId = conversationId,
                messageId = messageId,
            ).data
        }
        return getConversation(conversationId)
    }

    suspend fun unpinMessage(
        conversationId: Int,
    ): ConversationDetails {
        safe { api.unpinMessage(conversationId).data }
        return getConversation(conversationId)
    }

    private fun buildConversationTitle(
        title: String?,
        isSavedMessages: Boolean,
        peerNickname: String?,
        peerUserId: Int,
    ): String {
        return when {
            isSavedMessages -> title ?: "Избранное"
            !title.isNullOrBlank() -> title
            !peerNickname.isNullOrBlank() -> peerNickname
            else -> "User $peerUserId"
        }
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
}

private fun com.example.securechatapp.data.remote.dto.MessagePreviewDto.toDomain(): MessagePreview {
    return MessagePreview(
        messageId = messageId,
        messageUuid = messageUuid,
        senderUserId = senderUserId,
        messageType = messageType,
        text = "",
        hasAttachments = hasAttachments,
        clientCreatedAt = clientCreatedAt,
    )
}
