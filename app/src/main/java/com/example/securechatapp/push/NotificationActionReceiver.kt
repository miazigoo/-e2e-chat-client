package com.example.securechatapp.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.example.securechatapp.data.repository.ConversationRepository
import com.example.securechatapp.data.repository.MessageRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var conversationRepository: ConversationRepository

    @Inject
    lateinit var messageRepository: MessageRepository

    @Inject
    lateinit var pushNotificationManager: PushNotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching {
                    when (intent.action) {
                        NotificationIntents.ACTION_REPLY -> handleReply(intent)
                        NotificationIntents.ACTION_MARK_READ -> handleMarkRead(intent)
                    }
                }
                intent.getIntExtra(NotificationIntents.EXTRA_NOTIFICATION_ID, -1)
                    .takeIf { it > 0 }
                    ?.let(pushNotificationManager::cancel)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleReply(intent: Intent) {
        val conversationId = intent.getIntExtra(NotificationIntents.EXTRA_CONVERSATION_ID, -1)
        if (conversationId <= 0) return

        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationIntents.REMOTE_INPUT_REPLY)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (replyText.isBlank()) return

        val conversation = conversationRepository.getConversation(conversationId)
        messageRepository.sendMessage(
            conversationId = conversationId,
            recipientUserId = conversation.peerUserId,
            plainText = replyText,
        )
    }

    private suspend fun handleMarkRead(intent: Intent) {
        val conversationId = intent.getIntExtra(NotificationIntents.EXTRA_CONVERSATION_ID, -1)
        if (conversationId <= 0) return

        val conversation = conversationRepository.getConversation(conversationId)
        val messages = messageRepository.listMessages(
            conversationId = conversationId,
            peerUserId = conversation.peerUserId,
        )
        messageRepository.markIncomingMessagesAsRead(messages)
    }
}
