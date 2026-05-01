package com.example.securechatapp.data.repository

import android.net.Uri
import com.example.securechatapp.core.common.ConversationsRefreshBus
import com.example.securechatapp.data.files.AttachmentUploadManager
import com.example.securechatapp.domain.model.MessageSendPhase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class OutboxDispatcher @Inject constructor(
    private val outboxRepository: OutboxRepository,
    private val messageRepository: MessageRepository,
    private val attachmentUploadManager: AttachmentUploadManager,
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
            outboxRepository.markSending(
                localMessageId = pending.localMessageId,
                phase = pending.sendPhase ?: MessageSendPhase.SENDING,
                progress = pending.sendProgress,
            )

            val preparedDescriptors = if (
                pending.attachmentDescriptors.isEmpty() &&
                pending.localAttachmentUris.isNotEmpty()
            ) {
                var lastReportedProgress = -1
                outboxRepository.updateSendProgress(
                    localMessageId = pending.localMessageId,
                    phase = MessageSendPhase.UPLOADING,
                    progress = 0,
                )
                val uploaded = attachmentUploadManager.uploadEncryptedAttachments(
                    conversationId = pending.conversationId,
                    uris = pending.localAttachmentUris.map(Uri::parse),
                    onProgress = { percent ->
                        if (percent != lastReportedProgress) {
                            lastReportedProgress = percent
                            runBlocking {
                                outboxRepository.updateSendProgress(
                                    localMessageId = pending.localMessageId,
                                    phase = MessageSendPhase.UPLOADING,
                                    progress = percent,
                                )
                            }
                        }
                    },
                )
                uploaded.map { it.descriptor }
                    .also { outboxRepository.updatePreparedAttachments(pending.localMessageId, it) }
            } else {
                pending.attachmentDescriptors
            }

            outboxRepository.updateSendProgress(
                localMessageId = pending.localMessageId,
                phase = MessageSendPhase.SENDING,
                progress = null,
            )
            messageRepository.sendMessage(
                conversationId = pending.conversationId,
                recipientUserId = pending.recipientUserId,
                plainText = pending.plainText,
                replyToMessageId = pending.replyToMessageId,
                attachmentIds = preparedDescriptors.map { it.attachmentId },
                attachmentDescriptors = preparedDescriptors,
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
