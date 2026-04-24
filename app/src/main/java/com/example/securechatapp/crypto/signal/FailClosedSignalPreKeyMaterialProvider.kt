package com.example.securechatapp.crypto.signal

import com.example.securechatapp.domain.model.SignalPublicPreKey
import com.example.securechatapp.domain.model.SignalSignedPreKey
import javax.inject.Inject

class FailClosedSignalPreKeyMaterialProvider @Inject constructor(
    private val config: SignalProtocolConfig,
) : SignalPreKeyMaterialProvider {

    override suspend fun generateOneTimePreKeys(count: Int): List<SignalPublicPreKey> {
        require(count in 1..200) { "count must be in range 1..200" }
        throwIfDisabled()
        throw SignalProtocolUnavailableException(
            "Signal one-time pre-key generation is not wired to libsignal stores yet.",
        )
    }

    override suspend fun generateSignedPreKey(): SignalSignedPreKey {
        throwIfDisabled()
        throw SignalProtocolUnavailableException(
            "Signal signed pre-key generation is not wired to libsignal stores yet.",
        )
    }

    private fun throwIfDisabled() {
        if (!config.isEnabled) {
            throw SignalProtocolUnavailableException(
                "Signal protocol is disabled by build config.",
            )
        }
    }
}
