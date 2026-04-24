package com.example.securechatapp.data.repository

import com.example.securechatapp.core.network.ApiErrorEnvelopeDto
import com.example.securechatapp.core.result.AppResult
import com.example.securechatapp.data.remote.api.KeysApi
import com.example.securechatapp.data.remote.dto.keys.KeyBundleResponseDto
import com.example.securechatapp.data.remote.dto.keys.PreKeyDto
import com.example.securechatapp.data.remote.dto.keys.RefillPreKeysRequestDto
import com.example.securechatapp.data.remote.dto.keys.RotateSignedPreKeyRequestDto
import com.example.securechatapp.domain.model.SignalKeyBundle
import com.example.securechatapp.domain.model.SignalPreKeyRefillResult
import com.example.securechatapp.domain.model.SignalPublicPreKey
import com.example.securechatapp.domain.model.SignalSignedPreKey
import com.example.securechatapp.domain.model.SignalSignedPreKeyRotationResult
import com.example.securechatapp.domain.repository.SignalPreKeyRepository
import javax.inject.Inject
import kotlinx.serialization.json.Json
import retrofit2.HttpException

class SignalPreKeyRepositoryImpl @Inject constructor(
    private val keysApi: KeysApi,
    private val json: Json,
) : SignalPreKeyRepository {

    override suspend fun getBundleForUser(userId: Int): AppResult<SignalKeyBundle> {
        if (userId <= 0) {
            return AppResult.Error(
                code = "INVALID_USER_ID",
                message = "User id must be positive.",
            )
        }

        return callApi {
            keysApi.getKeyBundle(userId).data.toDomain()
        }
    }

    override suspend fun refillOneTimePreKeys(
        preKeys: List<SignalPublicPreKey>,
    ): AppResult<SignalPreKeyRefillResult> {
        val normalizedPreKeys = preKeys
            .distinctBy { it.preKeyId }
            .sortedBy { it.preKeyId }

        if (normalizedPreKeys.isEmpty()) {
            return AppResult.Error(
                code = "EMPTY_PREKEY_BATCH",
                message = "At least one one-time pre-key is required.",
            )
        }

        if (normalizedPreKeys.size > MAX_PREKEY_REFILL_BATCH_SIZE) {
            return AppResult.Error(
                code = "PREKEY_BATCH_TOO_LARGE",
                message = "A pre-key refill batch cannot contain more than $MAX_PREKEY_REFILL_BATCH_SIZE keys.",
            )
        }

        return callApi {
            val response = keysApi.refillPreKeys(
                RefillPreKeysRequestDto(
                    prekeys = normalizedPreKeys.map {
                        PreKeyDto(
                            preKeyId = it.preKeyId,
                            publicPreKey = it.publicPreKey,
                        )
                    },
                )
            ).data

            SignalPreKeyRefillResult(
                deviceId = response.deviceId,
                added = response.added,
                preKeysCount = response.preKeysCount,
            )
        }
    }

    override suspend fun rotateSignedPreKey(
        signedPreKey: SignalSignedPreKey,
    ): AppResult<SignalSignedPreKeyRotationResult> {
        return callApi {
            val response = keysApi.rotateSignedPreKey(
                RotateSignedPreKeyRequestDto(
                    signedPreKeyId = signedPreKey.signedPreKeyId,
                    signedPreKey = signedPreKey.signedPreKey,
                    signedPreKeySignature = signedPreKey.signature,
                )
            ).data

            SignalSignedPreKeyRotationResult(
                deviceId = response.deviceId,
                rotated = response.rotated,
            )
        }
    }

    private suspend fun <T> callApi(block: suspend () -> T): AppResult<T> {
        return try {
            AppResult.Success(block())
        } catch (e: HttpException) {
            parseHttpError(e)
        } catch (e: IllegalArgumentException) {
            AppResult.Error(
                code = "INVALID_SIGNAL_PREKEY_PAYLOAD",
                message = e.message ?: "Invalid Signal pre-key payload.",
            )
        } catch (e: Exception) {
            AppResult.Error(
                code = "NETWORK_ERROR",
                message = e.message ?: "Unknown network error.",
            )
        }
    }

    private fun KeyBundleResponseDto.toDomain(): SignalKeyBundle {
        return SignalKeyBundle(
            userId = userId,
            deviceId = deviceId,
            requestedByDeviceId = requestedByDeviceId,
            registrationId = registrationId,
            publicIdentityKey = publicIdentityKey,
            publicSigningKey = publicSigningKey,
            signedPreKeyId = signedPreKeyId,
            signedPreKey = signedPreKey,
            signedPreKeySignature = signedPreKeySignature,
            oneTimePreKey = oneTimePreKey?.let {
                SignalPublicPreKey(
                    preKeyId = it.preKeyId,
                    publicPreKey = it.publicPreKey,
                )
            },
            preKeysRemaining = preKeysRemaining,
        )
    }

    private fun parseHttpError(e: HttpException): AppResult.Error {
        val raw = e.response()?.errorBody()?.string()
        val parsed = raw?.let {
            runCatching {
                json.decodeFromString(ApiErrorEnvelopeDto.serializer(), it)
            }.getOrNull()
        }

        return AppResult.Error(
            code = parsed?.error?.code ?: "HTTP_${e.code()}",
            message = parsed?.error?.message ?: (e.message() ?: "HTTP ${e.code()}"),
            statusCode = e.code(),
        )
    }

    private companion object {
        const val MAX_PREKEY_REFILL_BATCH_SIZE = 200
    }
}
