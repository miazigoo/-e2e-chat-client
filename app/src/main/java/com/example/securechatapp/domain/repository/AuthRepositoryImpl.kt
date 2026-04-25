package com.example.securechatapp.data.repository

import android.os.Build
import com.example.securechatapp.core.network.ApiErrorEnvelopeDto
import com.example.securechatapp.core.result.AppResult
import com.example.securechatapp.crypto.signal.SignalBootstrapKeyMaterialProvider
import com.example.securechatapp.data.local.preferences.SecureSessionLocalDataSource
import com.example.securechatapp.data.remote.api.AuthApi
import com.example.securechatapp.data.remote.dto.auth.BootstrapDeviceRequestDto
import com.example.securechatapp.data.remote.dto.auth.Google2FAConfirmRequestDto
import com.example.securechatapp.data.remote.dto.auth.LoginRequestDto
import com.example.securechatapp.data.remote.dto.auth.OneTimePreKeyDto
import com.example.securechatapp.data.remote.dto.auth.RegisterRequestDto
import com.example.securechatapp.data.remote.dto.auth.VerifyEmailCodeRequestDto
import com.example.securechatapp.domain.model.Google2faSetupResult
import com.example.securechatapp.domain.model.Google2faStatusResult
import com.example.securechatapp.domain.model.LoginResult
import com.example.securechatapp.domain.model.RegisterResult
import com.example.securechatapp.domain.model.VerifyEmailCodeResult
import com.example.securechatapp.domain.repository.AuthRepository
import javax.inject.Inject
import kotlinx.serialization.json.Json
import retrofit2.HttpException


