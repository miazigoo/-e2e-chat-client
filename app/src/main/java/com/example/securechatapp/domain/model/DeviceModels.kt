package com.example.securechatapp.domain.model

data class AuthorizedDevice(
    val deviceId: Int,
    val deviceUuid: String,
    val deviceName: String,
    val platform: String,
    val appVersion: String,
    val isCurrent: Boolean,
    val fcmTokenPresent: Boolean,
    val registeredAt: String,
    val lastSeenAt: String?,
)

data class DeviceAuthorizationRequest(
    val requestId: String,
    val deviceUuid: String,
    val deviceName: String?,
    val platform: String?,
    val appVersion: String?,
    val ipAddress: String?,
    val userAgent: String?,
    val requestedAt: String,
    val expiresAt: String,
)

data class DeviceAuthorizationResolution(
    val requestId: String,
    val status: String,
    val bootstrapAvailable: Boolean,
)
