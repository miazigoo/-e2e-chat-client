package com.example.securechatapp.crypto.signal

data class SignalCipherPayload(
    val ciphertextBase64: String,
    val messageType: Int,
) {
    init {
        require(ciphertextBase64.isNotBlank()) { "ciphertextBase64 must not be blank" }
    }
}

data class SignalRemoteAddress(
    val userId: Int,
    val deviceId: Int,
) {
    init {
        require(userId > 0) { "userId must be positive" }
        require(deviceId > 0) { "deviceId must be positive" }
    }

    val stableName: String = "user:$userId"
}
