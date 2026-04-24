package com.example.securechatapp.crypto.signal

import com.example.securechatapp.domain.model.SignalPublicPreKey
import com.example.securechatapp.domain.model.SignalSignedPreKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealSignalPreKeyMaterialProvider @Inject constructor() : SignalPreKeyMaterialProvider {

    override suspend fun generateOneTimePreKeys(count: Int): List<SignalPublicPreKey> {
        require(count in 1..200) { "count must be in range 1..200" }
        throw SignalProtocolUnavailableException(
            "Real libsignal pre-key generation requires durable identity/pre-key stores."
        )
    }

    override suspend fun generateSignedPreKey(): SignalSignedPreKey {
        throw SignalProtocolUnavailableException(
            "Real libsignal signed pre-key generation requires durable identity/signed-pre-key stores."
        )
    }
}
