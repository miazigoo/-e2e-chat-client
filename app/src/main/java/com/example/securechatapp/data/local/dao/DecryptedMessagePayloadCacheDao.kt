package com.example.securechatapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.securechatapp.data.local.entity.DecryptedMessagePayloadCacheEntity

@Dao
interface DecryptedMessagePayloadCacheDao {

    @Query(
        """
        SELECT * FROM decrypted_message_payload_cache
        WHERE messageId IN (:messageIds)
        """
    )
    suspend fun listByMessageIds(
        messageIds: List<Int>,
    ): List<DecryptedMessagePayloadCacheEntity>

    @Upsert
    suspend fun upsertAll(
        items: List<DecryptedMessagePayloadCacheEntity>,
    )
}
