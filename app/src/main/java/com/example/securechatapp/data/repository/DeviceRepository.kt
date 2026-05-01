package com.example.securechatapp.data.repository

import com.example.securechatapp.data.remote.api.DevicesApi
import com.example.securechatapp.domain.model.AuthorizedDevice
import com.example.securechatapp.domain.model.DeviceAuthorizationRequest
import com.example.securechatapp.domain.model.DeviceAuthorizationResolution
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class DeviceRepository @Inject constructor(
    private val api: DevicesApi,
    json: Json,
) : BaseApiRepository(json) {

    suspend fun listDevices(): List<AuthorizedDevice> {
        return safe { api.listDevices().data }.items.map { item ->
            AuthorizedDevice(
                deviceId = item.deviceId,
                deviceUuid = item.deviceUuid,
                deviceName = item.deviceName,
                platform = item.platform,
                appVersion = item.appVersion,
                isCurrent = item.isCurrent,
                fcmTokenPresent = item.fcmTokenPresent,
                registeredAt = item.registeredAt,
                lastSeenAt = item.lastSeenAt,
            )
        }
    }

    suspend fun listAuthorizationRequests(): List<DeviceAuthorizationRequest> {
        return safe { api.listAuthorizationRequests().data }.items.map { item ->
            DeviceAuthorizationRequest(
                requestId = item.requestId,
                deviceUuid = item.deviceUuid,
                deviceName = item.deviceName,
                platform = item.platform,
                appVersion = item.appVersion,
                ipAddress = item.ipAddress,
                userAgent = item.userAgent,
                requestedAt = item.requestedAt,
                expiresAt = item.expiresAt,
            )
        }
    }

    suspend fun approveAuthorizationRequest(requestId: String): DeviceAuthorizationResolution {
        val data = safe { api.approveAuthorizationRequest(requestId).data }
        return DeviceAuthorizationResolution(
            requestId = data.requestId,
            status = data.status,
            bootstrapAvailable = data.bootstrapAvailable,
        )
    }

    suspend fun denyAuthorizationRequest(requestId: String): DeviceAuthorizationResolution {
        val data = safe { api.denyAuthorizationRequest(requestId).data }
        return DeviceAuthorizationResolution(
            requestId = data.requestId,
            status = data.status,
            bootstrapAvailable = data.bootstrapAvailable,
        )
    }

    suspend fun revokeDevice(deviceId: Int) {
        safe { api.revokeDevice(deviceId).data }
    }
}
