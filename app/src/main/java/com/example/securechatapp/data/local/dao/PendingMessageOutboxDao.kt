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
          AND nextAttemptAtEpochMillis <= :nowEpochMillis
        ORDER BY nextAttemptAtEpochMillis ASC, createdAt ASC, localMessageId ASC
        LIMIT :limit
        """
    )
    suspend fun listDueByConversationAndStatus(
        conversationId: Int,
        status: String,
        nowEpochMillis: Long,
        limit: Int,
    ): List<PendingMessageOutboxEntity>

    @Query(
        """
        SELECT * FROM pending_message_outbox
        WHERE status = :status
          AND nextAttemptAtEpochMillis <= :nowEpochMillis
        ORDER BY nextAttemptAtEpochMillis ASC, createdAt ASC, localMessageId ASC
        LIMIT :limit
        """
    )
    suspend fun listDueByStatus(
        status: String,
        nowEpochMillis: Long,
        limit: Int,
    ): List<PendingMessageOutboxEntity>

    @Query(
        """
        SELECT MIN(nextAttemptAtEpochMillis) FROM pending_message_outbox
        WHERE status = :status
        """
    )
    suspend fun getNextAttemptAt(status: String): Long?

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
        SET
            status = :status,
            errorMessage = :errorMessage,
            lastAttemptAtEpochMillis = :lastAttemptAtEpochMillis
        WHERE localMessageId = :localMessageId
        """
    )
    suspend fun updateStatus(
        localMessageId: Int,
        status: String,
        errorMessage: String?,
        lastAttemptAtEpochMillis: Long?,
    )

    @Query(
        """
        UPDATE pending_message_outbox
        SET
            status = :status,
            errorMessage = :errorMessage,
            attemptCount = :attemptCount,
            lastAttemptAtEpochMillis = :lastAttemptAtEpochMillis,
            nextAttemptAtEpochMillis = :nextAttemptAtEpochMillis
        WHERE localMessageId = :localMessageId
        """
    )
    suspend fun updateRetryState(
        localMessageId: Int,
        status: String,
        errorMessage: String?,
        attemptCount: Int,
        lastAttemptAtEpochMillis: Long?,
        nextAttemptAtEpochMillis: Long,
    )

    @Query(
        """
        UPDATE pending_message_outbox
        SET
            status = 'queued',
            errorMessage = NULL,
            attemptCount = 0,
            lastAttemptAtEpochMillis = NULL,
            nextAttemptAtEpochMillis = 0
        WHERE localMessageId = :localMessageId
        """
    )
    suspend fun resetForManualRetry(localMessageId: Int)

    @Query(
        """
        UPDATE pending_message_outbox
        SET status = 'queued', errorMessage = NULL
        WHERE conversationId = :conversationId
          AND status = 'sending'
        """
    )
    suspend fun requeueSendingForConversation(conversationId: Int)

    @Query(
        """
        UPDATE pending_message_outbox
        SET status = 'queued', errorMessage = NULL
        WHERE status = 'sending'
        """
    )
    suspend fun requeueAllSending()
}
