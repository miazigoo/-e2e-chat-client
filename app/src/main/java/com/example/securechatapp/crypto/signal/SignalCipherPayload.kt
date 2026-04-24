package com.example.securechatapp.crypto.signal

import kotlinx.serialization.Serializable

@Serializable
data class SignalCipherPayload(
    val ciphertextBase64: String,
    val messageType: SignalMessageType,
    val senderUserId: Int,
    val senderDeviceId: Int,
    val recipientUserId: Int,
    val recipientDeviceId: Int,
)

enum class SignalMessageType {
    PRE_KEY,
    WHISPER,
}
