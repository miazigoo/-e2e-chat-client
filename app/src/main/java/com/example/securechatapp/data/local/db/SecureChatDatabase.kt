package com.example.securechatapp.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.securechatapp.data.local.dao.ConversationCacheDao
import com.example.securechatapp.data.local.dao.MessageCacheDao
import com.example.securechatapp.data.local.dao.PendingMessageOutboxDao
import com.example.securechatapp.data.local.entity.ConversationCacheEntity
import com.example.securechatapp.data.local.entity.MessageCacheEntity
import com.example.securechatapp.data.local.entity.PendingMessageOutboxEntity

@Database(
    entities = [
        ConversationCacheEntity::class,
        MessageCacheEntity::class,
        PendingMessageOutboxEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class SecureChatDatabase : RoomDatabase() {
    abstract fun conversationCacheDao(): ConversationCacheDao
    abstract fun messageCacheDao(): MessageCacheDao
    abstract fun pendingMessageOutboxDao(): PendingMessageOutboxDao
}
