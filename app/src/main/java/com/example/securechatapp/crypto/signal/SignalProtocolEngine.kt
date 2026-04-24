package com.example.securechatapp.crypto.signal

interface SignalProtocolEngine {
    val isEnabled: Boolean

    suspend fun ensureLocalIdentity()

    suspend fun rotatePreKeysIfNeeded()

    suspend fun createPreKeyBundleDraft(): SignalPreKeyBundleDraft

    suspend fun processRemotePreKeyBundle(
        address: SignalAddress,
        bundle: SignalPreKeyBundleDraft,
    )

    suspend fun encrypt(message: SignalPlaintextMessage): SignalCiphertextMessage

    suspend fun decrypt(message: SignalCiphertextMessage): SignalPlaintextMessage
}
