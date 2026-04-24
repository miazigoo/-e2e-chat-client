package com.example.securechatapp.crypto.signal

data class SignalRemoteAddress(
    val userId: Int,
    val deviceId: Int,
) {
    init {
        require(userId > 0) { "userId must be positive" }
        require(deviceId > 0) { "deviceId must be positive" }
    }

    val addressName: String
        get() = "user:$userId"
}

enum class SignalCiphertextType(val wireValue: Int) {
    SIGNAL(2),
    PREKEY(3);

    companion object {
        fun fromWireValue(value: Int): SignalCiphertextType =
            entries.firstOrNull { it.wireValue == value }
                ?: throw IllegalArgumentException("Unsupported Signal ciphertext type: $value")
    }
}

data class SignalEncryptedMessage(
    val ciphertext: String,
    val type: SignalCiphertextType,
)
