package com.example.securechatapp.data.remote.dto.keys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PreKeyDto(
    @SerialName("prekey_id")
    val preKeyId: Int,
    @SerialName("public_prekey")
    val publicPreKey: String,
)

@Serializable
data class RefillPreKeysRequestDto(
    val prekeys: List<PreKeyDto>,
)

@Serializable
data class RefillPreKeysResponseDto(
    @SerialName("device_id")
    val deviceId: Int,
    val added: Int,
    @SerialName("prekeys_count")
    val preKeysCount: Int,
)

@Serializable
data class RotateSignedPreKeyRequestDto(
    @SerialName("signed_prekey")
    val signedPreKey: String,
    @SerialName("signed_prekey_signature")
    val signedPreKeySignature: String,
)

@Serializable
data class RotateSignedPreKeyResponseDto(
    @SerialName("device_id")
    val deviceId: Int,
    val rotated: Boolean,
)

@Serializable
data class KeyBundleResponseDto(
    @SerialName("user_id")
    val userId: Int,
    @SerialName("device_id")
    val deviceId: Int,
    @SerialName("requested_by_device_id")
    val requestedByDeviceId: Int,
    @SerialName("public_identity_key")
    val publicIdentityKey: String,
    @SerialName("public_signing_key")
    val publicSigningKey: String,
    @SerialName("signed_prekey")
    val signedPreKey: String,
    @SerialName("signed_prekey_signature")
    val signedPreKeySignature: String,
    @SerialName("one_time_prekey")
    val oneTimePreKey: PreKeyDto? = null,
    @SerialName("prekeys_remaining")
    val preKeysRemaining: Int,
)
