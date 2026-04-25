package com.example.securechatapp.data.repository

import com.example.securechatapp.data.remote.api.ChatBackendApi
import com.example.securechatapp.data.remote.dto.ConversationListItemDto
import com.example.securechatapp.data.remote.dto.CreateConversationRequestDto
import com.example.securechatapp.data.remote.dto.UpdateConversationSettingsRequestDto
import com.example.securechatapp.domain.model.ConversationDetails
import com.example.securechatapp.domain.model.ConversationListItem
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
                title = it.title ?: (it.peer.nickname ?: "User ${it.peer.userId}"),
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

    suspend fun getConversation(conversationId: Int): ConversationDetails {
        val data = safe { api.getConversation(conversationId).data }
        return ConversationDetails(
            conversationId = data.conversationId,
            conversationUuid = data.conversationUuid,
            title = data.title ?: "Chat ${data.conversationId}",
            peerUserId = data.peerUserId,
            protectionMode = data.protectionMode,
            messageTtlDays = data.messageTtlDays,
            deleteAfterReadSeconds = data.deleteAfterReadSeconds,
            sharedSecretEnabled = data.sharedSecretEnabled,
            sharedSecretFingerprint = data.sharedSecretFingerprint,
            peerSharedSecretEnabled = data.peerSharedSecretEnabled,
            isActive = data.isActive,
            isPurged = data.isPurged,
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
