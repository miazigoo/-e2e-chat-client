package com.example.securechatapp.data.local.db

import com.example.securechatapp.data.local.entity.ConversationCacheEntity
import com.example.securechatapp.data.local.entity.MessageCacheEntity
import com.example.securechatapp.domain.model.AttachmentItem
import com.example.securechatapp.domain.model.ChatMessage
import com.example.securechatapp.domain.model.ConversationDetails
import com.example.securechatapp.domain.model.ConversationListItem
import com.example.securechatapp.domain.model.MessagePreview
import com.example.securechatapp.domain.model.MessageReactionSummary
import com.example.securechatapp.domain.model.MessageSendStatus
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

fun ConversationCacheEntity.toConversationListItem(): ConversationListItem {
    return ConversationListItem(
        conversationId = conversationId,
        conversationUuid = conversationUuid,
        title = title,
        isSavedMessages = isSavedMessages,
        peerUserId = peerUserId,
        peerNickname = peerNickname,
        unreadCount = unreadCount,
        lastMessagePreview = lastMessagePreview,
        protectionMode = protectionMode ?: "normal",
        messageTtlDays = messageTtlDays,
        deleteAfterReadSeconds = deleteAfterReadSeconds,
        isActive = isActive,
        isPurged = isPurged,
        updatedAt = updatedAt,
        sharedSecretEnabled = sharedSecretEnabled,
        sharedSecretFingerprint = sharedSecretFingerprint,
        peerSharedSecretEnabled = peerSharedSecretEnabled,
        pinnedMessage = decodeMessagePreviewJson(pinnedMessageJson),
    )
}

fun ConversationCacheEntity.toConversationDetails(): ConversationDetails {
    return ConversationDetails(
        conversationId = conversationId,
        conversationUuid = conversationUuid,
        title = title,
        isSavedMessages = isSavedMessages,
        peerUserId = peerUserId,
        protectionMode = protectionMode ?: "normal",
        messageTtlDays = messageTtlDays,
        deleteAfterReadSeconds = deleteAfterReadSeconds,
        sharedSecretEnabled = sharedSecretEnabled,
        sharedSecretFingerprint = sharedSecretFingerprint,
        peerSharedSecretEnabled = peerSharedSecretEnabled,
        isActive = isActive,
        isPurged = isPurged,
        pinnedMessage = decodeMessagePreviewJson(pinnedMessageJson),
    )
}

fun ConversationListItem.toEntity(previous: ConversationCacheEntity?): ConversationCacheEntity {
    return ConversationCacheEntity(
        conversationId = conversationId,
        conversationUuid = conversationUuid,
        title = title,
        isSavedMessages = isSavedMessages,
        peerUserId = peerUserId,
        peerNickname = peerNickname,
        unreadCount = unreadCount,
        lastMessagePreview = lastMessagePreview,
        updatedAt = updatedAt,
        protectionMode = protectionMode,
        messageTtlDays = messageTtlDays,
        deleteAfterReadSeconds = deleteAfterReadSeconds,
        isActive = isActive,
        isPurged = isPurged,
        lastEventId = previous?.lastEventId,
        sharedSecretEnabled = sharedSecretEnabled,
        sharedSecretFingerprint = sharedSecretFingerprint,
        peerSharedSecretEnabled = peerSharedSecretEnabled,
        pinnedMessageJson = encodeMessagePreviewJson(pinnedMessage),
    )
}

fun ConversationDetails.toEntity(previous: ConversationCacheEntity?): ConversationCacheEntity {
    return ConversationCacheEntity(
        conversationId = conversationId,
        conversationUuid = conversationUuid,
        title = title,
        isSavedMessages = isSavedMessages,
        peerUserId = peerUserId,
        peerNickname = previous?.peerNickname.orEmpty(),
        unreadCount = previous?.unreadCount ?: 0,
        lastMessagePreview = previous?.lastMessagePreview ?: "Нет сообщений",
        updatedAt = previous?.updatedAt,
        protectionMode = protectionMode,
        messageTtlDays = messageTtlDays,
        deleteAfterReadSeconds = deleteAfterReadSeconds,
        isActive = isActive,
        isPurged = isPurged,
        lastEventId = previous?.lastEventId,
        sharedSecretEnabled = sharedSecretEnabled,
        sharedSecretFingerprint = sharedSecretFingerprint,
        peerSharedSecretEnabled = peerSharedSecretEnabled,
        pinnedMessageJson = encodeMessagePreviewJson(pinnedMessage),
    )
}

fun MessageCacheEntity.toDomain(
    json: Json,
): ChatMessage {
    return ChatMessage(
        messageId = messageId,
        messageUuid = messageUuid,
        text = text,
        isMine = isMine,
        createdAt = createdAt,
        clientCreatedAt = createdAt,
        deliveredAt = deliveredAt,
        readAt = readAt,
        hasAttachments = hasAttachments,
        attachmentIds = attachmentIdsCsv
            .split(",")
            .mapNotNull { it.trim().takeIf(String::isNotBlank)?.toIntOrNull() },
        attachments = decodeAttachmentsJson(
            json = json,
            raw = attachmentsJson,
        ),
        sendStatus = MessageSendStatus.SENT,
        errorMessage = null,
        reactions = decodeReactionsJson(
            json = json,
            raw = reactionsJson,
        ),
    )
}

fun ChatMessage.toEntity(
    conversationId: Int,
    json: Json,
): MessageCacheEntity {
    return MessageCacheEntity(
        messageId = messageId,
        conversationId = conversationId,
        messageUuid = messageUuid,
        text = text,
        isMine = isMine,
        createdAt = createdAt,
        deliveredAt = deliveredAt,
        readAt = readAt,
        hasAttachments = hasAttachments,
        attachmentIdsCsv = attachmentIds.joinToString(","),
        attachmentsJson = encodeAttachmentsJson(
            json = json,
            attachments = attachments,
        ),
        reactionsJson = encodeReactionsJson(
            json = json,
            reactions = reactions,
        ),
    )
}

fun encodeAttachmentsJson(
    json: Json,
    attachments: List<AttachmentItem>,
): String {
    return json.encodeToString(
        ListSerializer(AttachmentItem.serializer()),
        attachments,
    )
}

fun decodeAttachmentsJson(
    json: Json,
    raw: String,
): List<AttachmentItem> {
    return runCatching {
        json.decodeFromString(
            ListSerializer(AttachmentItem.serializer()),
            raw,
        )
    }.getOrDefault(emptyList())
}


fun encodeReactionsJson(
    json: Json,
    reactions: List<MessageReactionSummary>,
): String {
    return json.encodeToString(
        ListSerializer(MessageReactionSummary.serializer()),
        reactions,
    )
}

fun decodeReactionsJson(
    json: Json,
    raw: String,
): List<MessageReactionSummary> {
    return runCatching {
        json.decodeFromString(
            ListSerializer(MessageReactionSummary.serializer()),
            raw,
        )
    }.getOrDefault(emptyList())
}

private fun encodeMessagePreviewJson(
    preview: MessagePreview?,
): String? {
    return preview?.let { Json.encodeToString(MessagePreview.serializer(), it) }
}

private fun decodeMessagePreviewJson(
    raw: String?,
): MessagePreview? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        Json.decodeFromString(MessagePreview.serializer(), raw)
    }.getOrNull()
}
