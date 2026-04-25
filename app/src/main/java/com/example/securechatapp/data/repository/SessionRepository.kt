package com.example.securechatapp.data.repository

import com.example.securechatapp.data.local.preferences.SecureSessionLocalDataSource
import com.example.securechatapp.data.remote.api.ChatBackendApi
import com.example.securechatapp.data.remote.dto.UpdateFcmTokenRequestDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class SessionRepository @Inject constructor(
    private val api: ChatBackendApi,
    private val sessionStore: SecureSessionLocalDataSource,
    private val json: Json,
) : BaseApiRepository(json) {

    suspend fun ensureDeviceUuid(): String = sessionStore.getOrCreateDeviceUuid()

    suspend fun heartbeat(): String? {
        return runCatching {
            safe { api.sendHeartbeat().data }.lastSeenAt
        }.recoverCatching { error ->
            handleSessionInvalidation(error)
            throw error
        }.getOrNull()
    }

    suspend fun updateFcmToken(token: String?) {
        runCatching {
            safe {
                api.updateFcmToken(
                    UpdateFcmTokenRequestDto(fcmToken = token)
                ).data
            }
        }.onFailure { error ->
            handleSessionInvalidation(error)
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

    private fun handleSessionInvalidation(error: Throwable) {
        val backendError = when (error) {
            is BackendApiException -> error
            is retrofit2.HttpException -> parseBackendApiException(json, error)
            else -> null
        } ?: return

        if (backendError.code in SESSION_INVALIDATION_CODES) {
            sessionStore.clearSession(keepDeviceUuid = true)
        }
    }

    private companion object {
        val SESSION_INVALIDATION_CODES = setOf(
            "AUTH_REQUIRED",
            "INVALID_ACCESS_TOKEN",
            "INVALID_TOKEN_TYPE",
            "SESSION_NOT_FOUND",
            "SESSION_TOKEN_MISMATCH",
            "ACCOUNT_UNAVAILABLE",
            "DEVICE_UUID_REQUIRED",
            "DEVICE_NOT_REGISTERED",
            "DEVICE_SESSION_MISMATCH",
        )
    }
}
