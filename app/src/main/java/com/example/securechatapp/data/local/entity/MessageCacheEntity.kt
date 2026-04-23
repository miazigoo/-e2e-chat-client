package com.example.securechatapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages_cache",
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["conversationId", "messageId"]),
        Index(value = ["messageUuid"]),
    ],
)
data class MessageCacheEntity(
    @PrimaryKey
    val messageId: Int,
    val conversationId: Int,
    val messageUuid: String? = null,
    val text: String,
    val isMine: Boolean,
    val createdAt: String,
    val deliveredAt: String? = null,
    val readAt: String? = null,
    val hasAttachments: Boolean = false,
    val attachmentIdsCsv: String = "",
    val attachmentsJson: String = "[]",
)