class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val sessionLocalDataSource: SecureSessionLocalDataSource,
    private val signalBootstrapKeyMaterialProvider: SignalBootstrapKeyMaterialProvider,
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
        totpCode: String?,
    ): AppResult<LoginResult> {
        val stableDeviceUuid = deviceUuid ?: sessionLocalDataSource.getOrCreateDeviceUuid()

        return try {
            var bootstrapAttempts = 0

            while (true) {
                val response = authApi.login(
                    LoginRequestDto(
                        nickname = nickname,
                        password = password,
                        deviceUuid = stableDeviceUuid,
                        totpCode = totpCode?.trim()?.takeIf { it.isNotEmpty() },
                    )
                )

                val data = response.data
                when (val bootstrapResult = maybeBootstrapDevice(
                    requiresBootstrap = data.requiresBootstrap,
                    bootstrapToken = data.bootstrapToken,
                    deviceUuid = stableDeviceUuid,
                    bootstrapAttempts = bootstrapAttempts,
                )) {
                    BootstrapResult.NotRequired -> {
                        persistSessionIfPresent(
                            accessToken = data.accessToken,
                            refreshToken = data.refreshToken,
                            deviceUuid = stableDeviceUuid,
                        )

                        return AppResult.Success(
                            LoginResult(
                                requiresEmailCode = data.requiresEmailCode,
                                requiresTotp = data.requiresTotp,
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
                    }

                    BootstrapResult.Retried -> bootstrapAttempts++
                    is BootstrapResult.Error -> return bootstrapResult.result
                }
            }

            error("Unreachable login bootstrap state")
        } catch (e: HttpException) {
            parseHttpError(e)
        } catch (e: Exception) {
            AppResult.Error(
                code = "NETWORK_ERROR",
                message = e.message ?: "Unknown network error",
            )
        }
    }

    suspend fun beginGoogle2faSetup(): AppResult<Google2faSetupResult> {
        return try {
            val data = authApi.beginGoogle2faSetup().data
            AppResult.Success(
                Google2faSetupResult(
                    secret = data.secret,
                    provisioningUri = data.provisioningUri,
                    issuer = data.issuer,
                    accountName = data.accountName,
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

    suspend fun confirmGoogle2faSetup(code: String): AppResult<Google2faStatusResult> {
        return try {
            val data = authApi.confirmGoogle2faSetup(
                Google2FAConfirmRequestDto(code = code.trim()),
            ).data
            AppResult.Success(
                Google2faStatusResult(
                    enabled = data.enabled,
                    confirmedAt = data.confirmedAt,
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

    suspend fun disableGoogle2fa(): AppResult<Google2faStatusResult> {
        return try {
            val data = authApi.disableGoogle2fa().data
            AppResult.Success(
                Google2faStatusResult(
                    enabled = data.enabled,
                    confirmedAt = data.confirmedAt,
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
            var bootstrapAttempts = 0

            while (true) {
                val response = authApi.verifyEmailCode(
                    VerifyEmailCodeRequestDto(
                        loginChallengeId = loginChallengeId,
                        code = code,
                        deviceUuid = stableDeviceUuid,
                    )
                )

                val data = response.data
                when (val bootstrapResult = maybeBootstrapDevice(
                    requiresBootstrap = data.requiresBootstrap,
                    bootstrapToken = data.bootstrapToken,
                    deviceUuid = stableDeviceUuid,
                    bootstrapAttempts = bootstrapAttempts,
                )) {
                    BootstrapResult.NotRequired -> {
                        persistSessionIfPresent(
                            accessToken = data.accessToken,
                            refreshToken = data.refreshToken,
                            deviceUuid = stableDeviceUuid,
                        )

                        return AppResult.Success(
                            VerifyEmailCodeResult(
                                requiresBootstrap = data.requiresBootstrap,
                                bootstrapToken = data.bootstrapToken,
                                bootstrapExpiresIn = data.bootstrapExpiresIn,
                                accessToken = data.accessToken,
                                refreshToken = data.refreshToken,
                                expiresIn = data.expiresIn,
                            )
                        )
                    }

                    BootstrapResult.Retried -> bootstrapAttempts++
                    is BootstrapResult.Error -> return bootstrapResult.result
                }
            }

            error("Unreachable verifyEmailCode bootstrap state")
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
        val signalMaterial = signalBootstrapKeyMaterialProvider.getOrCreateBootstrapMaterial(
            oneTimePreKeyCount = 100,
        )

        authApi.bootstrap(
            authorization = "Bearer $bootstrapToken",
            body = BootstrapDeviceRequestDto(
                deviceUuid = deviceUuid,
                deviceName = deviceName.ifBlank { "Android device" },
                platform = "android",
                appVersion = "1.0.0",
                registrationId = signalMaterial.registrationId,
                publicIdentityKey = signalMaterial.publicIdentityKey,
                publicSigningKey = signalMaterial.publicSigningKey,
                signedPrekeyId = signalMaterial.signedPreKeyId,
                signedPrekey = signalMaterial.signedPreKey,
                signedPrekeySignature = signalMaterial.signedPreKeySignature,
                oneTimePrekeys = signalMaterial.oneTimePreKeys,
            )
        )

        sessionLocalDataSource.saveDeviceUuid(deviceUuid)
    }

    private suspend fun maybeBootstrapDevice(
        requiresBootstrap: Boolean,
        bootstrapToken: String?,
        deviceUuid: String,
        bootstrapAttempts: Int,
    ): BootstrapResult {
        if (!requiresBootstrap) return BootstrapResult.NotRequired

        val normalizedToken = bootstrapToken?.trim().takeIf { !it.isNullOrEmpty() }
            ?: return BootstrapResult.Error(
                AppResult.Error(
                    code = "BOOTSTRAP_TOKEN_MISSING",
                    message = "Device bootstrap was requested without a bootstrap token",
                )
            )

        if (bootstrapAttempts >= MAX_BOOTSTRAP_ATTEMPTS) {
            return BootstrapResult.Error(
                AppResult.Error(
                    code = "BOOTSTRAP_RETRY_LIMIT",
                    message = "Device bootstrap retry limit reached",
                )
            )
        }

        bootstrapDevice(
            bootstrapToken = normalizedToken,
            deviceUuid = deviceUuid,
        )
        return BootstrapResult.Retried
    }

    private fun persistSessionIfPresent(
        accessToken: String?,
        refreshToken: String?,
        deviceUuid: String,
    ) {
        if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank()) return

        sessionLocalDataSource.saveFullSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            deviceUuid = deviceUuid,
        )
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

    private sealed interface BootstrapResult {
        data object NotRequired : BootstrapResult
        data object Retried : BootstrapResult
        data class Error(val result: AppResult.Error) : BootstrapResult
    }

    private companion object {
        const val MAX_BOOTSTRAP_ATTEMPTS = 2
    }
}
