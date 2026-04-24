package com.example.securechatapp.crypto.signal

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibSignalMessageCryptoEngine @Inject constructor() {

    suspend fun encrypt(
        recipientUserId: Int,
        recipientDeviceId: Int,
        plainText: String,
    ): SignalCipherPayload {
        throw SignalProtocolUnavailableException(
            "Signal encryption is not enabled yet. Durable libsignal stores and server message_type support are required."
        )
    }

    suspend fun decrypt(payload: SignalCipherPayload): String {
        throw SignalProtocolUnavailableException(
            "Signal decryption is not enabled yet. Durable libsignal stores and server message_type support are required."
        )
    }
}
