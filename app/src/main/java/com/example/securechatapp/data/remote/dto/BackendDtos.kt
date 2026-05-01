package com.example.securechatapp.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ApiMetaDto(
    @SerialName("request_id")
    val requestId: String? = null,
)

@Serializable
data class ApiErrorBodyDto(
    val code: String,
    val message: String,
)

@Serializable
data class ApiErrorEnvelopeDto(
    val ok: Boolean,
    val error: ApiErrorBodyDto,
    val meta: ApiMetaDto? = null,
)

@Serializable
data class ApiEnvelopeDto<T>(
    val ok: Boolean,
    val data: T,
    val meta: ApiMetaDto? = null,
)

@Serializable
data class RegisterRequestDto(
    val nickname: String,
    val password: String,
    val email: String? = null,
    @SerialName("email_2fa_enabled")
    val email2faEnabled: Boolean = false,
)

@Serializable
data class RegisterResponseDto(
    @SerialName("user_id")
    val userId: Int,
    val nickname: String,
    @SerialName("requires_device_registration")
    val requiresDeviceRegistration: Boolean,
    @SerialName("bootstrap_token")
    val bootstrapToken: String? = null,
    @SerialName("bootstrap_expires_in")
    val bootstrapExpiresIn: Int? = null,
)

@Serializable
data class LoginRequestDto(
    val nickname: String,
    val password: String,
    @SerialName("device_uuid")
    val deviceUuid: String? = null,
)

@Serializable
data class LoginResponseDto(
    @SerialName("requires_email_code")
    val requiresEmailCode: Boolean,
    @SerialName("requires_bootstrap")
    val requiresBootstrap: Boolean = false,
    @SerialName("login_challenge_id")
    val loginChallengeId: String? = null,
    @SerialName("email_masked")
    val emailMasked: String? = null,
    @SerialName("debug_code")
    val debugCode: String? = null,
    @SerialName("bootstrap_token")
    val bootstrapToken: String? = null,
    @SerialName("bootstrap_expires_in")
    val bootstrapExpiresIn: Int? = null,
    @SerialName("access_token")
    val accessToken: String? = null,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    @SerialName("expires_in")
    val expiresIn: Int? = null,
)

@Serializable
data class VerifyEmailCodeRequestDto(
    @SerialName("login_challenge_id")
    val loginChallengeId: String,
    val code: String,
    @SerialName("device_uuid")
    val deviceUuid: String? = null,
)

@Serializable
data class VerifyEmailCodeResponseDto(
    @SerialName("requires_bootstrap")
    val requiresBootstrap: Boolean = false,
    @SerialName("bootstrap_token")
    val bootstrapToken: String? = null,
    @SerialName("bootstrap_expires_in")
    val bootstrapExpiresIn: Int? = null,
    @SerialName("access_token")
    val accessToken: String? = null,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    @SerialName("expires_in")
    val expiresIn: Int? = null,
)

@Serializable
data class RefreshRequestDto(
    @SerialName("refresh_token")
    val refreshToken: String,
)

@Serializable
data class RefreshResponseDto(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("expires_in")
    val expiresIn: Int,
)

@Serializable
data class OneTimePreKeyDto(
    @SerialName("prekey_id")
    val prekeyId: Int,
    @SerialName("public_prekey")
    val publicPrekey: String,
)

@Serializable
data class BootstrapDeviceRequestDto(
    @SerialName("device_uuid")
    val deviceUuid: String,
    @SerialName("device_name")
    val deviceName: String,
    val platform: String,
    @SerialName("app_version")
    val appVersion: String,
    @SerialName("fcm_token")
    val fcmToken: String? = null,
    @SerialName("public_identity_key")
    val publicIdentityKey: String,
    @SerialName("public_signing_key")
    val publicSigningKey: String,
    @SerialName("signed_prekey")
    val signedPrekey: String,
    @SerialName("signed_prekey_signature")
    val signedPrekeySignature: String,
    @SerialName("one_time_prekeys")
    val oneTimePrekeys: List<OneTimePreKeyDto>,
)

@Serializable
data class BootstrapDeviceResponseDto(
    @SerialName("device_id")
    val deviceId: Int,
    @SerialName("device_uuid")
    val deviceUuid: String,
    @SerialName("is_active")
    val isActive: Boolean,
    @SerialName("prekeys_count")
    val prekeysCount: Int,
    @SerialName("last_seen_at")
    val lastSeenAt: String? = null,
)


