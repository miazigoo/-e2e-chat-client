package com.example.securechatapp.core.network

import com.example.securechatapp.data.local.preferences.SessionLocalDataSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

class AuthAuthenticator(
    private val baseUrl: String,
    private val sessionLocalDataSource: SessionLocalDataSource,
    private val json: Json,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null

        val currentSession = runBlocking { sessionLocalDataSource.getSessionSnapshot() } ?: return null
        val refreshToken = currentSession.refreshToken ?: return null

        val refreshUrl = "${baseUrl.trimEnd('/')}/auth/refresh"

        val client = OkHttpClient.Builder().build()

        val requestBodyJson = """{"refresh_token":"$refreshToken"}"""
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(refreshUrl)
            .post(requestBodyJson)
            .header("Content-Type", "application/json")
            .build()

        return try {
            client.newCall(request).execute().use { refreshResponse ->
                if (!refreshResponse.isSuccessful) {
                    runBlocking { sessionLocalDataSource.clearSession() }
                    return null
                }

                val raw = refreshResponse.body?.string().orEmpty()
                if (raw.isBlank()) {
                    runBlocking { sessionLocalDataSource.clearSession() }
                    return null
                }

                val parsed = runCatching {
                    json.decodeFromString(
                        RefreshEnvelopeDto.serializer(),
                        raw,
                    )
                }.getOrNull() ?: run {
                    runBlocking { sessionLocalDataSource.clearSession() }
                    return null
                }

                runBlocking {
                    sessionLocalDataSource.updateTokens(
                        accessToken = parsed.data.accessToken,
                        refreshToken = parsed.data.refreshToken,
                    )
                }

                response.request.newBuilder()
                    .header("Authorization", "Bearer ${parsed.data.accessToken}")
                    .build()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}

@Serializable
private data class RefreshEnvelopeDto(
    val ok: Boolean,
    val data: RefreshTokensDto,
)

@Serializable
private data class RefreshTokensDto(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("expires_in")
    val expiresIn: Int,
)
