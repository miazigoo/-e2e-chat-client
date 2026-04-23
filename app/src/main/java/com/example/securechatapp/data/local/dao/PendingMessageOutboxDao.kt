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
        SET status = :status, errorMessage = :errorMessage
        WHERE localMessageId = :localMessageId
        """
    )
    suspend fun updateStatus(
        localMessageId: Int,
        status: String,
        errorMessage: String?,
    )

    @Query(
        """
        UPDATE pending_message_outbox
        SET status = 'queued', errorMessage = NULL
        WHERE conversationId = :conversationId
          AND status = 'sending'
        """
    )
    suspend fun requeueSendingForConversation(conversationId: Int)
}
