package com.example.securechatapp.data.repository

import com.example.securechatapp.data.remote.api.ChatBackendApi
import com.example.securechatapp.data.remote.dto.ConversationListItemDto
import com.example.securechatapp.data.remote.dto.CreateConversationRequestDto
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
                title = it.title ?: (it.peer.nickname ?: "User ${it.peer.userId}"),
                peerUserId = it.peer.userId,
                peerNickname = it.peer.nickname ?: "user_${it.peer.userId}",
                unreadCount = it.unreadCount,
                lastMessagePreview = buildConversationPreview(it),
                updatedAt = it.lastMessage?.serverReceivedAt ?: it.updatedAt,
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
            title = data.title ?: "Chat ${data.conversationId}",
            peerUserId = data.peerUserId,
            protectionMode = data.protectionMode,
        )
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
