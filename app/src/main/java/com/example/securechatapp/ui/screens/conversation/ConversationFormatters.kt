package com.example.securechatapp.ui.screens.conversation

import com.example.securechatapp.data.remote.websocket.RealtimeConnectionState
import com.example.securechatapp.domain.model.ChatMessage
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun buildConversationRows(
    messages: List<ChatMessage>,
): List<ConversationRow> {
    if (messages.isEmpty()) return emptyList()

    val result = mutableListOf<ConversationRow>()
    var previousDate: LocalDate? = null

    messages.forEachIndexed { index, message ->
        val currentDate = parseLocalDate(message.createdAt)

        if (currentDate != null && currentDate != previousDate) {
            result += ConversationRow.DateSeparator(formatDateSeparator(currentDate))
            previousDate = currentDate
        }

        val previousMessage = messages.getOrNull(index - 1)
        val nextMessage = messages.getOrNull(index + 1)

        val sameAsPrevious = previousMessage != null &&
                previousMessage.isMine == message.isMine &&
                parseLocalDate(previousMessage.createdAt) == currentDate

        val sameAsNext = nextMessage != null &&
                nextMessage.isMine == message.isMine &&
                parseLocalDate(nextMessage.createdAt) == currentDate

        val position = when {
            sameAsPrevious && sameAsNext -> MessageGroupPosition.MIDDLE
            sameAsPrevious -> MessageGroupPosition.END
            sameAsNext -> MessageGroupPosition.START
            else -> MessageGroupPosition.SINGLE
        }

        result += ConversationRow.MessageItem(
            message = message,
            groupPosition = position,
        )
    }

    return result
}

fun buildConversationSubtitle(
    messages: List<ChatMessage>,
    realtimeState: RealtimeConnectionState,
    isSyncing: Boolean,
): String {
    val activityText = if (messages.isEmpty()) {
        "начните защищённый диалог"
    } else {
        val last = messages.last()
        val lastDate = parseLocalDate(last.createdAt)
        val today = LocalDate.now()

        when (lastDate) {
            null -> "защищённый чат"
            today -> "был(а) недавно"
            today.minusDays(1) -> "был(а) вчера"
            else -> {
                val text = runCatching {
                    OffsetDateTime.parse(last.createdAt)
                        .format(DateTimeFormatter.ofPattern("d MMM", Locale("ru")))
                }.getOrDefault("")
                if (text.isBlank()) "защищённый чат" else "последняя активность $text"
            }
        }
    }

    val prefix = when {
        isSyncing -> "синхронизация"
        realtimeState == RealtimeConnectionState.RECONNECTING -> "переподключение"
        realtimeState == RealtimeConnectionState.CONNECTING -> "подключение"
        realtimeState == RealtimeConnectionState.DISCONNECTED -> "офлайн"
        else -> null
    }

    return listOfNotNull(prefix, activityText).joinToString(" · ")
}

fun realtimeBannerText(
    realtimeState: RealtimeConnectionState,
): String? {
    return when (realtimeState) {
        RealtimeConnectionState.CONNECTED -> null
        RealtimeConnectionState.CONNECTING -> "Подключаем realtime…"
        RealtimeConnectionState.RECONNECTING -> "Переподключаем realtime…"
        RealtimeConnectionState.DISCONNECTED -> "Realtime недоступен. История и отправка продолжат синхронизацию при восстановлении сети."
    }
}

fun realtimeStatusLabel(
    realtimeState: RealtimeConnectionState,
): String {
    return when (realtimeState) {
        RealtimeConnectionState.CONNECTED -> "secure connection"
        RealtimeConnectionState.CONNECTING -> "connecting…"
        RealtimeConnectionState.RECONNECTING -> "reconnecting…"
        RealtimeConnectionState.DISCONNECTED -> "offline mode"
    }
}

fun parseLocalDate(raw: String): LocalDate? {
    return runCatching {
        OffsetDateTime.parse(raw).toLocalDate()
    }.getOrNull()
}

fun formatDateSeparator(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Сегодня"
        today.minusDays(1) -> "Вчера"
        else -> date.format(
            DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))
        )
    }
}

fun formatMessageTime(raw: String): String {
    return runCatching {
        OffsetDateTime.parse(raw)
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault(raw)
}
