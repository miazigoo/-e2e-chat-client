package com.example.securechatapp.data.repository

import android.os.Build
import com.example.securechatapp.core.crypto.DevCryptoEngine
import com.example.securechatapp.core.network.ApiErrorEnvelopeDto
import com.example.securechatapp.core.result.AppResult
import com.example.securechatapp.data.local.preferences.SessionLocalDataSource
import com.example.securechatapp.data.remote.api.AuthApi
import com.example.securechatapp.data.remote.dto.auth.BootstrapDeviceRequestDto
import com.example.securechatapp.data.remote.dto.auth.LoginRequestDto
import com.example.securechatapp.data.remote.dto.auth.OneTimePreKeyDto
import com.example.securechatapp.data.remote.dto.auth.RegisterRequestDto
import com.example.securechatapp.data.remote.dto.auth.VerifyEmailCodeRequestDto
import com.example.securechatapp.domain.model.LoginResult
import com.example.securechatapp.domain.model.RegisterResult
import com.example.securechatapp.domain.model.VerifyEmailCodeResult
import com.example.securechatapp.domain.repository.AuthRepository
import javax.inject.Inject
import kotlinx.serialization.json.Json
import retrofit2.HttpException

class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val sessionLocalDataSource: SessionLocalDataSource,
    private val crypto: DevCryptoEngine,
    private val json: Json,
) : AuthRepository {

    override suspend fun register(
        nickname: String,
        password: String,
        email: String?,
        email2faEnabled: Boolean,
    ): AppResult<RegisterResult> {
        return try {
            val response = authApi.register(
                RegisterRequestDto(
                    nickname = nickname,
                    password = password,
                    email = email,
                    email2faEnabled = email2faEnabled,
                )
            )

            val deviceUuid = sessionLocalDataSource.getOrCreateDeviceUuid()
            response.data.bootstrapToken?.takeIf { it.isNotBlank() }?.let { token ->
                bootstrapDevice(
                    bootstrapToken = token,
                    deviceUuid = deviceUuid,
                )
            }

            AppResult.Success(
                RegisterResult(
                    userId = response.data.userId,
                    nickname = response.data.nickname,
                    requiresDeviceRegistration = response.data.requiresDeviceRegistration,
                    bootstrapToken = response.data.bootstrapToken,
                    bootstrapExpiresIn = response.data.bootstrapExpiresIn,
                )
            )
        } catch (e: HttpException) {
            parseHttpError(e)
        } catch (e: Exception) {
            AppResult.Error(
                code = "NETWORK_ERROR",
                message = e.message ?: "Unknown network error",
            )
        }
    }

    override suspend fun login(
        nickname: String,
        password: String,
        deviceUuid: String?,
    ): AppResult<LoginResult> {
        val stableDeviceUuid = deviceUuid ?: sessionLocalDataSource.getOrCreateDeviceUuid()

        return try {
            val response = authApi.login(
                LoginRequestDto(
                    nickname = nickname,
                    password = password,
                    deviceUuid = stableDeviceUuid,
                )
            )

            val data = response.data

            if (data.requiresBootstrap && !data.bootstrapToken.isNullOrBlank()) {
                bootstrapDevice(
                    bootstrapToken = data.bootstrapToken,
                    deviceUuid = stableDeviceUuid,
                )
                return login(
                    nickname = nickname,
                    password = password,
                    deviceUuid = stableDeviceUuid,
                )
            }

            if (!data.accessToken.isNullOrBlank() &&
                !data.refreshToken.isNullOrBlank()
            ) {
                sessionLocalDataSource.saveFullSession(
                    accessToken = data.accessToken,
                    refreshToken = data.refreshToken,
                    deviceUuid = stableDeviceUuid,
                )
            }

            AppResult.Success(
                LoginResult(
                    requiresEmailCode = data.requiresEmailCode,
                    requiresBootstrap = data.requiresBootstrap,
                    loginChallengeId = data.loginChallengeId,
                    emailMasked = data.emailMasked,
                    debugCode = data.debugCode,
                    bootstrapToken = data.bootstrapToken,
                    bootstrapExpiresIn = data.bootstrapExpiresIn,
                    accessToken = data.accessToken,
                    refreshToken = data.refreshToken,
                    expiresIn = data.expiresIn,
                )
            )
        } catch (e: HttpException) {
            parseHttpError(e)
        } catch (e: Exception) {
            AppResult.Error(
                code = "NETWORK_ERROR",
                message = e.message ?: "Unknown network error",
            )
        }
    }

    override suspend fun verifyEmailCode(
        loginChallengeId: String,
        code: String,
        deviceUuid: String?,
    ): AppResult<VerifyEmailCodeResult> {
        val stableDeviceUuid = deviceUuid ?: sessionLocalDataSource.getOrCreateDeviceUuid()

        return try {
            val response = authApi.verifyEmailCode(
                VerifyEmailCodeRequestDto(
                    loginChallengeId = loginChallengeId,
                    code = code,
                    deviceUuid = stableDeviceUuid,
                )
            )

            val data = response.data

            if (data.requiresBootstrap && !data.bootstrapToken.isNullOrBlank()) {
                bootstrapDevice(
                    bootstrapToken = data.bootstrapToken,
                    deviceUuid = stableDeviceUuid,
                )
            }

            if (!data.accessToken.isNullOrBlank() &&
                !data.refreshToken.isNullOrBlank()
            ) {
                sessionLocalDataSource.saveFullSession(
                    accessToken = data.accessToken,
                    refreshToken = data.refreshToken,
                    deviceUuid = stableDeviceUuid,
                )
            }

            AppResult.Success(
                VerifyEmailCodeResult(
                    requiresBootstrap = data.requiresBootstrap,
                    bootstrapToken = data.bootstrapToken,
                    bootstrapExpiresIn = data.bootstrapExpiresIn,
                    accessToken = data.accessToken,
                    refreshToken = data.refreshToken,
                    expiresIn = data.expiresIn,
                )
            )
        } catch (e: HttpException) {
            parseHttpError(e)
        } catch (e: Exception) {
            AppResult.Error(
                code = "NETWORK_ERROR",
                message = e.message ?: "Unknown network error",
            )
        }
    }

    private suspend fun bootstrapDevice(
        bootstrapToken: String,
        deviceUuid: String,
    ) {
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

        authApi.bootstrap(
            authorization = "Bearer $bootstrapToken",
            body = BootstrapDeviceRequestDto(
                deviceUuid = deviceUuid,
                deviceName = deviceName.ifBlank { "Android device" },
                platform = "android",
                appVersion = "1.0.0",
                publicIdentityKey = crypto.randomBase64(48),
                publicSigningKey = crypto.randomBase64(48),
                signedPrekey = crypto.randomBase64(48),
                signedPrekeySignature = crypto.randomBase64(64),
                oneTimePrekeys = (1..20).map { index ->
                    OneTimePreKeyDto(
                        prekeyId = index,
                        publicPrekey = crypto.randomBase64(48),
                    )
                },
            )
        )

        sessionLocalDataSource.saveDeviceUuid(deviceUuid)
    }

    private fun parseHttpError(e: HttpException): AppResult.Error {
        val raw = e.response()?.errorBody()?.string()
        val parsed = raw?.let {
            runCatching {
                json.decodeFromString(ApiErrorEnvelopeDto.serializer(), it)
            }.getOrNull()
        }

        return AppResult.Error(
            code = parsed?.error?.code ?: "HTTP_${e.code()}",
            message = parsed?.error?.message ?: (e.message() ?: "HTTP ${e.code()}"),
            statusCode = e.code(),
        )
    }
}