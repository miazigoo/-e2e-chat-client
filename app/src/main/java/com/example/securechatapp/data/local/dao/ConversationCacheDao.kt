package com.example.securechatapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.securechatapp.data.local.entity.ConversationCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationCacheDao {

    @Query(
        """
        SELECT * FROM conversations_cache
        ORDER BY 
            isPinned DESC,
            CASE WHEN updatedAt IS NULL THEN 1 ELSE 0 END,
            updatedAt DESC,
            conversationId DESC
        """
    )
    fun observeAll(): Flow<List<ConversationCacheEntity>>

    @Query(
        """
        SELECT * FROM conversations_cache
        ORDER BY 
            isPinned DESC,
            CASE WHEN updatedAt IS NULL THEN 1 ELSE 0 END,
            updatedAt DESC,
            conversationId DESC
        """
    )
    suspend fun listAll(): List<ConversationCacheEntity>

    @Query(
        """
        SELECT * FROM conversations_cache
        WHERE conversationId = :conversationId
        LIMIT 1
        """
    )
    fun observeById(conversationId: Int): Flow<ConversationCacheEntity?>

    @Query(
        """
        SELECT * FROM conversations_cache
        WHERE conversationId = :conversationId
        LIMIT 1
        """
    )
    suspend fun getById(conversationId: Int): ConversationCacheEntity?

    @Query(
        """
        SELECT * FROM conversations_cache
        WHERE conversationId IN (:conversationIds)
        """
    )
    suspend fun getByIds(conversationIds: List<Int>): List<ConversationCacheEntity>

    @Upsert
    suspend fun upsertAll(items: List<ConversationCacheEntity>)

    @Upsert
    suspend fun upsert(item: ConversationCacheEntity)

    @Query("DELETE FROM conversations_cache")
    suspend fun clearAll()

    @Query(
        """
        DELETE FROM conversations_cache
        WHERE conversationId NOT IN (:conversationIds)
        """
    )
    suspend fun deleteAllExcept(conversationIds: List<Int>)

    @Query(
        """
        SELECT lastEventId FROM conversations_cache
        WHERE conversationId = :conversationId
        LIMIT 1
        """
    )
    suspend fun getLastEventId(conversationId: Int): Int?

    @Query(
        """
        UPDATE conversations_cache
        SET lastEventId = :lastEventId
        WHERE conversationId = :conversationId
        """
    )
    suspend fun updateLastEventId(
        conversationId: Int,
        lastEventId: Int?,
    )

    @Query(
        """
        UPDATE conversations_cache
        SET lastMessagePreview = :lastMessagePreview,
            updatedAt = :updatedAt
        WHERE conversationId = :conversationId
        """
    )
    suspend fun updateLastMessagePreview(
        conversationId: Int,
        lastMessagePreview: String,
        updatedAt: String?,
    )

    @Query(
        """
        UPDATE conversations_cache
        SET unreadCount = :unreadCount
        WHERE conversationId = :conversationId
        """
    )
    suspend fun updateUnreadCount(
        conversationId: Int,
        unreadCount: Int,
    )
}
