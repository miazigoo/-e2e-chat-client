package com.example.securechatapp.data.background

import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ConversationSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            BackgroundWorkerEntryPoint::class.java,
        )

        val session = dependencies.sessionLocalDataSource().getSessionSnapshot()
        if (session?.accessToken.isNullOrBlank()) {
            return Result.success()
        }

        return runCatching {
            val conversationRepository = dependencies.conversationRepository()
            val messageRepository = dependencies.messageRepository()
            val cacheRepository = dependencies.chatCacheRepository()

            val conversations = conversationRepository.listConversations()
            cacheRepository.replaceConversations(conversations)

            conversations.take(MAX_CONVERSATIONS_TO_REFRESH).forEach { conversation ->
                val details = conversationRepository.getConversation(conversation.conversationId)
                cacheRepository.upsertConversationDetails(details)

                val messages = messageRepository.listMessages(
                    conversationId = conversation.conversationId,
                    peerUserId = conversation.peerUserId,
                )
                cacheRepository.replaceMessages(
                    conversationId = conversation.conversationId,
                    messages = messages,
                )
                cacheRepository.updateConversationSnapshotFromMessages(
                    conversationId = conversation.conversationId,
                    messages = messages,
                )
            }
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "secure_chat_conversation_sync"
        private const val MAX_CONVERSATIONS_TO_REFRESH = 20
    }
}
