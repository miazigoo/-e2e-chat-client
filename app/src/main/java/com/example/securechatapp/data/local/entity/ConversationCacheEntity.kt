package com.example.securechatapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations_cache")
data class ConversationCacheEntity(
    @PrimaryKey
    val conversationId: Int,
    val conversationUuid: String = "",
    val title: String,
    val isSavedMessages: Boolean = false,
    val peerUserId: Int,
    val peerNickname: String,
    val unreadCount: Int,
    val lastMessagePreview: String,
    val updatedAt: String? = null,
    val protectionMode: String? = null,
    val messageTtlDays: Int? = null,
    val deleteAfterReadSeconds: Int? = null,
    val isActive: Boolean = true,
    val isPurged: Boolean = false,
    val isPinned: Boolean = false,
    val lastEventId: Int? = null,
    val sharedSecretEnabled: Boolean = false,
    val sharedSecretFingerprint: String? = null,
    val peerSharedSecretEnabled: Boolean = false,
    val pinnedMessageJson: String? = null,
)
