package com.example.securechatapp.data.repository

import android.os.Build
import android.util.Log
import com.example.securechatapp.core.result.AppResult
import com.example.securechatapp.crypto.signal.SignalBootstrapKeyMaterialProvider
import com.example.securechatapp.data.local.preferences.SecureSessionLocalDataSource
import com.example.securechatapp.data.remote.api.AuthApi
import com.example.securechatapp.data.repository.parseBackendApiException
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
import java.io.IOException
import javax.inject.Inject
import kotlinx.serialization.json.Json
import retrofit2.HttpException


class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val sessionLocalDataSource: SecureSessionLocalDataSource,
    private val signalBootstrapKeyMaterialProvider: SignalBootstrapKeyMaterialProvider,
    private val json: Json,
) : AuthRepository {

    private val logTag = "AuthRepository"

    override suspend fun register(
        nickname: String,
        password: String,
        email: String?,
        email2faEnabled: Boolean,
    ): AppResult<RegisterResult> {
        return try {
            debugLog("register: sending auth/register for $nickname")
            val response = authApi.register(
                RegisterRequestDto(
                    nickname = nickname,
                    password = password,
                    email = email,
                    email2faEnabled = email2faEnabled,
                )
            )

            debugLog("register: auth/register completed, resolving device UUID")
            val deviceUuid = sessionLocalDataSource.getOrCreateDeviceUuid()
            debugLog(
                "register: device UUID ready, bootstrap token present=${!response.data.bootstrapToken.isNullOrBlank()}",
            )
            response.data.bootstrapToken?.takeIf { it.isNotBlank() }?.let { token ->
                debugLog("register: starting auth/bootstrap")
                bootstrapDevice(
                    bootstrapToken = token,
                    deviceUuid = deviceUuid,
                )
                debugLog("register: auth/bootstrap completed")
            }

            debugLog("register: building success result")
            AppResult.Success(
                RegisterResult(
                    userId = response.data.userId,
                    nickname = response.data.nickname.ifBlank { nickname },
                    requiresDeviceRegistration = response.data.requiresDeviceRegistration,
                    bootstrapToken = response.data.bootstrapToken,
                    bootstrapExpiresIn = response.data.bootstrapExpiresIn,
                )
            )
        } catch (e: HttpException) {
            errorLog("register HTTP failure", e)
            parseHttpError(e)
        } catch (e: IOException) {
            errorLog("register network failure", e)
            AppResult.Error(
                code = "NETWORK_ERROR",
                message = normalizeErrorMessage(e.message, "Network request failed"),
            )
        } catch (e: Exception) {
            errorLog(
                "register unexpected failure: ${e::class.java.name}: ${e.message.orEmpty()}",
                e,
            )
            AppResult.Error(
                code = "UNEXPECTED_ERROR",
                message = normalizeErrorMessage(e.message, "Unexpected request failure"),
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
                debugLog(
                    "login: response requiresEmailCode=${data.requiresEmailCode} " +
                            "requiresTotp=${data.requiresTotp} " +
                            "requiresBootstrap=${data.requiresBootstrap} " +
                            "challengeIdPresent=${!data.loginChallengeId.isNullOrBlank()} " +
                            "emailMasked=${data.emailMasked ?: "<none>"} " +
                            "debugCodePresent=${!data.debugCode.isNullOrBlank()} " +
                            "accessTokenPresent=${!data.accessToken.isNullOrBlank()}",
                )
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
        } catch (e: IOException) {
            AppResult.Error(
                code = "NETWORK_ERROR",
                message = normalizeErrorMessage(e.message, "Network request failed"),
            )
        } catch (e: Exception) {
            AppResult.Error(
                code = "UNEXPECTED_ERROR",
                message = normalizeErrorMessage(e.message, "Unexpected request failure"),
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
        } catch (e: IOException) {
            AppResult.Error(
                code = "NETWORK_ERROR",
                message = normalizeErrorMessage(e.message, "Network request failed"),
            )
        } catch (e: Exception) {
            AppResult.Error(
                code = "UNEXPECTED_ERROR",
                message = normalizeErrorMessage(e.message, "Unexpected request failure"),
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
        } catch (e: IOException) {
            AppResult.Error(
                code = "NETWORK_ERROR",
                message = normalizeErrorMessage(e.message, "Network request failed"),
            )
        } catch (e: Exception) {
            AppResult.Error(
                code = "UNEXPECTED_ERROR",
                message = normalizeErrorMessage(e.message, "Unexpected request failure"),
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
        } catch (e: IOException) {
            AppResult.Error(
                code = "NETWORK_ERROR",
                message = normalizeErrorMessage(e.message, "Network request failed"),
            )
        } catch (e: Exception) {
            AppResult.Error(
                code = "UNEXPECTED_ERROR",
                message = normalizeErrorMessage(e.message, "Unexpected request failure"),
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
            debugLog(
                "verifyEmailCode: response requiresBootstrap=${data.requiresBootstrap} " +
                        "bootstrapTokenPresent=${!data.bootstrapToken.isNullOrBlank()} " +
                        "accessTokenPresent=${!data.accessToken.isNullOrBlank()}",
            )

            when (val bootstrapResult = maybeBootstrapDevice(
                requiresBootstrap = data.requiresBootstrap,
                bootstrapToken = data.bootstrapToken,
                deviceUuid = stableDeviceUuid,
                bootstrapAttempts = 0,
            )) {
                BootstrapResult.NotRequired -> Unit
                BootstrapResult.Retried -> {
                    debugLog("verifyEmailCode: device bootstrap completed, re-login required")
                }
                is BootstrapResult.Error -> return bootstrapResult.result
            }

            persistSessionIfPresent(
                accessToken = data.accessToken,
                refreshToken = data.refreshToken,
                deviceUuid = stableDeviceUuid,
            )

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
        } catch (e: IOException) {
            AppResult.Error(
                code = "NETWORK_ERROR",
                message = normalizeErrorMessage(e.message, "Network request failed"),
            )
        } catch (e: Exception) {
            AppResult.Error(
                code = "UNEXPECTED_ERROR",
                message = normalizeErrorMessage(e.message, "Unexpected request failure"),
            )
        }
    }

    private suspend fun bootstrapDevice(
        bootstrapToken: String,
        deviceUuid: String,
    ) {
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        debugLog("bootstrapDevice: requesting Signal bootstrap material")
        val signalMaterial = signalBootstrapKeyMaterialProvider.getOrCreateBootstrapMaterial(
            oneTimePreKeyCount = 100,
        )
        debugLog(
            "bootstrapDevice: material ready registrationId=${signalMaterial.registrationId} preKeys=${signalMaterial.oneTimePreKeys.size}",
        )

        debugLog("bootstrapDevice: sending auth/bootstrap")
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
        debugLog("bootstrapDevice: auth/bootstrap returned successfully")

        debugLog("bootstrapDevice: saving device UUID")
        sessionLocalDataSource.saveDeviceUuid(deviceUuid)
        debugLog("bootstrapDevice: device UUID saved")
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
        val backendError = parseBackendApiException(json, e)

        return AppResult.Error(
            code = backendError.code,
            message = localizeBackendMessage(
                code = backendError.code,
                message = backendError.message,
                statusCode = backendError.statusCode,
                fallback = "HTTP ${e.code()}",
            ),
            statusCode = backendError.statusCode,
        )
    }

    private fun normalizeErrorMessage(message: String?, fallback: String): String =
        message?.trim().takeUnless { it.isNullOrEmpty() } ?: fallback

    private fun localizeBackendMessage(
        code: String,
        message: String?,
        statusCode: Int?,
        fallback: String,
    ): String {
        val normalized = message?.trim().orEmpty()
        if (normalized.isEmpty()) {
            return when (statusCode) {
                500 -> "Внутренняя ошибка сервера. Попробуйте позже."
                503 -> "Сервис временно недоступен. Попробуйте позже."
                else -> fallback
            }
        }

        return when {
            normalized.equals("Could not deliver verification code", ignoreCase = true) ->
                "Сервер не смог отправить код подтверждения. Попробуйте позже."

            normalized.equals("Internal server error", ignoreCase = true) ->
                "Внутренняя ошибка сервера. Попробуйте позже."

            statusCode == 503 && normalized.equals("Service Unavailable", ignoreCase = true) ->
                "Сервис временно недоступен. Попробуйте позже."

            code == "HTTP_500" && normalized.equals("HTTP 500", ignoreCase = true) ->
                "Внутренняя ошибка сервера. Попробуйте позже."

            code == "HTTP_503" && normalized.equals("HTTP 503", ignoreCase = true) ->
                "Сервис временно недоступен. Попробуйте позже."

            else -> normalized
        }
    }

    private fun debugLog(message: String) {
        runCatching { Log.d(logTag, message) }
    }

    private fun errorLog(message: String, throwable: Throwable) {
        runCatching { Log.e(logTag, message, throwable) }
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
