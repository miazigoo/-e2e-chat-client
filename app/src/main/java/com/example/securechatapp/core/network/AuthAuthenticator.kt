package com.example.securechatapp.core.network

import com.example.securechatapp.data.local.preferences.SecureSessionLocalDataSource
import java.util.concurrent.TimeUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

class AuthAuthenticator(
    private val baseUrl: String,
    private val sessionLocalDataSource: SecureSessionLocalDataSource,
    private val json: Json,
) : Authenticator {

    private val refreshLock = Any()

    private val refreshClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        if (response.request.url.encodedPath.endsWith("/auth/refresh")) return null

        synchronized(refreshLock) {
            val latestSession = sessionLocalDataSource.getSessionSnapshot()
            val latestAccessToken = latestSession.accessToken?.takeIf { it.isNotBlank() }
            val requestAccessToken = response.request.bearerToken()

            if (!latestAccessToken.isNullOrBlank() && latestAccessToken != requestAccessToken) {
                return buildRetriedRequest(
                    originalRequest = response.request,
                    accessToken = latestAccessToken,
                    deviceUuid = latestSession.deviceUuid,
                )
            }

            val refreshToken = latestSession.refreshToken?.takeIf { it.isNotBlank() } ?: return null
            val refreshed = refreshTokens(
                refreshToken = refreshToken,
                deviceUuid = latestSession.deviceUuid,
            ) ?: return null

            return buildRetriedRequest(
                originalRequest = response.request,
                accessToken = refreshed.accessToken,
                deviceUuid = latestSession.deviceUuid,
            )
        }
    }

    private fun refreshTokens(
        refreshToken: String,
        deviceUuid: String?,
    ): RefreshTokensDto? {
        val requestBodyJson = json.encodeToString(
            RefreshRequestDto(refreshToken = refreshToken),
        ).toRequestBody("application/json".toMediaType())

        val refreshRequest = Request.Builder()
            .url(
                baseUrl.toHttpUrl()
                    .newBuilder()
                    .addPathSegment("auth")
                    .addPathSegment("refresh")
                    .build(),
            )
            .post(requestBodyJson)
            .header("Content-Type", "application/json")
            .apply {
                deviceUuid?.takeIf { it.isNotBlank() }?.let { header("X-Device-UUID", it) }
            }
            .build()

        return runCatching {
            refreshClient.newCall(refreshRequest).execute().use { refreshResponse ->
                if (!refreshResponse.isSuccessful) {
                    sessionLocalDataSource.clearSession()
                    return null
                }

                val raw = refreshResponse.body?.string().orEmpty()
                if (raw.isBlank()) {
                    sessionLocalDataSource.clearSession()
                    return null
                }

                val parsed = json.decodeFromString(
                    RefreshEnvelopeDto.serializer(),
                    raw,
                )

                if (!parsed.ok) {
                    sessionLocalDataSource.clearSession()
                    return null
                }

                sessionLocalDataSource.updateTokens(
                    accessToken = parsed.data.accessToken,
                    refreshToken = parsed.data.refreshToken,
                )
                parsed.data
            }
        }.getOrElse {
            null
        }
    }

    private fun buildRetriedRequest(
        originalRequest: Request,
        accessToken: String,
        deviceUuid: String?,
    ): Request {
        return originalRequest.newBuilder()
            .header("Authorization", "Bearer $accessToken")
            .apply {
                deviceUuid?.takeIf { it.isNotBlank() }?.let { header("X-Device-UUID", it) }
            }
            .build()
    }

    private fun Request.bearerToken(): String? {
        return header("Authorization")
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
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
private data class RefreshRequestDto(
    @SerialName("refresh_token")
    val refreshToken: String,
)

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