@Serializable
data class UserSearchItemDto(
    @SerialName("user_id")
    val userId: Int,
    val nickname: String,
)

@Serializable
data class UserSearchResponseDto(
    val items: List<UserSearchItemDto> = emptyList(),
)

@Serializable
data class UserSafetyResponseDto(
    @SerialName("user_id")
    val userId: Int,
    val nickname: String,
    @SerialName("can_start_conversation")
    val canStartConversation: Boolean,
    @SerialName("is_deleted")
    val isDeleted: Boolean,
    @SerialName("pending_deletion")
    val pendingDeletion: Boolean,
    @SerialName("has_active_device")
    val hasActiveDevice: Boolean,
    @SerialName("supports_encrypted_chat")
    val supportsEncryptedChat: Boolean,
    @SerialName("safety_code_available")
    val safetyCodeAvailable: Boolean,
)

@Serializable
data class UserProfileSettingsDto(
    @SerialName("language_code")
    val languageCode: String,
    val theme: String,
    @SerialName("push_notifications_enabled")
    val pushNotificationsEnabled: Boolean,
    @SerialName("apk_update_notifications_enabled")
    val apkUpdateNotificationsEnabled: Boolean,
    @SerialName("google_2fa_enabled")
    val google2faEnabled: Boolean = false,
)

@Serializable
data class UserPublicProfileResponseDto(
    @SerialName("user_id")
    val userId: Int,
    @SerialName("public_id")
    val publicId: String,
    val nickname: String,
    @SerialName("full_name")
    val fullName: String? = null,
    val bio: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("avatar_updated_at")
    val avatarUpdatedAt: String? = null,
    @SerialName("created_at")
    val createdAt: String,
)

@Serializable
data class UserProfileResponseDto(
    @SerialName("user_id")
    val userId: Int,
    @SerialName("public_id")
    val publicId: String,
    val nickname: String,
    @SerialName("full_name")
    val fullName: String? = null,
    val bio: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("avatar_updated_at")
    val avatarUpdatedAt: String? = null,
    @SerialName("created_at")
    val createdAt: String,
    val settings: UserProfileSettingsDto,
)

@Serializable
data class UpdateUserProfileRequestDto(
    val nickname: String? = null,
    @SerialName("full_name")
    val fullName: String? = null,
    val bio: String? = null,
    @SerialName("language_code")
    val languageCode: String? = null,
    val theme: String? = null,
    @SerialName("push_notifications_enabled")
    val pushNotificationsEnabled: Boolean? = null,
    @SerialName("apk_update_notifications_enabled")
    val apkUpdateNotificationsEnabled: Boolean? = null,
)

@Serializable
data class AppReleaseDetailsDto(
    val platform: String,
    @SerialName("version_name")
    val versionName: String,
    @SerialName("version_code")
    val versionCode: Int,
    @SerialName("file_name")
    val fileName: String,
    @SerialName("file_size")
    val fileSize: Long,
    val sha256: String,
    val changelog: String? = null,
    @SerialName("content_type")
    val contentType: String,
    @SerialName("uploaded_at")
    val uploadedAt: String,
)

@Serializable
data class LatestAppReleaseResponseDto(
    val platform: String,
    @SerialName("version_name")
    val versionName: String,
    @SerialName("version_code")
    val versionCode: Int,
    @SerialName("file_name")
    val fileName: String,
    @SerialName("file_size")
    val fileSize: Long,
    val sha256: String,
    val changelog: String? = null,
    @SerialName("content_type")
    val contentType: String,
    @SerialName("uploaded_at")
    val uploadedAt: String,
    @SerialName("download_url")
    val downloadUrl: String,
    @SerialName("download_url_expires_in")
    val downloadUrlExpiresIn: Int,
)

@Serializable
data class AppVersionCheckResponseDto(
    @SerialName("current_version_code")
    val currentVersionCode: Int,
    @SerialName("latest_version_code")
    val latestVersionCode: Int,
    @SerialName("update_available")
    val updateAvailable: Boolean,
    val release: LatestAppReleaseResponseDto,
)

