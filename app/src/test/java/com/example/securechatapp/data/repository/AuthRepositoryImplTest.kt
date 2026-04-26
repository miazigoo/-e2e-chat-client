package com.example.securechatapp.data.repository

import com.example.securechatapp.core.network.ApiEnvelopeDto
import com.example.securechatapp.core.result.AppResult
import com.example.securechatapp.crypto.signal.SignalBootstrapKeyMaterial
import com.example.securechatapp.crypto.signal.SignalBootstrapKeyMaterialProvider
import com.example.securechatapp.data.local.preferences.SecureSessionLocalDataSource
import com.example.securechatapp.data.remote.api.AuthApi
import com.example.securechatapp.data.remote.dto.auth.BootstrapDeviceResponseDto
import com.example.securechatapp.data.remote.dto.auth.LoginResponseDto
import com.example.securechatapp.data.remote.dto.auth.OneTimePreKeyDto
import com.example.securechatapp.data.remote.dto.auth.VerifyEmailCodeResponseDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class AuthRepositoryImplTest {

    private val authApi = mockk<AuthApi>()
    private val sessionLocalDataSource = mockk<SecureSessionLocalDataSource>(relaxed = true)
    private val signalBootstrapKeyMaterialProvider = mockk<SignalBootstrapKeyMaterialProvider>()
    private val json = Json { ignoreUnknownKeys = true }

    private val repository = AuthRepositoryImpl(
        authApi = authApi,
        sessionLocalDataSource = sessionLocalDataSource,
        signalBootstrapKeyMaterialProvider = signalBootstrapKeyMaterialProvider,
        json = json,
    )

    @Test
    fun `login bootstraps device and retries until tokens are received`() = runTest {
        val deviceUuid = "device-uuid"
        every { sessionLocalDataSource.getOrCreateDeviceUuid() } returns deviceUuid
        coEvery {
            signalBootstrapKeyMaterialProvider.getOrCreateBootstrapMaterial(100)
        } returns bootstrapMaterial()
        coEvery { authApi.login(any()) } returnsMany listOf(
            envelope(
                LoginResponseDto(
                    requiresEmailCode = false,
                    requiresBootstrap = true,
                    bootstrapToken = "bootstrap-token",
                    bootstrapExpiresIn = 300,
                )
            ),
            envelope(
                LoginResponseDto(
                    requiresEmailCode = false,
                    requiresBootstrap = false,
                    accessToken = "access-token",
                    refreshToken = "refresh-token",
                    expiresIn = 3600,
                )
            ),
        )
        coEvery { authApi.bootstrap(any(), any()) } returns envelope(
            BootstrapDeviceResponseDto(
                deviceId = 1,
                deviceUuid = deviceUuid,
                isActive = true,
                prekeysCount = 100,
            )
        )

        val result = repository.login(
            nickname = "@alice",
            password = "password123",
            deviceUuid = null,
        )

        require(result is AppResult.Success)
        assertEquals("access-token", result.data.accessToken)
        assertEquals("refresh-token", result.data.refreshToken)
        coVerify(exactly = 2) { authApi.login(any()) }
        coVerify(exactly = 1) { authApi.bootstrap("Bearer bootstrap-token", any()) }
        verify(exactly = 1) {
            sessionLocalDataSource.saveDeviceUuid(deviceUuid)
        }
        verify(exactly = 1) {
            sessionLocalDataSource.saveFullSession(
                accessToken = "access-token",
                refreshToken = "refresh-token",
                deviceUuid = deviceUuid,
            )
        }
    }

    @Test
    fun `verifyEmailCode saves full session when tokens are returned`() = runTest {
        coEvery {
            authApi.verifyEmailCode(any())
        } returns envelope(
            VerifyEmailCodeResponseDto(
                requiresBootstrap = false,
                accessToken = "access-token",
                refreshToken = "refresh-token",
                expiresIn = 3600,
            )
        )

        val result = repository.verifyEmailCode(
            loginChallengeId = "challenge-1",
            code = "123456",
            deviceUuid = "device-uuid",
        )

        require(result is AppResult.Success)
        assertEquals("access-token", result.data.accessToken)
        verify(exactly = 1) {
            sessionLocalDataSource.saveFullSession(
                accessToken = "access-token",
                refreshToken = "refresh-token",
                deviceUuid = "device-uuid",
            )
        }
    }

    @Test
    fun `verifyEmailCode bootstraps device and retries until tokens are received`() = runTest {
        val deviceUuid = "device-uuid"
        coEvery {
            signalBootstrapKeyMaterialProvider.getOrCreateBootstrapMaterial(100)
        } returns bootstrapMaterial()
        coEvery { authApi.verifyEmailCode(any()) } returnsMany listOf(
            envelope(
                VerifyEmailCodeResponseDto(
                    requiresBootstrap = true,
                    bootstrapToken = "bootstrap-token",
                    bootstrapExpiresIn = 300,
                )
            ),
            envelope(
                VerifyEmailCodeResponseDto(
                    requiresBootstrap = false,
                    accessToken = "access-token",
                    refreshToken = "refresh-token",
                    expiresIn = 3600,
                )
            ),
        )
        coEvery { authApi.bootstrap(any(), any()) } returns envelope(
            BootstrapDeviceResponseDto(
                deviceId = 1,
                deviceUuid = deviceUuid,
                isActive = true,
                prekeysCount = 100,
            )
        )

        val result = repository.verifyEmailCode(
            loginChallengeId = "challenge-1",
            code = "123456",
            deviceUuid = deviceUuid,
        )

        require(result is AppResult.Success)
        assertEquals("access-token", result.data.accessToken)
        coVerify(exactly = 2) { authApi.verifyEmailCode(any()) }
        coVerify(exactly = 1) { authApi.bootstrap("Bearer bootstrap-token", any()) }
        verify(exactly = 1) { sessionLocalDataSource.saveDeviceUuid(deviceUuid) }
        verify(exactly = 1) {
            sessionLocalDataSource.saveFullSession(
                accessToken = "access-token",
                refreshToken = "refresh-token",
                deviceUuid = deviceUuid,
            )
        }
    }

    @Test
    fun `login fails when bootstrap is requested without token`() = runTest {
        coEvery { authApi.login(any()) } returns envelope(
            LoginResponseDto(
                requiresEmailCode = false,
                requiresBootstrap = true,
                bootstrapToken = null,
                bootstrapExpiresIn = 300,
            )
        )

        val result = repository.login(
            nickname = "@alice",
            password = "password123",
            deviceUuid = "device-uuid",
        )

        require(result is AppResult.Error)
        assertEquals("BOOTSTRAP_TOKEN_MISSING", result.code)
        coVerify(exactly = 0) { authApi.bootstrap(any(), any()) }
    }

    @Test
    fun `login stops after bootstrap retry limit`() = runTest {
        val deviceUuid = "device-uuid"
        coEvery {
            signalBootstrapKeyMaterialProvider.getOrCreateBootstrapMaterial(100)
        } returns bootstrapMaterial()
        coEvery { authApi.login(any()) } returnsMany listOf(
            envelope(
                LoginResponseDto(
                    requiresEmailCode = false,
                    requiresBootstrap = true,
                    bootstrapToken = "bootstrap-token-1",
                    bootstrapExpiresIn = 300,
                )
            ),
            envelope(
                LoginResponseDto(
                    requiresEmailCode = false,
                    requiresBootstrap = true,
                    bootstrapToken = "bootstrap-token-2",
                    bootstrapExpiresIn = 300,
                )
            ),
            envelope(
                LoginResponseDto(
                    requiresEmailCode = false,
                    requiresBootstrap = true,
                    bootstrapToken = "bootstrap-token-3",
                    bootstrapExpiresIn = 300,
                )
            ),
        )
        coEvery { authApi.bootstrap(any(), any()) } returns envelope(
            BootstrapDeviceResponseDto(
                deviceId = 1,
                deviceUuid = deviceUuid,
                isActive = true,
                prekeysCount = 100,
            )
        )

        val result = repository.login(
            nickname = "@alice",
            password = "password123",
            deviceUuid = deviceUuid,
        )

        require(result is AppResult.Error)
        assertEquals("BOOTSTRAP_RETRY_LIMIT", result.code)
        coVerify(exactly = 3) { authApi.login(any()) }
        coVerify(exactly = 2) { authApi.bootstrap(any(), any()) }
    }

    @Test
    fun `login returns parsed backend error`() = runTest {
        coEvery { authApi.login(any()) } throws httpException(
            code = 403,
            body = """{"ok":false,"error":{"code":"EMAIL_CODE_REQUIRED","message":"Email code required"}}""",
        )

        val result = repository.login(
            nickname = "@alice",
            password = "password123",
            deviceUuid = "device-uuid",
        )

        require(result is AppResult.Error)
        assertEquals("EMAIL_CODE_REQUIRED", result.code)
        assertEquals("Email code required", result.message)
        assertEquals(403, result.statusCode)
    }

    @Test
    fun `register returns network error on unexpected exception`() = runTest {
        coEvery { authApi.register(any()) } throws IllegalStateException("network down")

        val result = repository.register(
            nickname = "@alice",
            password = "password123",
            email = null,
            email2faEnabled = false,
        )

        require(result is AppResult.Error)
        assertEquals("UNEXPECTED_ERROR", result.code)
        assertEquals("network down", result.message)
    }

    @Test
    fun `register returns network error on io exception`() = runTest {
        coEvery { authApi.register(any()) } coAnswers { throw IOException("timeout") }

        val result = repository.register(
            nickname = "@alice",
            password = "password123",
            email = null,
            email2faEnabled = false,
        )

        require(result is AppResult.Error)
        assertEquals("NETWORK_ERROR", result.code)
        assertEquals("timeout", result.message)
    }

    @Test
    fun `login includes raw backend body when error envelope is malformed`() = runTest {
        coEvery { authApi.login(any()) } throws httpException(
            code = 502,
            body = """{"message":"gateway exploded"}""",
        )

        val result = repository.login(
            nickname = "@alice",
            password = "password123",
            deviceUuid = "device-uuid",
        )

        require(result is AppResult.Error)
        assertEquals("HTTP_502", result.code)
        assertTrue(result.message.contains("HTTP 502"))
        assertTrue(result.message.contains("gateway exploded"))
    }

    private fun bootstrapMaterial() = SignalBootstrapKeyMaterial(
        registrationId = 321,
        publicIdentityKey = "identity-key",
        publicSigningKey = "signing-key",
        signedPreKeyId = 99,
        signedPreKey = "signed-pre-key",
        signedPreKeySignature = "signature",
        oneTimePreKeys = listOf(
            OneTimePreKeyDto(
                prekeyId = 1,
                publicPrekey = "pre-key",
            )
        ),
    )

    private fun <T> envelope(data: T): ApiEnvelopeDto<T> =
        ApiEnvelopeDto(ok = true, data = data, meta = null)

    private fun httpException(
        code: Int,
        body: String,
    ): HttpException {
        return HttpException(
            Response.error<Unit>(
                code,
                body.toResponseBody("application/json".toMediaType()),
            ),
        )
    }
}
