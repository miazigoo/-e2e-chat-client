package com.example.securechatapp.crypto.signal

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DisabledSignalProtocolEngine @Inject constructor(
    private val config: SignalProtocolConfig,
) : SignalProtocolEngine {

    override val isEnabled: Boolean
        get() = config.enabled

    override suspend fun ensureLocalIdentity() {
        throw unavailable()
    }

    override suspend fun rotatePreKeysIfNeeded() {
        throw unavailable()
    }

    override suspend fun createPreKeyBundleDraft(): SignalPreKeyBundleDraft {
        throw unavailable()
    }

    override suspend fun processRemotePreKeyBundle(
        address: SignalAddress,
        bundle: SignalPreKeyBundleDraft,
    ) {
        throw unavailable()
    }

    override suspend fun encrypt(message: SignalPlaintextMessage): SignalCiphertextMessage {
        throw unavailable()
    }

    override suspend fun decrypt(message: SignalCiphertextMessage): SignalPlaintextMessage {
        throw unavailable()
    }

    private fun unavailable(): SignalProtocolUnavailableException {
        return SignalProtocolUnavailableException()
    }
}
