package com.example.securechatapp.domain.usecase

import com.example.securechatapp.core.result.AppResult
import com.example.securechatapp.crypto.signal.SignalPreKeyMaterialProvider
import com.example.securechatapp.crypto.signal.SignalProtocolUnavailableException
import com.example.securechatapp.domain.model.SignalPreKeyRefillResult
import com.example.securechatapp.domain.model.SignalSignedPreKeyRotationResult
import com.example.securechatapp.domain.repository.SignalPreKeyRepository
import javax.inject.Inject

class SignalPreKeyMaintenanceUseCase @Inject constructor(
    private val repository: SignalPreKeyRepository,
    private val materialProvider: SignalPreKeyMaterialProvider,
) {

    suspend fun refillOneTimePreKeys(
        count: Int = DEFAULT_REFILL_COUNT,
    ): AppResult<SignalPreKeyRefillResult> {
        if (count !in 1..MAX_REFILL_COUNT) {
            return AppResult.Error(
                code = "INVALID_PREKEY_REFILL_COUNT",
                message = "Pre-key refill count must be in range 1..$MAX_REFILL_COUNT.",
            )
        }

        return try {
            repository.refillOneTimePreKeys(
                preKeys = materialProvider.generateOneTimePreKeys(count),
            )
        } catch (e: SignalProtocolUnavailableException) {
            AppResult.Error(
                code = "SIGNAL_PROTOCOL_UNAVAILABLE",
                message = e.message ?: "Signal protocol is unavailable.",
            )
        }
    }

    suspend fun rotateSignedPreKey(): AppResult<SignalSignedPreKeyRotationResult> {
        return try {
            repository.rotateSignedPreKey(
                signedPreKey = materialProvider.generateSignedPreKey(),
            )
        } catch (e: SignalProtocolUnavailableException) {
            AppResult.Error(
                code = "SIGNAL_PROTOCOL_UNAVAILABLE",
                message = e.message ?: "Signal protocol is unavailable.",
            )
        }
    }

    private companion object {
        const val DEFAULT_REFILL_COUNT = 50
        const val MAX_REFILL_COUNT = 200
    }
}
