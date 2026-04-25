package com.example.securechatapp.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class UserSearchItem(
    val userId: Int,
    val nickname: String,
)

@Serializable
data class ConversationListItem(
    val conversationId: Int,
    val conversationUuid: String,
    val title: String,
    val isSavedMessages: Boolean = false,
    val peerUserId: Int,
    val peerNickname: String,
    val unreadCount: Int,
    val lastMessagePreview: String,
    val protectionMode: String = "normal",
    val messageTtlDays: Int? = null,
    val deleteAfterReadSeconds: Int? = null,
    val isActive: Boolean = true,
    val isPurged: Boolean = false,
    val updatedAt: String? = null,
    val sharedSecretEnabled: Boolean = false,
    val sharedSecretFingerprint: String? = null,
    val peerSharedSecretEnabled: Boolean = false,
    val pinnedMessage: MessagePreview? = null,
)

@Serializable
data class ConversationDetails(
    val conversationId: Int,
    val conversationUuid: String,
    val title: String,
    val isSavedMessages: Boolean = false,
    val peerUserId: Int,
    val protectionMode: String,
    val messageTtlDays: Int? = null,
    val deleteAfterReadSeconds: Int? = null,
    val sharedSecretEnabled: Boolean = false,
    val sharedSecretFingerprint: String? = null,
    val peerSharedSecretEnabled: Boolean = false,
    val isActive: Boolean = true,
    val isPurged: Boolean = false,
    val pinnedMessage: MessagePreview? = null,
)

@Serializable
enum class MessageSendStatus {
    SENT,
    SENDING,
    FAILED,
}

@Serializable
data class MessageReactionSummary(
    val reaction: String,
    val count: Int,
    val me: Boolean = false,
)

@Serializable
data class ChatMessage(
    val messageId: Int,
    val messageUuid: String? = null,
    val text: String,
    val isMine: Boolean,
    val createdAt: String,
    val clientCreatedAt: String? = null,
    val deliveredAt: String? = null,
    val readAt: String? = null,
    val expiresAt: String? = null,
    val messageType: String = "text",
    val hasAttachments: Boolean = false,
    val attachmentIds: List<Int> = emptyList(),
    val attachments: List<AttachmentItem> = emptyList(),
    val sendStatus: MessageSendStatus = MessageSendStatus.SENT,
    val errorMessage: String? = null,
    val reactions: List<MessageReactionSummary> = emptyList(),
    val replyToMessageId: Int? = null,
    val forwardFromMessageId: Int? = null,
    val replyPreview: MessagePreview? = null,
    val forwardPreview: MessagePreview? = null,
)

@Serializable
data class MessagePreview(
    val messageId: Int,
    val messageUuid: String,
    val senderUserId: Int,
    val messageType: String,
    val text: String,
    val hasAttachments: Boolean,
    val clientCreatedAt: String,
)

@Serializable
data class AttachmentItem(
    val attachmentId: Int,
    val fileName: String,
    val mimeType: String?,
    val fileSize: Long,
    val canDownload: Boolean = true,
    val blobKeyBase64: String? = null,
    val blobNonceBase64: String? = null,
    val sha256EncryptedBlob: String? = null,
) {
    val isImage: Boolean
        get() = mimeType?.startsWith("image/") == true

    val hasEncryptedBlobKeys: Boolean
        get() = !blobKeyBase64.isNullOrBlank() && !blobNonceBase64.isNullOrBlank()
}

data class AttachmentDownloadInfo(
    val attachmentId: Int,
    val downloadUrl: String,
    val fileName: String,
    val mimeType: String?,
)

@Serializable
data class SharedTabCounts(
    val media: Int = 0,
    val links: Int = 0,
    val files: Int = 0,
)

@Serializable
data class SharedMessagesPage(
    val conversationId: Int,
    val tab: String,
    val counts: SharedTabCounts,
    val items: List<ChatMessage> = emptyList(),
)
