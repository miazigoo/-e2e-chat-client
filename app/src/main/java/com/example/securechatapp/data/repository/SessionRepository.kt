package com.example.securechatapp.data.repository

import com.example.securechatapp.data.local.preferences.SessionLocalDataSource
import com.example.securechatapp.data.remote.api.ChatBackendApi
import com.example.securechatapp.data.remote.dto.UpdateFcmTokenRequestDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class SessionRepository @Inject constructor(
    private val api: ChatBackendApi,
    private val sessionStore: SessionLocalDataSource,
    json: Json,
) : BaseApiRepository(json) {

    suspend fun ensureDeviceUuid(): String = sessionStore.getOrCreateDeviceUuid()

    suspend fun heartbeat(): String? {
        return runCatching {
            safe { api.sendHeartbeat().data }.lastSeenAt
        }.getOrNull()
    }

    suspend fun updateFcmToken(token: String?) {
        runCatching {
            safe {
                api.updateFcmToken(
                    UpdateFcmTokenRequestDto(fcmToken = token)
                ).data
            }
        }
    }

    suspend fun logoutSession() {
        runCatching {
            safe { api.logoutSession().data }
        }
        sessionStore.clearSession(keepDeviceUuid = true)
    }

    suspend fun logoutAllSessions() {
        runCatching {
            safe { api.logoutAllSessions().data }
        }
        sessionStore.clearSession(keepDeviceUuid = true)
    }

    suspend fun revokeCurrentDevice() {
        runCatching {
            safe { api.revokeCurrentDevice().data }
        }
        sessionStore.clearSession(keepDeviceUuid = true)
    }
}
