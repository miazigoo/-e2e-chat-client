package com.example.securechatapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations_cache")
data class ConversationCacheEntity(
    @PrimaryKey
    val conversationId: Int,
    val conversationUuid: String = "",
    val title: String,
    val peerUserId: Int,
    val peerNickname: String,
    val unreadCount: Int,
    val lastMessagePreview: String,
    val updatedAt: String? = null,
    val protectionMode: String? = null,
    val lastEventId: Int? = null,
    val sharedSecretEnabled: Boolean = false,
    val sharedSecretFingerprint: String? = null,
    val peerSharedSecretEnabled: Boolean = false,
)
