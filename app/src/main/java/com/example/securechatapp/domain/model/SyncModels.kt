package com.example.securechatapp.domain.model

import kotlinx.serialization.json.JsonObject

data class ConversationSyncEvent(
    val eventId: Int,
    val eventUuid: String,
    val eventType: String,
    val actorUserId: Int? = null,
    val actorDeviceId: Int? = null,
    val targetMessageId: Int? = null,
    val payload: JsonObject? = null,
    val createdAt: String,
)

data class ConversationEventsPage(
    val conversationId: Int,
    val items: List<ConversationSyncEvent> = emptyList(),
    val nextAfterEventId: Int? = null,
    val hasMore: Boolean,
)

object ConversationEventTypes {
    const val MESSAGE_CREATED = "message_created"
    const val MESSAGE_DELETED_GLOBAL = "message_deleted_global"
    const val MESSAGE_HIDDEN_FOR_USER = "message_hidden_for_user"
    const val CONVERSATION_CLEARED_LOCAL = "conversation_cleared_local"
    const val CONVERSATION_CLEARED_GLOBAL = "conversation_cleared_global"
    const val MESSAGE_DELIVERED = "message_delivered"
    const val MESSAGE_READ = "message_read"
    const val FILE_UPLOADED = "file_uploaded"
    const val FILE_DELETED = "file_deleted"
    const val PARTICIPANT_KEY_CHANGED = "participant_key_changed"
    const val CONVERSATION_PURGED = "conversation_purged"
}