@Serializable
data class CreateConversationRequestDto(
    @SerialName("recipient_user_id")
    val recipientUserId: Int,
    val title: String? = null,
    @SerialName("protection_mode")
    val protectionMode: String = "normal",
    @SerialName("message_ttl_days")
    val messageTtlDays: Int? = 60,
    @SerialName("delete_after_read_seconds")
    val deleteAfterReadSeconds: Int? = null,
)

@Serializable
data class CreateConversationResponseDto(
    @SerialName("conversation_id")
    val conversationId: Int,
    @SerialName("conversation_uuid")
    val conversationUuid: String,
    @SerialName("recipient_user_id")
    val recipientUserId: Int,
    @SerialName("protection_mode")
    val protectionMode: String,
    @SerialName("is_saved_messages")
    val isSavedMessages: Boolean = false,
)

@Serializable
data class ConversationPeerDto(
    @SerialName("user_id")
    val userId: Int,
    val nickname: String? = null,
)

@Serializable
data class ConversationListItemDto(
    @SerialName("conversation_id")
    val conversationId: Int,
    @SerialName("conversation_uuid")
    val conversationUuid: String,
    val title: String? = null,
    @SerialName("is_saved_messages")
    val isSavedMessages: Boolean = false,
    @SerialName("protection_mode")
    val protectionMode: String = "normal",
    @SerialName("message_ttl_days")
    val messageTtlDays: Int? = null,
    @SerialName("delete_after_read_seconds")
    val deleteAfterReadSeconds: Int? = null,
    val peer: ConversationPeerDto,
    @SerialName("unread_count")
    val unreadCount: Int,
    @SerialName("is_active")
    val isActive: Boolean = true,
    @SerialName("is_purged")
    val isPurged: Boolean = false,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("last_message")
    val lastMessage: ConversationLastMessageDto? = null,
    @SerialName("shared_secret_enabled")
    val sharedSecretEnabled: Boolean = false,
    @SerialName("shared_secret_fingerprint")
    val sharedSecretFingerprint: String? = null,
    @SerialName("shared_secret_updated_at")
    val sharedSecretUpdatedAt: String? = null,
    @SerialName("peer_shared_secret_enabled")
    val peerSharedSecretEnabled: Boolean = false,
    @SerialName("pinned_message")
    val pinnedMessage: MessagePreviewDto? = null,
)

@Serializable
data class ListConversationsResponseDto(
    val items: List<ConversationListItemDto> = emptyList(),
)

@Serializable
data class GetConversationResponseDto(
    @SerialName("conversation_id")
    val conversationId: Int,
    @SerialName("conversation_uuid")
    val conversationUuid: String,
    val title: String? = null,
    @SerialName("peer_user_id")
    val peerUserId: Int,
    @SerialName("is_saved_messages")
    val isSavedMessages: Boolean = false,
    @SerialName("protection_mode")
    val protectionMode: String,
    @SerialName("message_ttl_days")
    val messageTtlDays: Int? = null,
    @SerialName("delete_after_read_seconds")
    val deleteAfterReadSeconds: Int? = null,
    @SerialName("shared_secret_enabled")
    val sharedSecretEnabled: Boolean = false,
    @SerialName("shared_secret_fingerprint")
    val sharedSecretFingerprint: String? = null,
    @SerialName("shared_secret_updated_at")
    val sharedSecretUpdatedAt: String? = null,
    @SerialName("peer_shared_secret_enabled")
    val peerSharedSecretEnabled: Boolean = false,
    @SerialName("is_active")
    val isActive: Boolean = true,
    @SerialName("is_purged")
    val isPurged: Boolean = false,
    @SerialName("pinned_message")
    val pinnedMessage: MessagePreviewDto? = null,
)


@Serializable
data class UpdateConversationSettingsRequestDto(
    @SerialName("shared_secret_enabled")
    val sharedSecretEnabled: Boolean? = null,
    @SerialName("shared_secret_fingerprint")
    val sharedSecretFingerprint: String? = null,
)

@Serializable
data class ConversationSettingsResponseDto(
    @SerialName("conversation_id")
    val conversationId: Int,
    @SerialName("user_id")
    val userId: Int,
    @SerialName("shared_secret_enabled")
    val sharedSecretEnabled: Boolean,
    @SerialName("shared_secret_fingerprint")
    val sharedSecretFingerprint: String? = null,
    @SerialName("shared_secret_updated_at")
    val sharedSecretUpdatedAt: String? = null,
)

