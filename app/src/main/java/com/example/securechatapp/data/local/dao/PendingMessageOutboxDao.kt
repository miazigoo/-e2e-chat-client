package com.example.securechatapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.securechatapp.data.local.entity.PendingMessageOutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingMessageOutboxDao {

    @Query(
        """
        SELECT * FROM pending_message_outbox
        WHERE conversationId = :conversationId
        ORDER BY createdAt ASC, localMessageId ASC
        """
    )
    fun observeForConversation(conversationId: Int): Flow<List<PendingMessageOutboxEntity>>

    @Query(
        """
        SELECT * FROM pending_message_outbox
        WHERE localMessageId = :localMessageId
        LIMIT 1
        """
    )
    suspend fun getByLocalMessageId(localMessageId: Int): PendingMessageOutboxEntity?

    @Query(
        """
        SELECT * FROM pending_message_outbox
        WHERE conversationId = :conversationId
          AND status = :status
        ORDER BY createdAt ASC, localMessageId ASC
        """
    )
    suspend fun listByConversationAndStatus(
        conversationId: Int,
        status: String,
    ): List<PendingMessageOutboxEntity>

    @Query(
        """
        SELECT * FROM pending_message_outbox
        WHERE status = :status
        ORDER BY createdAt ASC, localMessageId ASC
        """
    )
    suspend fun listByStatus(
        status: String,
    ): List<PendingMessageOutboxEntity>

    @Upsert
    suspend fun upsert(entity: PendingMessageOutboxEntity)

    @Query(
        """
        DELETE FROM pending_message_outbox
        WHERE localMessageId = :localMessageId
        """
    )
    suspend fun deleteByLocalMessageId(localMessageId: Int)

    @Query(
        """
        UPDATE pending_message_outbox
        SET status = :status,
            sendPhase = :sendPhase,
            sendProgress = :sendProgress,
            errorMessage = :errorMessage
        WHERE localMessageId = :localMessageId
        """
    )
    suspend fun updateStatus(
        localMessageId: Int,
        status: String,
        sendPhase: String?,
        sendProgress: Int?,
        errorMessage: String?,
    )

    @Query(
        """
        UPDATE pending_message_outbox
        SET attachmentIdsCsv = :attachmentIdsCsv,
            attachmentDescriptorsJson = :attachmentDescriptorsJson,
            attachmentPreviewJson = :attachmentPreviewJson,
            hasAttachments = :hasAttachments
        WHERE localMessageId = :localMessageId
        """
    )
    suspend fun updatePreparedAttachments(
        localMessageId: Int,
        attachmentIdsCsv: String,
        attachmentDescriptorsJson: String,
        attachmentPreviewJson: String,
        hasAttachments: Boolean,
    )

    @Query(
        """
        UPDATE pending_message_outbox
        SET status = 'queued',
            sendProgress = NULL,
            errorMessage = NULL
        WHERE conversationId = :conversationId
          AND status = 'sending'
        """
    )
    suspend fun requeueSendingForConversation(conversationId: Int)

    @Query(
        """
        UPDATE pending_message_outbox
        SET status = 'queued',
            sendProgress = NULL,
            errorMessage = NULL
        WHERE status = 'sending'
        """
    )
    suspend fun requeueAllSending()
}
