package com.example.securechatapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversation_media_tags_cache",
    indices = [
        Index(value = ["conversationId"]),
    ],
)
data class ConversationMediaTagCacheEntity(
    @PrimaryKey
    val tagId: Int,
    val conversationId: Int,
    val name: String,
    val color: String? = null,
    val createdByUserId: Int? = null,
    val createdAt: String,
    val updatedAt: String,
)