@Serializable
data class MessageDevicePayloadRequestDto(
    @SerialName("device_id")
    val deviceId: Int,
    val ciphertext: String,
    @SerialName("ciphertext_version")
    val ciphertextVersion: Int = 1,
    val nonce: String,
    @SerialName("aad_hash")
    val aadHash: String? = null,
)

@Serializable
data class MessageDevicePayloadDto(
    @SerialName("device_id")
    val deviceId: Int,
    val ciphertext: String,
    @SerialName("ciphertext_version")
    val ciphertextVersion: Int,
    val nonce: String,
    @SerialName("aad_hash")
    val aadHash: String? = null,
)

@Serializable
data class SendMessageRequestDto(
    @SerialName("conversation_id")
    val conversationId: Int,
    @SerialName("recipient_user_id")
    val recipientUserId: Int,
    @SerialName("message_uuid")
    val messageUuid: String,
    @SerialName("message_type")
    val messageType: String = "text",
    val ciphertext: String,
    @SerialName("ciphertext_version")
    val ciphertextVersion: Int = 1,
    @SerialName("encryption_mode")
    val encryptionMode: String = "signal",
    val nonce: String,
    @SerialName("aad_hash")
    val aadHash: String? = null,
    @SerialName("client_created_at")
    val clientCreatedAt: String,
    @SerialName("reply_to_message_id")
    val replyToMessageId: Int? = null,
    @SerialName("expires_at")
    val expiresAt: String? = null,
    @SerialName("attachment_ids")
    val attachmentIds: List<Int> = emptyList(),
    @SerialName("device_payloads")
    val devicePayloads: List<MessageDevicePayloadRequestDto> = emptyList(),
)

@Serializable
data class SendMessageResponseDto(
    @SerialName("message_id")
    val messageId: Int,
    @SerialName("message_uuid")
    val messageUuid: String,
    @SerialName("conversation_id")
    val conversationId: Int,
    @SerialName("recipient_user_id")
    val recipientUserId: Int,
    @SerialName("recipient_device_id")
    val recipientDeviceId: Int,
    @SerialName("recipient_device_ids")
    val recipientDeviceIds: List<Int> = emptyList(),
    @SerialName("server_received_at")
    val serverReceivedAt: String,
    @SerialName("delivery_status")
    val deliveryStatus: String,
    @SerialName("is_idempotent_replay")
    val isIdempotentReplay: Boolean = false,
)

@Serializable
data class ForwardMessagesRequestDto(
    @SerialName("conversation_id")
    val conversationId: Int,
    @SerialName("recipient_user_id")
    val recipientUserId: Int,
    @SerialName("message_ids")
    val messageIds: List<Int>,
    @SerialName("client_created_at")
    val clientCreatedAt: String? = null,
)

@Serializable
data class ForwardedMessageItemDto(
    @SerialName("source_message_id")
    val sourceMessageId: Int,
    @SerialName("message_id")
    val messageId: Int,
    @SerialName("message_uuid")
    val messageUuid: String,
    @SerialName("recipient_device_id")
    val recipientDeviceId: Int,
    @SerialName("recipient_device_ids")
    val recipientDeviceIds: List<Int> = emptyList(),
    @SerialName("server_received_at")
    val serverReceivedAt: String,
)

@Serializable
data class ForwardMessagesResponseDto(
    @SerialName("conversation_id")
    val conversationId: Int,
    @SerialName("recipient_user_id")
    val recipientUserId: Int,
    val items: List<ForwardedMessageItemDto> = emptyList(),
)


@Serializable
data class MessageReactionSummaryDto(
    val reaction: String,
    val count: Int,
    val me: Boolean = false,
)

