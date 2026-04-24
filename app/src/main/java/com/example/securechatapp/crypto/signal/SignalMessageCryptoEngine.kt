package com.example.securechatapp.crypto.signal

import com.example.securechatapp.domain.model.SignalKeyBundle

interface SignalMessageCryptoEngine {
    fun hasSession(remoteAddress: SignalRemoteAddress): Boolean

    fun processRemoteBundle(bundle: SignalKeyBundle)

    fun encrypt(
        remoteAddress: SignalRemoteAddress,
        plainText: String,
    ): SignalCipherPayload

    fun decrypt(
        remoteAddress: SignalRemoteAddress,
        payload: SignalCipherPayload,
    ): String
}
