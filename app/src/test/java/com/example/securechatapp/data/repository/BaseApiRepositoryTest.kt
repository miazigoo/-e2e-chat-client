package com.example.securechatapp.data.repository

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class BaseApiRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `safe returns parsed backend error message`() = runTest {
        val repository = TestRepository(json)

        val error = runCatching {
            repository.runSafe<Unit> {
                throw httpException(
                    code = 401,
                    body = """{"ok":false,"error":{"code":"TOKEN_EXPIRED","message":"Token expired"}}""",
                )
            }
        }.exceptionOrNull()

        require(error is BackendApiException)
        assertEquals("TOKEN_EXPIRED", error.code)
        assertEquals("Token expired", error.message)
    }

    @Test
    fun `safe includes raw body when backend error envelope is invalid`() = runTest {
        val repository = TestRepository(json)

        val error = runCatching {
            repository.runSafe<Unit> {
                throw httpException(
                    code = 500,
                    body = "upstream gateway exploded",
                )
            }
        }.exceptionOrNull()

        require(error is BackendApiException)
        assertEquals("HTTP_500", error.code)
        assertTrue(error.message.orEmpty().contains("upstream gateway exploded"))
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

    private class TestRepository(
        json: Json,
    ) : BaseApiRepository(json) {
        suspend fun <T> runSafe(block: suspend () -> T): T = safe(block)
    }
}
