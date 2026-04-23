package com.example.securechatapp.data.repository

import com.example.securechatapp.core.network.ApiErrorEnvelopeDto
import kotlinx.serialization.json.Json
import retrofit2.HttpException

abstract class BaseApiRepository(
    private val json: Json,
) {
    protected suspend fun <T> safe(block: suspend () -> T): T {
        try {
            return block()
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string()
            val parsed = body?.let {
                runCatching {
                    json.decodeFromString(ApiErrorEnvelopeDto.serializer(), it)
                }.getOrNull()
            }

            val fallbackMessage = buildString {
                append(
                    parsed?.error?.message ?: e.message().orEmpty().ifBlank {
                        "HTTP ${e.code()}"
                    }
                )
                if (!body.isNullOrBlank() && parsed == null) {
                    append(": ")
                    append(body)
                }
            }

            throw IllegalStateException(fallbackMessage)
        }
    }
}
