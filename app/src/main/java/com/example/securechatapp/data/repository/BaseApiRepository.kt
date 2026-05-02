package com.example.securechatapp.data.repository

import com.example.securechatapp.core.network.ApiErrorEnvelopeDto
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

class BackendApiException(
    val code: String,
    override val message: String,
    val statusCode: Int? = null,
) : IllegalStateException(message)

fun parseBackendApiException(
    json: Json,
    exception: HttpException,
): BackendApiException {
    val body = exception.response()?.errorBody()?.string()
    val parsed = body?.let {
        runCatching {
            json.decodeFromString(ApiErrorEnvelopeDto.serializer(), it)
        }.getOrNull()
    }

    val code = parsed?.error?.code ?: "HTTP_${exception.code()}"
    val fallbackMessage = "HTTP ${exception.code()}"
    val retrofitMessage = exception.response()?.message()
        ?.takeUnless { it.isBlank() || it == "Response.error()" }
    val message = buildString {
        append(
            parsed?.error?.message
                ?: retrofitMessage
                ?: fallbackMessage
        )
        if (!body.isNullOrBlank() && parsed == null) {
            append(": ")
            append(body)
        }
    }

    return BackendApiException(
        code = code,
        message = message,
        statusCode = exception.code(),
    )
}

abstract class BaseApiRepository(
    private val json: Json,
) {
    protected suspend fun <T> safe(block: suspend () -> T): T {
        try {
            return block()
        } catch (e: IOException) {
            throw BackendApiException(
                code = "NETWORK_ERROR",
                message = localizeNetworkErrorMessage(e),
            )
        } catch (e: HttpException) {
            throw parseBackendApiException(json, e)
        }
    }

    private fun localizeNetworkErrorMessage(error: IOException): String {
        return when {
            error is SocketTimeoutException -> "Сервер не ответил вовремя. Попробуй позже."
            error.message.equals("Read timed out", ignoreCase = true) ->
                "Сервер не ответил вовремя. Попробуй позже."
            error.message.equals("timeout", ignoreCase = true) ->
                "Сервер не ответил вовремя. Попробуй позже."
            else -> error.message ?: "Network request failed"
        }
    }
}

fun Throwable.isRecoverableNetworkUncertainty(): Boolean {
    return this is BackendApiException && code == "NETWORK_ERROR"
}
