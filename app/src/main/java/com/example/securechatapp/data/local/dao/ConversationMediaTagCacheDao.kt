package com.example.securechatapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.securechatapp.data.local.entity.ConversationMediaTagCacheEntity

@Dao
interface ConversationMediaTagCacheDao {

    @Query(
        """
        SELECT * FROM conversation_media_tags_cache
        WHERE conversationId = :conversationId
        ORDER BY name COLLATE NOCASE ASC, tagId ASC
        """
    )
    suspend fun listByConversation(conversationId: Int): List<ConversationMediaTagCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ConversationMediaTagCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ConversationMediaTagCacheEntity)

    @Query("DELETE FROM conversation_media_tags_cache WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: Int)

    @Query("DELETE FROM conversation_media_tags_cache WHERE tagId = :tagId")
    suspend fun deleteByTagId(tagId: Int)
}
