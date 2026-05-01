package com.example.securechatapp.data.repository

import android.os.Build
import com.example.securechatapp.BuildConfig
import com.example.securechatapp.crypto.signal.SignalBootstrapKeyMaterialProvider
import com.example.securechatapp.data.local.preferences.SecureSessionLocalDataSource
import com.example.securechatapp.data.remote.api.AuthApi
import com.example.securechatapp.data.remote.api.ChatBackendApi
import com.example.securechatapp.data.remote.dto.UpdateFcmTokenRequestDto
import com.example.securechatapp.data.remote.dto.auth.BootstrapDeviceRequestDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class SessionRepository @Inject constructor(
    private val api: ChatBackendApi,
    private val authApi: AuthApi,
    private val sessionStore: SecureSessionLocalDataSource,
    private val signalBootstrapKeyMaterialProvider: SignalBootstrapKeyMaterialProvider,
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

    suspend fun rebootstrapCurrentDevice(): Boolean {
        val session = sessionStore.getSessionSnapshot()
        val accessToken = session.accessToken?.takeIf { it.isNotBlank() } ?: return false
        val deviceUuid = session.deviceUuid?.takeIf { it.isNotBlank() }
            ?: sessionStore.getOrCreateDeviceUuid()

        return runCatching {
            val signalMaterial = signalBootstrapKeyMaterialProvider.getOrCreateBootstrapMaterial(
                oneTimePreKeyCount = 100,
            )

            safe {
                authApi.bootstrap(
                    authorization = "Bearer $accessToken",
                    body = BootstrapDeviceRequestDto(
                        deviceUuid = deviceUuid,
                        deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Android device" },
                        platform = "android",
                        appVersion = BuildConfig.VERSION_NAME,
                        registrationId = signalMaterial.registrationId,
                        publicIdentityKey = signalMaterial.publicIdentityKey,
                        publicSigningKey = signalMaterial.publicSigningKey,
                        signedPrekeyId = signalMaterial.signedPreKeyId,
                        signedPrekey = signalMaterial.signedPreKey,
                        signedPrekeySignature = signalMaterial.signedPreKeySignature,
                        oneTimePrekeys = signalMaterial.oneTimePreKeys,
                    ),
                ).data
            }

            sessionStore.saveDeviceUuid(deviceUuid)
            true
        }.recoverCatching { error ->
            handleSessionInvalidation(error)
            throw error
        }.getOrDefault(false)
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
