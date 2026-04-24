package com.example.securechatapp.data.background

import com.example.securechatapp.data.local.preferences.SecureSessionLocalDataSource
import com.example.securechatapp.data.repository.ChatCacheRepository
import com.example.securechatapp.data.repository.ConversationRepository
import com.example.securechatapp.data.repository.MessageRepository
import com.example.securechatapp.data.repository.OutboxDispatcher
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackgroundWorkerEntryPoint {
    fun sessionLocalDataSource(): SecureSessionLocalDataSource
    fun outboxDispatcher(): OutboxDispatcher
    fun conversationRepository(): ConversationRepository
    fun messageRepository(): MessageRepository
    fun chatCacheRepository(): ChatCacheRepository
}
