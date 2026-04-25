package com.example.securechatapp.data.repository

import com.example.securechatapp.data.local.preferences.SecureSessionLocalDataSource
import com.example.securechatapp.data.remote.api.ChatBackendApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SessionRepositoryTest {

    private val api = mockk<ChatBackendApi>()
    private val sessionStore = mockk<SecureSessionLocalDataSource>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }

    private val repository = SessionRepository(
        api = api,
        sessionStore = sessionStore,
        json = json,
    )

    @Test
    fun `heartbeat clears local session on device session mismatch`() = runTest {
        coEvery { api.sendHeartbeat() } throws httpException(
            code = 403,
            body = """{"ok":false,"error":{"code":"DEVICE_SESSION_MISMATCH","message":"Device does not match the active session"}}""",
        )

        val result = repository.heartbeat()

        assertNull(result)
        coVerify(exactly = 1) { sessionStore.clearSession(keepDeviceUuid = true) }
    }

    @Test
    fun `updateFcmToken does not clear session on generic backend failure`() = runTest {
        coEvery { api.updateFcmToken(any()) } throws httpException(
            code = 429,
            body = """{"ok":false,"error":{"code":"RATE_LIMITED","message":"Too many requests"}}""",
        )

        repository.updateFcmToken("token")

        coVerify(exactly = 0) { sessionStore.clearSession(any()) }
    }

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
