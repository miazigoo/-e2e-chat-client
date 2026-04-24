package com.example.securechatapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_message_outbox",
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["status"]),
        Index(value = ["conversationId", "status"]),
        Index(value = ["status", "nextAttemptAtEpochMillis"]),
        Index(value = ["clientMessageUuid"], unique = true),
    ],
)
data class PendingMessageOutboxEntity(
    @PrimaryKey
    val localMessageId: Int,
    val conversationId: Int,
    val recipientUserId: Int,
    val clientMessageUuid: String,
    val plainText: String,
    val attachmentIdsCsv: String = "",
    val attachmentDescriptorsJson: String = "[]",
    val attachmentPreviewJson: String = "[]",
    val hasAttachments: Boolean = false,
    val createdAt: String,
    val status: String,
    val errorMessage: String? = null,
    val attemptCount: Int = 0,
    val lastAttemptAtEpochMillis: Long? = null,
    val nextAttemptAtEpochMillis: Long = 0L,
)
