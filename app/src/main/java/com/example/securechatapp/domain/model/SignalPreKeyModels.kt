package com.example.securechatapp.domain.model

data class SignalPublicPreKey(
    val preKeyId: Int,
    val publicPreKey: String,
) {
    init {
        require(preKeyId > 0) { "preKeyId must be positive" }
        require(publicPreKey.isNotBlank()) { "publicPreKey must not be blank" }
    }
}

data class SignalSignedPreKey(
    val signedPreKey: String,
    val signature: String,
) {
    init {
        require(signedPreKey.isNotBlank()) { "signedPreKey must not be blank" }
        require(signature.isNotBlank()) { "signature must not be blank" }
    }
}

data class SignalKeyBundle(
    val userId: Int,
    val deviceId: Int,
    val requestedByDeviceId: Int,
    val publicIdentityKey: String,
    val publicSigningKey: String,
    val signedPreKey: String,
    val signedPreKeySignature: String,
    val oneTimePreKey: SignalPublicPreKey?,
    val preKeysRemaining: Int,
)

data class SignalPreKeyRefillResult(
    val deviceId: Int,
    val added: Int,
    val preKeysCount: Int,
)

data class SignalSignedPreKeyRotationResult(
    val deviceId: Int,
    val rotated: Boolean,
)
