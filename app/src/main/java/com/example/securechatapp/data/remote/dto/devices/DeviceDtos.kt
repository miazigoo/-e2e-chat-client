package com.example.securechatapp.data.remote.dto.devices

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceListItemDto(
    @SerialName("device_id")
    val deviceId: Int,
    @SerialName("device_uuid")
    val deviceUuid: String,
    @SerialName("device_name")
    val deviceName: String,
    val platform: String,
    @SerialName("app_version")
    val appVersion: String,
    @SerialName("is_current")
    val isCurrent: Boolean,
    @SerialName("fcm_token_present")
    val fcmTokenPresent: Boolean,
    @SerialName("registered_at")
    val registeredAt: String,
    @SerialName("last_seen_at")
    val lastSeenAt: String? = null,
)

@Serializable
data class ListDevicesResponseDto(
    val items: List<DeviceListItemDto> = emptyList(),
)

@Serializable
data class DeviceAuthorizationRequestDto(
    @SerialName("request_id")
    val requestId: String,
    @SerialName("device_uuid")
    val deviceUuid: String,
    @SerialName("device_name")
    val deviceName: String? = null,
    val platform: String? = null,
    @SerialName("app_version")
    val appVersion: String? = null,
    @SerialName("ip_address")
    val ipAddress: String? = null,
    @SerialName("user_agent")
    val userAgent: String? = null,
    @SerialName("requested_at")
    val requestedAt: String,
    @SerialName("expires_at")
    val expiresAt: String,
)

@Serializable
data class ListDeviceAuthorizationRequestsResponseDto(
    val items: List<DeviceAuthorizationRequestDto> = emptyList(),
)

@Serializable
data class ResolveDeviceAuthorizationRequestResponseDto(
    @SerialName("request_id")
    val requestId: String,
    val status: String,
    @SerialName("bootstrap_available")
    val bootstrapAvailable: Boolean = false,
)

@Serializable
data class RevokeDeviceResponseDto(
    @SerialName("device_id")
    val deviceId: Int,
    val revoked: Boolean,
    @SerialName("revoked_sessions")
    val revokedSessions: Int,
    @SerialName("revoked_at")
    val revokedAt: String,
)
