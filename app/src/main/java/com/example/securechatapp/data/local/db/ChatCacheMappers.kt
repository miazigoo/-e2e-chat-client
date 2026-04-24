package com.example.securechatapp.data.local.db

import com.example.securechatapp.data.local.entity.ConversationCacheEntity
import com.example.securechatapp.data.local.entity.MessageCacheEntity
import com.example.securechatapp.domain.model.AttachmentItem
import com.example.securechatapp.domain.model.ChatMessage
import com.example.securechatapp.domain.model.ConversationDetails
import com.example.securechatapp.domain.model.ConversationListItem
import com.example.securechatapp.domain.model.MessageSendStatus
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

fun ConversationCacheEntity.toConversationListItem(): ConversationListItem {
    return ConversationListItem(
        conversationId = conversationId,
        conversationUuid = conversationUuid,
        title = title,
        peerUserId = peerUserId,
        peerNickname = peerNickname,
        unreadCount = unreadCount,
        lastMessagePreview = lastMessagePreview,
        updatedAt = updatedAt,
        sharedSecretEnabled = sharedSecretEnabled,
        sharedSecretFingerprint = sharedSecretFingerprint,
        peerSharedSecretEnabled = peerSharedSecretEnabled,
    )
}

fun ConversationCacheEntity.toConversationDetails(): ConversationDetails {
    return ConversationDetails(
        conversationId = conversationId,
        conversationUuid = conversationUuid,
        title = title,
        peerUserId = peerUserId,
        protectionMode = protectionMode ?: "normal",
        sharedSecretEnabled = sharedSecretEnabled,
        sharedSecretFingerprint = sharedSecretFingerprint,
        peerSharedSecretEnabled = peerSharedSecretEnabled,
    )
}

fun ConversationListItem.toEntity(previous: ConversationCacheEntity?): ConversationCacheEntity {
    return ConversationCacheEntity(
        conversationId = conversationId,
        conversationUuid = conversationUuid,
        title = title,
        peerUserId = peerUserId,
        peerNickname = peerNickname,
        unreadCount = unreadCount,
        lastMessagePreview = lastMessagePreview,
        updatedAt = updatedAt,
        protectionMode = previous?.protectionMode,
        lastEventId = previous?.lastEventId,
        sharedSecretEnabled = sharedSecretEnabled,
        sharedSecretFingerprint = sharedSecretFingerprint,
        peerSharedSecretEnabled = peerSharedSecretEnabled,
    )
}

fun ConversationDetails.toEntity(previous: ConversationCacheEntity?): ConversationCacheEntity {
    return ConversationCacheEntity(
        conversationId = conversationId,
        conversationUuid = conversationUuid,
        title = title,
        peerUserId = peerUserId,
        peerNickname = previous?.peerNickname.orEmpty(),
        unreadCount = previous?.unreadCount ?: 0,
        lastMessagePreview = previous?.lastMessagePreview ?: "Нет сообщений",
        updatedAt = previous?.updatedAt,
        protectionMode = protectionMode,
        lastEventId = previous?.lastEventId,
        sharedSecretEnabled = sharedSecretEnabled,
        sharedSecretFingerprint = sharedSecretFingerprint,
        peerSharedSecretEnabled = peerSharedSecretEnabled,
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
