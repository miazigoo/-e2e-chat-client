package com.example.securechatapp.data.repository

import androidx.room.withTransaction
import com.example.securechatapp.data.local.db.SecureChatDatabase
import com.example.securechatapp.data.local.db.toConversationDetails
import com.example.securechatapp.data.local.db.toConversationListItem
import com.example.securechatapp.data.local.db.toDomain
import com.example.securechatapp.data.local.db.toEntity
import com.example.securechatapp.domain.model.ChatMessage
import com.example.securechatapp.domain.model.MessagePreview
import com.example.securechatapp.domain.model.ConversationDetails
import com.example.securechatapp.domain.model.ConversationListItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

@Singleton
class ChatCacheRepository @Inject constructor(
    private val database: SecureChatDatabase,
    private val json: Json,
) {
    private val conversationsDao = database.conversationCacheDao()
    private val messagesDao = database.messageCacheDao()

    fun observeConversations(): Flow<List<ConversationListItem>> {
        return conversationsDao.observeAll().map { entities ->
            entities.map { it.toConversationListItem() }
        }
    }

    suspend fun getCachedConversations(): List<ConversationListItem> {
        return conversationsDao.listAll().map { it.toConversationListItem() }
    }

    fun observeConversationDetails(
        conversationId: Int,
    ): Flow<ConversationDetails?> {
        return conversationsDao.observeById(conversationId).map { entity ->
            entity?.toConversationDetails()
        }
    }

    fun observeMessages(
        conversationId: Int,
    ): Flow<List<ChatMessage>> {
        return messagesDao.observeForConversation(conversationId).map { entities ->
            entities.map { it.toDomain(json) }
        }
    }

    suspend fun replaceConversations(
        items: List<ConversationListItem>,
    ) {
        database.withTransaction {
            if (items.isEmpty()) {
                conversationsDao.clearAll()
                return@withTransaction
            }

            val ids = items.map { it.conversationId }
            val existing = conversationsDao.getByIds(ids).associateBy { it.conversationId }

            val entities = items.map { item ->
                item.toEntity(previous = existing[item.conversationId])
            }

            conversationsDao.upsertAll(entities)
            conversationsDao.deleteAllExcept(ids)
        }
    }

    suspend fun upsertConversationDetails(
        details: ConversationDetails,
    ) {
        database.withTransaction {
            val previous = conversationsDao.getById(details.conversationId)
            conversationsDao.upsert(details.toEntity(previous))
        }
    }

    suspend fun replaceMessages(
        conversationId: Int,
        messages: List<ChatMessage>,
    ) {
        val existingMessagesById = messagesDao
            .listForConversation(conversationId)
            .map { it.toDomain(json) }
            .associateBy { it.messageId }
        val hydratedMessages = messages.map { incoming ->
            preserveReadableCachedMessage(
                cached = existingMessagesById[incoming.messageId],
                incoming = incoming,
            )
        }

        database.withTransaction {
            messagesDao.deleteByConversationId(conversationId)
            messagesDao.upsertAll(
                hydratedMessages.map { message ->
                    message.toEntity(
                        conversationId = conversationId,
                        json = json,
                    )
                }
            )
        }
    }

    suspend fun markMessageDelivered(
        messageId: Int,
        deliveredAt: String,
    ) {
        messagesDao.markDelivered(
            messageId = messageId,
            deliveredAt = deliveredAt,
        )
    }

    suspend fun markMessageRead(
        messageId: Int,
        readAt: String,
    ) {
        messagesDao.markRead(
            messageId = messageId,
            readAt = readAt,
        )
    }

    suspend fun getLastEventId(
        conversationId: Int,
    ): Int? {
        return conversationsDao.getLastEventId(conversationId)
    }

    suspend fun setLastEventId(
        conversationId: Int,
        lastEventId: Int?,
    ) {
        conversationsDao.updateLastEventId(
            conversationId = conversationId,
            lastEventId = lastEventId,
        )
    }

    suspend fun updateConversationLastMessagePreview(
        conversationId: Int,
        lastMessagePreview: String,
        updatedAt: String?,
    ) {
        conversationsDao.updateLastMessagePreview(
            conversationId = conversationId,
            lastMessagePreview = lastMessagePreview,
            updatedAt = updatedAt,
        )
    }

    suspend fun updateConversationUnreadCount(
        conversationId: Int,
        unreadCount: Int,
    ) {
        conversationsDao.updateUnreadCount(
            conversationId = conversationId,
            unreadCount = unreadCount,
        )
    }

    private fun preserveReadableCachedMessage(
        cached: ChatMessage?,
        incoming: ChatMessage,
    ): ChatMessage {
        if (cached == null || incoming.text != UNDECRYPTABLE_MESSAGE_PLACEHOLDER) {
            return incoming
        }

        if (cached.text == UNDECRYPTABLE_MESSAGE_PLACEHOLDER) {
            return incoming
        }

        return incoming.copy(
            text = cached.text,
            hasAttachments = cached.hasAttachments || incoming.hasAttachments,
            attachmentIds = if (incoming.attachmentIds.isNotEmpty()) {
                incoming.attachmentIds
            } else {
                cached.attachmentIds
            },
            attachments = if (incoming.attachments.isNotEmpty()) {
                incoming.attachments
            } else {
                cached.attachments
            },
            replyPreview = preserveReadablePreview(
                cached = cached.replyPreview,
                incoming = incoming.replyPreview,
            ),
            forwardPreview = preserveReadablePreview(
                cached = cached.forwardPreview,
                incoming = incoming.forwardPreview,
            ),
        )
    }

    private fun preserveReadablePreview(
        cached: MessagePreview?,
        incoming: MessagePreview?,
    ): MessagePreview? {
        if (incoming == null) return cached
        if (incoming.text != UNDECRYPTABLE_MESSAGE_PLACEHOLDER) return incoming
        if (cached == null || cached.text == UNDECRYPTABLE_MESSAGE_PLACEHOLDER) return incoming
        return incoming.copy(text = cached.text)
    }

    private companion object {
        const val UNDECRYPTABLE_MESSAGE_PLACEHOLDER =
            "Сообщение не удалось расшифровать на этом устройстве"
    }
}
