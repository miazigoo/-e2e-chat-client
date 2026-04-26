package com.example.securechatapp.data.repository

import com.example.securechatapp.core.common.ConversationsRefreshBus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class OutboxDispatcher @Inject constructor(
    private val outboxRepository: OutboxRepository,
    private val messageRepository: MessageRepository,
    private val conversationsRefreshBus: ConversationsRefreshBus,
) {
    private val mutex = Mutex()

    suspend fun recoverStuckMessages() {
        outboxRepository.requeueAllSendingMessages()
    }

    suspend fun drainAll() {
        mutex.withLock {
            val pendingMessages = outboxRepository.listQueuedMessages()
            pendingMessages.forEach { pending ->
                dispatchPendingMessage(pending)
            }
        }
    }

    suspend fun drainConversation(conversationId: Int) {
        mutex.withLock {
            val pendingMessages = outboxRepository.listQueuedMessages(conversationId)
            pendingMessages.forEach { pending ->
                dispatchPendingMessage(pending)
            }
        }
    }

    suspend fun drainMessage(localMessageId: Int) {
        mutex.withLock {
            val pending = outboxRepository.getPendingMessage(localMessageId) ?: return
            dispatchPendingMessage(pending)
        }
    }

    private suspend fun dispatchPendingMessage(
        pending: PendingOutgoingMessage,
    ) {
        try {
            outboxRepository.markSending(pending.localMessageId)

            messageRepository.sendMessage(
                conversationId = pending.conversationId,
                recipientUserId = pending.recipientUserId,
                plainText = pending.plainText,
                replyToMessageId = pending.replyToMessageId,
                attachmentIds = pending.attachmentIds,
                attachmentDescriptors = pending.attachmentDescriptors,
                messageUuid = pending.clientMessageUuid,
            )

            outboxRepository.deletePendingMessage(pending.localMessageId)
            conversationsRefreshBus.requestRefresh()
        } catch (e: Exception) {
            outboxRepository.markFailed(
                localMessageId = pending.localMessageId,
                errorMessage = e.message ?: "Не удалось отправить сообщение",
            )
        }
    }
}