@Serializable
data class MessagePreviewDto(
    @SerialName("message_id")
    val messageId: Int,
    @SerialName("message_uuid")
    val messageUuid: String,
    @SerialName("sender_user_id")
    val senderUserId: Int,
    @SerialName("sender_device_id")
    val senderDeviceId: Int? = null,
    @SerialName("message_type")
    val messageType: String = "text",
    val ciphertext: String,
    @SerialName("ciphertext_version")
    val ciphertextVersion: Int? = null,
    val nonce: String? = null,
    @SerialName("aad_hash")
    val aadHash: String? = null,
    @SerialName("device_payload")
    val devicePayload: MessageDevicePayloadDto? = null,
    @SerialName("has_attachments")
    val hasAttachments: Boolean = false,
    @SerialName("client_created_at")
    val clientCreatedAt: String,
)

@Serializable
data class MessageItemDto(
    @SerialName("message_id")
    val messageId: Int,
    @SerialName("message_uuid")
    val messageUuid: String,
    @SerialName("sender_user_id")
    val senderUserId: Int,
    @SerialName("sender_device_id")
    val senderDeviceId: Int? = null,
    @SerialName("recipient_user_id")
    val recipientUserId: Int,
    @SerialName("message_type")
    val messageType: String = "text",
    val ciphertext: String,
    @SerialName("ciphertext_version")
    val ciphertextVersion: Int = 1,
    @SerialName("encryption_mode")
    val encryptionMode: String = "signal",
    val nonce: String = "",
    @SerialName("aad_hash")
    val aadHash: String? = null,
    @SerialName("device_payload")
    val devicePayload: MessageDevicePayloadDto? = null,
    @SerialName("client_created_at")
    val clientCreatedAt: String,
    @SerialName("server_received_at")
    val serverReceivedAt: String,
    @SerialName("delivered_at")
    val deliveredAt: String? = null,
    @SerialName("read_at")
    val readAt: String? = null,
    @SerialName("expires_at")
    val expiresAt: String? = null,
    @SerialName("has_attachments")
    val hasAttachments: Boolean = false,
    @SerialName("reply_to_message_id")
    val replyToMessageId: Int? = null,
    @SerialName("forward_from_message_id")
    val forwardFromMessageId: Int? = null,
    @SerialName("reply_preview")
    val replyPreview: MessagePreviewDto? = null,
    @SerialName("forward_preview")
    val forwardPreview: MessagePreviewDto? = null,
    val reactions: List<MessageReactionSummaryDto> = emptyList(),
)

@Serializable
data class ListMessagesResponseDto(
    val items: List<MessageItemDto> = emptyList(),
    @SerialName("anchor_message_id")
    val anchorMessageId: Int? = null,
    @SerialName("before_id")
    val beforeId: Int? = null,
    @SerialName("before_cursor")
    val beforeCursor: String? = null,
    @SerialName("after_id")
    val afterId: Int? = null,
    @SerialName("after_cursor")
    val afterCursor: String? = null,
)

@Serializable
data class SearchMessagesResponseDto(
    @SerialName("conversation_id")
    val conversationId: Int,
    val query: String,
    val items: List<MessageItemDto> = emptyList(),
)

@Serializable
data class SharedTabCountsDto(
    val media: Int = 0,
    val links: Int = 0,
    val files: Int = 0,
)

@Serializable
data class SharedMessagesResponseDto(
    @SerialName("conversation_id")
    val conversationId: Int,
    val tab: String,
    val counts: SharedTabCountsDto,
    val items: List<MessageItemDto> = emptyList(),
)

@Serializable
data class MarkDeliveredRequestDto(
    @SerialName("delivered_at")
    val deliveredAt: String? = null,
)

@Serializable
data class MarkDeliveredResponseDto(
    @SerialName("message_id")
    val messageId: Int,
    val status: String,
    @SerialName("delivered_at")
    val deliveredAt: String,
)

@Serializable
data class MarkReadRequestDto(
    @SerialName("read_at")
    val readAt: String? = null,
)

@Serializable
data class MarkReadResponseDto(
    @SerialName("message_id")
    val messageId: Int,
    val status: String,
    @SerialName("read_at")
    val readAt: String,
)


@Serializable
data class DeviceHeartbeatResponseDto(
    @SerialName("device_id")
    val deviceId: Int,
    @SerialName("device_uuid")
    val deviceUuid: String,
    val status: String,
    @SerialName("last_seen_at")
    val lastSeenAt: String,
)

