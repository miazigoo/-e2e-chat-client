package com.example.securechatapp.ui.screens.conversation

import com.example.securechatapp.domain.model.ChatMessage

sealed interface ConversationRow {
    val key: String

    data class DateSeparator(
        val label: String,
    ) : ConversationRow {
        override val key: String = "date_$label"
    }

    data class MessageItem(
        val message: ChatMessage,
        val groupPosition: MessageGroupPosition,
    ) : ConversationRow {
        override val key: String = "msg_${message.messageId}"
    }
}

enum class MessageGroupPosition {
    SINGLE,
    START,
    MIDDLE,
    END,
}
