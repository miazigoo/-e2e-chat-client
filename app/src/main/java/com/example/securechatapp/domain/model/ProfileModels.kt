package com.example.securechatapp.domain.model

data class UserProfileSettings(
    val languageCode: String,
    val theme: String,
    val pushNotificationsEnabled: Boolean,
    val apkUpdateNotificationsEnabled: Boolean,
    val google2faEnabled: Boolean,
)

data class UserProfile(
    val userId: Int,
    val publicId: String,
    val nickname: String,
    val fullName: String?,
    val bio: String?,
    val avatarUrl: String?,
    val avatarUpdatedAt: String?,
    val createdAt: String,
    val settings: UserProfileSettings,
)

data class UserSafety(
    val userId: Int,
    val nickname: String,
    val canStartConversation: Boolean,
    val isDeleted: Boolean,
    val pendingDeletion: Boolean,
    val hasActiveDevice: Boolean,
    val supportsEncryptedChat: Boolean,
    val safetyCodeAvailable: Boolean,
)

data class AppReleaseInfo(
    val platform: String,
    val versionName: String,
    val versionCode: Int,
    val fileName: String,
    val fileSize: Long,
    val sha256: String,
    val changelog: String?,
    val forceUpdate: Boolean,
    val minSupportedVersionCode: Int?,
    val contentType: String,
    val uploadedAt: String,
    val downloadUrl: String,
    val downloadUrlExpiresIn: Int,
)

data class AppVersionCheck(
    val currentVersionCode: Int,
    val latestVersionCode: Int,
    val updateAvailable: Boolean,
    val updateRequired: Boolean,
    val release: AppReleaseInfo,
)
