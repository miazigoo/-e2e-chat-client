package com.example.securechatapp.data.repository

import com.example.securechatapp.core.common.ConversationsRefreshBus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class BackgroundSyncCoordinator @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val chatCacheRepository: ChatCacheRepository,
    private val conversationsRefreshBus: ConversationsRefreshBus,
) {
    private val syncMutex = Mutex()

    suspend fun syncOnce(
        maxConversations: Int = DEFAULT_MAX_CONVERSATIONS,
    ): BackgroundSyncResult {
        return syncMutex.withLock {
            var refreshedConversations = 0
            var refreshedMessages = 0

            val conversations = conversationRepository.listConversations()
            chatCacheRepository.replaceConversations(conversations)
            refreshedConversations = conversations.size

            conversations
                .take(maxConversations)
                .forEach { conversation ->
                    runCatching {
                        val details = conversationRepository.getConversation(conversation.conversationId)
                        chatCacheRepository.upsertConversationDetails(details)

                        val messages = messageRepository.listMessages(
                            conversationId = conversation.conversationId,
                            peerUserId = conversation.peerUserId,
                        )
                        chatCacheRepository.replaceMessages(
                            conversationId = conversation.conversationId,
                            messages = messages,
                        )
                        chatCacheRepository.updateConversationSnapshotFromMessages(
                            conversationId = conversation.conversationId,
                            messages = messages,
                        )
                        refreshedMessages += messages.size
                    }
                }

            conversationsRefreshBus.requestRefresh()

            BackgroundSyncResult(
                conversations = refreshedConversations,
                messages = refreshedMessages,
            )
        }
    }

    private companion object {
        const val DEFAULT_MAX_CONVERSATIONS = 30
    }
}

data class BackgroundSyncResult(
    val conversations: Int,
    val messages: Int,
)
