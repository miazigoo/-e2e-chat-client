package com.example.securechatapp.data.repository

import com.example.securechatapp.core.common.ConversationsRefreshBus
import com.example.securechatapp.core.crypto.EncryptedAttachmentDescriptor
import com.example.securechatapp.data.remote.dto.SendMessageResponseDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class OutboxDispatcherTest {

    private val outboxRepository = mockk<OutboxRepository>(relaxed = true)
    private val messageRepository = mockk<MessageRepository>()
    private val refreshBus = mockk<ConversationsRefreshBus>(relaxed = true)

    private val dispatcher = OutboxDispatcher(
        outboxRepository = outboxRepository,
        messageRepository = messageRepository,
        conversationsRefreshBus = refreshBus,
    )

    @Test
    fun `drainAll sends queued messages and clears outbox on success`() = runTest {
        val pending = pendingMessage()
        coEvery { outboxRepository.listQueuedMessages() } returns listOf(pending)
        coEvery {
            messageRepository.sendMessage(
                conversationId = pending.conversationId,
                recipientUserId = pending.recipientUserId,
                plainText = pending.plainText,
                attachmentIds = pending.attachmentIds,
                attachmentDescriptors = pending.attachmentDescriptors,
                messageUuid = pending.clientMessageUuid,
            )
        } returns sendResponse(pending)

        dispatcher.drainAll()

        coVerify(exactly = 1) { outboxRepository.markSending(pending.localMessageId) }
        coVerify(exactly = 1) {
            messageRepository.sendMessage(
                conversationId = pending.conversationId,
                recipientUserId = pending.recipientUserId,
                plainText = pending.plainText,
                attachmentIds = pending.attachmentIds,
                attachmentDescriptors = pending.attachmentDescriptors,
                messageUuid = pending.clientMessageUuid,
            )
        }
        coVerify(exactly = 1) { outboxRepository.deletePendingMessage(pending.localMessageId) }
        verify(exactly = 1) { refreshBus.requestRefresh() }
    }

    @Test
    fun `drainAll marks message as failed when send throws`() = runTest {
        val pending = pendingMessage()
        coEvery { outboxRepository.listQueuedMessages() } returns listOf(pending)
        coEvery {
            messageRepository.sendMessage(
                conversationId = any(),
                recipientUserId = any(),
                plainText = any(),
                attachmentIds = any(),
                attachmentDescriptors = any(),
                messageUuid = any(),
            )
        } throws IllegalStateException("backend unavailable")

        dispatcher.drainAll()

        coVerify(exactly = 1) { outboxRepository.markSending(pending.localMessageId) }
        coVerify(exactly = 1) {
            outboxRepository.markFailed(
                localMessageId = pending.localMessageId,
                errorMessage = "backend unavailable",
            )
        }
        coVerify(exactly = 0) { outboxRepository.deletePendingMessage(any()) }
        verify(exactly = 0) { refreshBus.requestRefresh() }
    }

    @Test
    fun `recoverStuckMessages requeues sending entries`() = runTest {
        dispatcher.recoverStuckMessages()

        coVerify(exactly = 1) { outboxRepository.requeueAllSendingMessages() }
    }

    private fun pendingMessage() = PendingOutgoingMessage(
        localMessageId = -42,
        conversationId = 7,
        recipientUserId = 100,
        clientMessageUuid = "uuid-1",
        plainText = "hello",
        attachmentIds = listOf(9),
        attachmentDescriptors = listOf(
            EncryptedAttachmentDescriptor(
                attachmentId = 9,
                mimeType = "image/jpeg",
                fileSize = 128L,
            )
        ),
        createdAt = "2026-04-25T12:00:00Z",
        status = com.example.securechatapp.domain.model.MessageSendStatus.SENDING,
        errorMessage = null,
    )

    private fun sendResponse(pending: PendingOutgoingMessage) = SendMessageResponseDto(
        messageId = 11,
        messageUuid = pending.clientMessageUuid,
        conversationId = pending.conversationId,
        recipientUserId = pending.recipientUserId,
        recipientDeviceId = 1,
        serverReceivedAt = "2026-04-25T12:00:01Z",
        deliveryStatus = "sent",
    )
}
