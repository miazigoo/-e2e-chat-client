package com.example.securechatapp.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.securechatapp.MainActivity
import com.example.securechatapp.R
import com.example.securechatapp.data.local.preferences.NotificationPreferenceDataSource
import com.example.securechatapp.data.repository.ConversationRepository
import com.example.securechatapp.data.repository.MessageRepository
import com.example.securechatapp.ui.navigation.Routes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val notificationPreferenceDataSource: NotificationPreferenceDataSource,
) {
    suspend fun handle(payload: PushPayload) {
        ensureChannels()
        when (payload) {
            is PushPayload.NewMessage -> showMessageNotification(payload)
            is PushPayload.AppUpdateAvailable -> showAppUpdateNotification(payload)
            is PushPayload.ConversationEvent -> Unit
        }
    }

    fun cancel(notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    private suspend fun showMessageNotification(payload: PushPayload.NewMessage) {
        if (!notificationPreferenceDataSource.isPushNotificationsEnabled()) return
        if (!canPostNotifications()) return
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            return
        }

        val conversation = runCatching {
            conversationRepository.getConversation(payload.conversationId)
        }.getOrNull() ?: return

        val messages = runCatching {
            messageRepository.listMessages(
                conversationId = payload.conversationId,
                peerUserId = conversation.peerUserId,
            )
        }.getOrNull() ?: return

        val message = messages.lastOrNull { it.messageId == payload.messageId } ?: return
        if (message.isMine) return

        val title = conversation.title
        val body = when {
            message.text.isNotBlank() -> message.text
            message.hasAttachments -> "Вложение"
            else -> "Новое защищённое сообщение"
        }

        val notificationId = buildConversationNotificationId(payload.conversationId)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationIntents.EXTRA_OPEN_CONVERSATION_ID, payload.conversationId)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationIntents.ACTION_REPLY
            putExtra(NotificationIntents.EXTRA_CONVERSATION_ID, payload.conversationId)
            putExtra(NotificationIntents.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val remoteInput = RemoteInput.Builder(NotificationIntents.REMOTE_INPUT_REPLY)
            .setLabel("Ответить")
            .build()

        val markReadIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationIntents.ACTION_MARK_READ
            putExtra(NotificationIntents.EXTRA_CONVERSATION_ID, payload.conversationId)
            putExtra(NotificationIntents.EXTRA_MESSAGE_ID, payload.messageId)
            putExtra(NotificationIntents.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 10_000,
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val person = Person.Builder().setName(title).build()
        val style = NotificationCompat.MessagingStyle(person)
            .setConversationTitle(title)
            .addMessage(body, System.currentTimeMillis(), person)

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_secure_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setShortcutId("conversation_${payload.conversationId}")
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_view,
                    "Прочитано",
                    markReadPendingIntent,
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_send,
                    "Ответить",
                    replyPendingIntent,
                ).addRemoteInput(remoteInput).build()
            )
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private suspend fun showAppUpdateNotification(payload: PushPayload.AppUpdateAvailable) {
        if (!notificationPreferenceDataSource.isApkUpdateNotificationsEnabled()) return
        if (!canPostNotifications()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationIntents.EXTRA_OPEN_ROUTE, Routes.Settings)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_APP_UPDATES,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val content = buildString {
            append("Доступна версия ${payload.versionName} (${payload.versionCode})")
            payload.changelog?.takeIf { it.isNotBlank() }?.let {
                append(". ")
                append(it)
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_secure_chat)
            .setContentTitle("Новое обновление Secure Chat")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_APP_UPDATES, notification)
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)
        val messageChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            "Сообщения",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Уведомления о новых сообщениях и быстрые действия"
        }
        val updatesChannel = NotificationChannel(
            CHANNEL_UPDATES,
            "Обновления",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Уведомления о новых версиях приложения"
        }

        manager.createNotificationChannel(messageChannel)
        manager.createNotificationChannel(updatesChannel)
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun buildConversationNotificationId(conversationId: Int): Int = 20_000 + conversationId

    private companion object {
        const val CHANNEL_MESSAGES = "secure_chat_messages"
        const val CHANNEL_UPDATES = "secure_chat_updates"
        const val NOTIFICATION_ID_APP_UPDATES = 10_001
    }
}
