package com.example.securechatapp.domain.model

data class UserSearchItem(
    val userId: Int,
    val nickname: String,
)

data class ConversationListItem(
    val conversationId: Int,
    val title: String,
    val peerUserId: Int,
    val peerNickname: String,
    val unreadCount: Int,
    val lastMessagePreview: String,
    val updatedAt: String? = null,
)

data class ConversationDetails(
    val conversationId: Int,
    val title: String,
    val peerUserId: Int,
    val protectionMode: String,
)

data class ChatMessage(
    val messageId: Int,
    val text: String,
    val isMine: Boolean,
    val createdAt: String,
    val deliveredAt: String? = null,
    val readAt: String? = null,
    val hasAttachments: Boolean = false,
    val attachmentIds: List<Int> = emptyList(),
    val attachments: List<AttachmentItem> = emptyList(),
)

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
