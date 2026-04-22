package com.example.securechatapp.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiMetaDto(
    @SerialName("request_id")
    val requestId: String? = null,
)

@Serializable
data class ApiErrorBodyDto(
    val code: String,
    val message: String,
)

@Serializable
data class ApiErrorEnvelopeDto(
    val ok: Boolean,
    val error: ApiErrorBodyDto,
    val meta: ApiMetaDto? = null,
)

@Serializable
data class ApiEnvelopeDto<T>(
    val ok: Boolean,
    val data: T,
    val meta: ApiMetaDto? = null,
)