@Serializable
data class RevokeCurrentDeviceResponseDto(
    @SerialName("device_id")
    val deviceId: Int,
    val revoked: Boolean,
    @SerialName("revoked_sessions")
    val revokedSessions: Int,
    @SerialName("revoked_at")
    val revokedAt: String,
)

@Serializable
data class DeleteMessagesRequestDto(
    @SerialName("conversation_id")
    val conversationId: Int,
    @SerialName("message_ids")
    val messageIds: List<Int>,
)

@Serializable
data class DeleteMessagesResponseDto(
    val deleted: Boolean,
    val scope: String,
    @SerialName("message_ids")
    val messageIds: List<Int> = emptyList(),
)

@Serializable
data class UpdateFcmTokenRequestDto(
    @SerialName("fcm_token")
    val fcmToken: String? = null,
)

@Serializable
data class UpdateFcmTokenResponseDto(
    @SerialName("device_id")
    val deviceId: Int,
    val updated: Boolean,
    @SerialName("fcm_token_present")
    val fcmTokenPresent: Boolean,
    @SerialName("last_seen_at")
    val lastSeenAt: String? = null,
)

@Serializable
data class CreateUploadSessionRequestDto(
    @SerialName("conversation_id")
    val conversationId: Int,
    @SerialName("files_expected_count")
    val filesExpectedCount: Int,
)

@Serializable
data class CreateUploadSessionResponseDto(
    @SerialName("session_id")
    val sessionId: Int,
    @SerialName("session_uuid")
    val sessionUuid: String,
    @SerialName("conversation_id")
    val conversationId: Int,
    @SerialName("files_expected_count")
    val filesExpectedCount: Int,
    @SerialName("files_uploaded_count")
    val filesUploadedCount: Int,
    val status: String,
    @SerialName("expires_at")
    val expiresAt: String,
)

@Serializable
data class InitAttachmentItemRequestDto(
    @SerialName("encrypted_file_name")
    val encryptedFileName: String? = null,
    @SerialName("file_size")
    val fileSize: Long,
    @SerialName("mime_hint")
    val mimeHint: String? = null,
    @SerialName("sha256_encrypted_blob")
    val sha256EncryptedBlob: String,
    @SerialName("encrypted_metadata")
    val encryptedMetadata: Map<String, String>? = null,
)

@Serializable
data class InitAttachmentsRequestDto(
    val items: List<InitAttachmentItemRequestDto>,
)

@Serializable
data class AttachmentInitItemDto(
    @SerialName("attachment_id")
    val attachmentId: Int,
    @SerialName("attachment_uuid")
    val attachmentUuid: String,
    @SerialName("storage_key")
    val storageKey: String,
    @SerialName("bucket_name")
    val bucketName: String,
    @SerialName("upload_status")
    val uploadStatus: String,
    @SerialName("expires_at")
    val expiresAt: String? = null,
    @SerialName("upload_url")
    val uploadUrl: String? = null,
    @SerialName("upload_method")
    val uploadMethod: String? = null,
    @SerialName("upload_headers")
    val uploadHeaders: Map<String, String> = emptyMap(),
)

@Serializable
data class InitAttachmentsResponseDto(
    @SerialName("session_id")
    val sessionId: Int,
    @SerialName("session_uuid")
    val sessionUuid: String,
    val items: List<AttachmentInitItemDto> = emptyList(),
)

@Serializable
data class CompleteUploadSessionRequestDto(
    @SerialName("attachment_ids")
    val attachmentIds: List<Int>,
)

@Serializable
data class CompleteUploadSessionResponseDto(
    @SerialName("session_id")
    val sessionId: Int,
    @SerialName("session_uuid")
    val sessionUuid: String,
    val status: String,
    @SerialName("files_expected_count")
    val filesExpectedCount: Int,
    @SerialName("files_uploaded_count")
    val filesUploadedCount: Int,
    @SerialName("completed_at")
    val completedAt: String,
)

