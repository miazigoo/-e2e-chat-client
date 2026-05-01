package com.example.securechatapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "decrypted_message_payload_cache",
    indices = [
        Index(value = ["messageUuid"]),
        Index(value = ["updatedAt"]),
    ],
)
data class DecryptedMessagePayloadCacheEntity(
    @PrimaryKey
    val messageId: Int,
    val messageUuid: String? = null,
    val text: String,
    val attachmentsJson: String = "[]",
    val updatedAt: String,
)
