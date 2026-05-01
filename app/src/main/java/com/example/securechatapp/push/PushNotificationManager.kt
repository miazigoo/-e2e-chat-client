package com.example.securechatapp.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.AudioAttributes
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.example.securechatapp.MainActivity
import com.example.securechatapp.R
import com.example.securechatapp.data.local.preferences.NotificationPreferenceDataSource
import com.example.securechatapp.data.repository.ConversationRepository
import com.example.securechatapp.data.repository.MessageRepository
import com.example.securechatapp.ui.navigation.Routes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

@Singleton
class PushNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val notificationPreferenceDataSource: NotificationPreferenceDataSource,
    private val visibleConversationTracker: VisibleConversationTracker,
) {
    suspend fun handle(payload: PushPayload) {
        when (payload) {
            is PushPayload.NewMessage -> showMessageNotification(payload)
            is PushPayload.DeviceApprovalRequested -> showDeviceApprovalNotification(payload)
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
        if (visibleConversationTracker.isViewingConversation(payload.conversationId)) {
            cancel(buildConversationNotificationId(payload.conversationId))
            return
        }

        val soundKey = notificationPreferenceDataSource.getMessageNotificationSoundKey()
        val customSoundUri = notificationPreferenceDataSource.getMessageNotificationCustomSoundUri()
        val vibrationEnabled = notificationPreferenceDataSource.isMessageNotificationVibrationEnabled()
        val channelId = ensureMessageChannel(
            soundKey = soundKey,
            customSoundUri = customSoundUri,
            vibrationEnabled = vibrationEnabled,
        )
        val soundUri = NotificationSoundCatalog.resolveSoundUri(context, soundKey, customSoundUri)

        val conversation = runCatching {
            conversationRepository.getConversation(payload.conversationId)
        }.getOrNull() ?: return

        val message = awaitNotificationMessage(
            payload = payload,
            peerUserId = conversation.peerUserId,
        ) ?: return
        if (message.isMine) return

        val title = conversation.title
        val body = buildNotificationBody(message)

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

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_secure_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
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

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            notificationBuilder.setSound(soundUri)
            notificationBuilder.setVibrate(
                if (vibrationEnabled) LIGHT_VIBRATION_PATTERN else longArrayOf(0L)
            )
            notificationBuilder.setDefaults(Notification.DEFAULT_LIGHTS)
        }

        val notification = notificationBuilder.build()

        notifySafely(notificationId, notification)
    }

    private suspend fun showAppUpdateNotification(payload: PushPayload.AppUpdateAvailable) {
        if (!notificationPreferenceDataSource.isApkUpdateNotificationsEnabled()) return
        if (!canPostNotifications()) return
        ensureUpdatesChannel()

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

        notifySafely(NOTIFICATION_ID_APP_UPDATES, notification)
    }

    private suspend fun showDeviceApprovalNotification(
        payload: PushPayload.DeviceApprovalRequested,
    ) {
        if (!notificationPreferenceDataSource.isPushNotificationsEnabled()) return
        if (!canPostNotifications()) return

        val soundKey = notificationPreferenceDataSource.getMessageNotificationSoundKey()
        val customSoundUri = notificationPreferenceDataSource.getMessageNotificationCustomSoundUri()
        val vibrationEnabled = notificationPreferenceDataSource.isMessageNotificationVibrationEnabled()
        val channelId = ensureMessageChannel(
            soundKey = soundKey,
            customSoundUri = customSoundUri,
            vibrationEnabled = vibrationEnabled,
        )
        val soundUri = NotificationSoundCatalog.resolveSoundUri(context, soundKey, customSoundUri)

        val body = buildDeviceApprovalBody(payload)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationIntents.EXTRA_OPEN_ROUTE, Routes.Settings)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_DEVICE_APPROVAL_BASE + payload.requestId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_secure_chat)
            .setContentTitle("Новое устройство запрашивает доступ")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(soundUri)
            builder.setVibrate(
                if (vibrationEnabled) LIGHT_VIBRATION_PATTERN else longArrayOf(0L)
            )
            builder.setDefaults(Notification.DEFAULT_LIGHTS)
        }

        notifySafely(
            NOTIFICATION_ID_DEVICE_APPROVAL_BASE + payload.requestId.hashCode(),
            builder.build(),
        )
    }

    private fun ensureUpdatesChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val updatesChannel = NotificationChannel(
            CHANNEL_UPDATES,
            "Обновления",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Уведомления о новых версиях приложения"
        }

        manager.createNotificationChannel(updatesChannel)
    }

    private fun ensureMessageChannel(
        soundKey: String,
        customSoundUri: String?,
        vibrationEnabled: Boolean,
    ): String {
        val channelId = buildMessageChannelId(soundKey, customSoundUri, vibrationEnabled)
        val soundUri = NotificationSoundCatalog.resolveSoundUri(context, soundKey, customSoundUri)
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            channelId,
            "Сообщения",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Уведомления о новых сообщениях и быстрые действия"
            enableVibration(vibrationEnabled)
            vibrationPattern = if (vibrationEnabled) {
                LIGHT_VIBRATION_PATTERN
            } else {
                longArrayOf(0L)
            }
            setSound(
                soundUri,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        manager.createNotificationChannel(channel)
        return channelId
    }

    private suspend fun awaitNotificationMessage(
        payload: PushPayload.NewMessage,
        peerUserId: Int,
    ): com.example.securechatapp.domain.model.ChatMessage? {
        repeat(MESSAGE_LOOKUP_ATTEMPTS) { attempt ->
            val messages = runCatching {
                messageRepository.listMessages(
                    conversationId = payload.conversationId,
                    peerUserId = peerUserId,
                )
            }.getOrNull()

            val message = messages?.lastOrNull { it.messageId == payload.messageId }
            if (message != null) {
                return message
            }

            if (attempt < MESSAGE_LOOKUP_ATTEMPTS - 1) {
                delay(MESSAGE_LOOKUP_RETRY_DELAY_MS)
            }
        }

        return null
    }

    private fun buildNotificationBody(
        message: com.example.securechatapp.domain.model.ChatMessage,
    ): String {
        if (message.text.isNotBlank() && message.text != "[attachment]") {
            return message.text
        }

        val attachments = message.attachments
        if (attachments.isEmpty()) {
            return if (message.hasAttachments) "Вложение" else "Новое защищённое сообщение"
        }

        return when (attachments.size) {
            1 -> "📎 ${attachments.first().fileName}"
            else -> "📎 ${attachments.first().fileName} +${attachments.size - 1}"
        }
    }

    private fun buildDeviceApprovalBody(
        payload: PushPayload.DeviceApprovalRequested,
    ): String {
        val primary = payload.deviceName?.takeIf { it.isNotBlank() }
            ?: "Неизвестное устройство"
        val secondary = listOfNotNull(
            payload.platform?.takeIf { it.isNotBlank() },
            payload.appVersion?.takeIf { it.isNotBlank() },
        ).joinToString(" • ")

        return buildString {
            append(primary)
            if (secondary.isNotBlank()) {
                append(" (")
                append(secondary)
                append(")")
            }
            append(". Откройте настройки и подтвердите вход.")
        }
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun notifySafely(notificationId: Int, notification: android.app.Notification) {
        if (!canPostNotifications()) return

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            return
        }
    }

    private fun buildConversationNotificationId(conversationId: Int): Int = 20_000 + conversationId

    private fun buildMessageChannelId(
        soundKey: String,
        customSoundUri: String?,
        vibrationEnabled: Boolean,
    ): String {
        val soundSuffix = when (soundKey) {
            NotificationSoundCatalog.SYSTEM_PICKED_KEY -> {
                val digest = (customSoundUri ?: "default").hashCode().toUInt().toString(16)
                "picked_$digest"
            }
            else -> soundKey
        }
        val vibrationSuffix = if (vibrationEnabled) "vibe" else "silentvibe"
        return "${CHANNEL_MESSAGES}_${soundSuffix}_${vibrationSuffix}"
    }

    private companion object {
        const val CHANNEL_MESSAGES = "secure_chat_messages"
        const val CHANNEL_UPDATES = "secure_chat_updates"
        const val NOTIFICATION_ID_APP_UPDATES = 10_001
        const val NOTIFICATION_ID_DEVICE_APPROVAL_BASE = 30_000
        val LIGHT_VIBRATION_PATTERN = longArrayOf(0L, 35L, 30L, 45L)
        const val MESSAGE_LOOKUP_ATTEMPTS = 5
        const val MESSAGE_LOOKUP_RETRY_DELAY_MS = 450L
    }
}
