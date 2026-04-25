package com.example.securechatapp.data.repository

import com.example.securechatapp.core.network.ApiErrorEnvelopeDto
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
    val message = buildString {
        append(
            parsed?.error?.message ?: exception.message().orEmpty().ifBlank {
                "HTTP ${exception.code()}"
            }
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
        } catch (e: HttpException) {
            throw parseBackendApiException(json, e)
        }
    }
}