@Serializable
data class ConversationLastMessageDto(
    @SerialName("message_id")
    val messageId: Int,
    @SerialName("message_uuid")
    val messageUuid: String,
    @SerialName("sender_user_id")
    val senderUserId: Int,
    @SerialName("sender_device_id")
    val senderDeviceId: Int? = null,
    @SerialName("recipient_user_id")
    val recipientUserId: Int,
    @SerialName("message_type")
    val messageType: String,
    @SerialName("client_created_at")
    val clientCreatedAt: String,
    @SerialName("server_received_at")
    val serverReceivedAt: String,
    @SerialName("has_attachments")
    val hasAttachments: Boolean = false,
)
@Serializable
data class AttachmentMetadataItemDto(
    @SerialName("attachment_id")
    val attachmentId: Int,
    @SerialName("attachment_uuid")
    val attachmentUuid: String,
    @SerialName("message_id")
    val messageId: Int? = null,
    @SerialName("encrypted_file_name")
    val encryptedFileName: String? = null,
    @SerialName("encrypted_metadata")
    val encryptedMetadata: Map<String, String>? = null,
    @SerialName("file_size")
    val fileSize: Long,
    @SerialName("mime_hint")
    val mimeHint: String? = null,
    @SerialName("sha256_encrypted_blob")
    val sha256EncryptedBlob: String,
    @SerialName("bucket_name")
    val bucketName: String,
    @SerialName("storage_key")
    val storageKey: String,
    @SerialName("upload_status")
    val uploadStatus: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("expires_at")
    val expiresAt: String? = null,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
)

@Serializable
data class SetMessageReactionRequestDto(
    val reaction: String,
)

@Serializable
data class SetMessageReactionResponseDto(
    @SerialName("message_id")
    val messageId: Int,
    val reaction: String,
    val updated: Boolean,
)

@Serializable
data class DeleteMessageReactionResponseDto(
    @SerialName("message_id")
    val messageId: Int,
    val removed: Boolean,
)

@Serializable
data class PinMessageResponseDto(
    @SerialName("conversation_id")
    val conversationId: Int,
    @SerialName("message_id")
    val messageId: Int? = null,
    val pinned: Boolean,
)

@Serializable
data class ListMessageAttachmentsResponseDto(
    @SerialName("message_id")
    val messageId: Int,
    val items: List<AttachmentMetadataItemDto> = emptyList(),
)

@Serializable
data class GetAttachmentResponseDto(
    @SerialName("attachment_id")
    val attachmentId: Int,
    @SerialName("attachment_uuid")
    val attachmentUuid: String,
    @SerialName("message_id")
    val messageId: Int? = null,
    @SerialName("encrypted_file_name")
    val encryptedFileName: String? = null,
    @SerialName("encrypted_metadata")
    val encryptedMetadata: Map<String, String>? = null,
    @SerialName("file_size")
    val fileSize: Long,
    @SerialName("mime_hint")
    val mimeHint: String? = null,
    @SerialName("sha256_encrypted_blob")
    val sha256EncryptedBlob: String,
    @SerialName("bucket_name")
    val bucketName: String,
    @SerialName("storage_key")
    val storageKey: String,
    @SerialName("upload_status")
    val uploadStatus: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("expires_at")
    val expiresAt: String? = null,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
    @SerialName("can_download")
    val canDownload: Boolean = true,
    @SerialName("download_url")
    val downloadUrl: String? = null,
    @SerialName("download_url_expires_in")
    val downloadUrlExpiresIn: Int? = null,
)

@Serializable
data class LogoutResponseDto(
    val message: String,
    @SerialName("revoked_sessions")
    val revokedSessions: Int,
)
@Serializable
data class LogoutAllResponseDto(
    val message: String,
    @SerialName("revoked_sessions")
    val revokedSessions: Int,
)
@Serializable
data class ConversationEventItemDto(
    @SerialName("event_id")
    val eventId: Int,
    @SerialName("event_uuid")
    val eventUuid: String,
    @SerialName("event_type")
    val eventType: String,
    @SerialName("actor_user_id")
    val actorUserId: Int? = null,
    @SerialName("actor_device_id")
    val actorDeviceId: Int? = null,
    @SerialName("target_message_id")
    val targetMessageId: Int? = null,
    val payload: JsonObject? = null,
    @SerialName("created_at")
    val createdAt: String,
)

@Serializable
data class ConversationEventsResponseDto(
    @SerialName("conversation_id")
    val conversationId: Int,
    val items: List<ConversationEventItemDto> = emptyList(),
    @SerialName("next_after_event_id")
    val nextAfterEventId: Int? = null,
    @SerialName("has_more")
    val hasMore: Boolean,
)
