package com.example.securechatapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.securechatapp.data.local.entity.MessageCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageCacheDao {

    @Query(
        """
        SELECT * FROM messages_cache
        WHERE conversationId = :conversationId
        ORDER BY messageId ASC
        """
    )
    fun observeForConversation(conversationId: Int): Flow<List<MessageCacheEntity>>

    @Query(
        """
        SELECT * FROM messages_cache
        WHERE conversationId = :conversationId
        ORDER BY messageId ASC
        """
    )
    suspend fun listForConversation(conversationId: Int): List<MessageCacheEntity>

    @Upsert
    suspend fun upsertAll(items: List<MessageCacheEntity>)

    @Query(
        """
        DELETE FROM messages_cache
        WHERE conversationId = :conversationId
        """
    )
    suspend fun deleteByConversationId(conversationId: Int)

    @Query(
        """
        UPDATE messages_cache
        SET deliveredAt = CASE
            WHEN deliveredAt IS NULL THEN :deliveredAt
            ELSE deliveredAt
        END
        WHERE messageId = :messageId
        """
    )
    suspend fun markDelivered(
        messageId: Int,
        deliveredAt: String,
    )

    @Query(
        """
        UPDATE messages_cache
        SET
            deliveredAt = CASE
                WHEN deliveredAt IS NULL THEN :readAt
                ELSE deliveredAt
            END,
            readAt = CASE
                WHEN readAt IS NULL THEN :readAt
                ELSE readAt
            END
        WHERE messageId = :messageId
        """
    )
    suspend fun markRead(
        messageId: Int,
        readAt: String,
    )
}
