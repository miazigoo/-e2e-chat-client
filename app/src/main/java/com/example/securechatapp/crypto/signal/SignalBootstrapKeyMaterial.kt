package com.example.securechatapp.crypto.signal

import com.example.securechatapp.data.remote.dto.auth.OneTimePreKeyDto

data class SignalBootstrapKeyMaterial(
    val registrationId: Int,
    val publicIdentityKey: String,
    val publicSigningKey: String,
    val signedPreKeyId: Int,
    val signedPreKey: String,
    val signedPreKeySignature: String,
    val oneTimePreKeys: List<OneTimePreKeyDto>,
)

interface SignalBootstrapKeyMaterialProvider {
    suspend fun getOrCreateBootstrapMaterial(
        oneTimePreKeyCount: Int = 100,
    ): SignalBootstrapKeyMaterial
}
