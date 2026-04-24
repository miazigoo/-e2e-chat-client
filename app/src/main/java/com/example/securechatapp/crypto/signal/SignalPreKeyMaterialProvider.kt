package com.example.securechatapp.crypto.signal

import com.example.securechatapp.domain.model.SignalPublicPreKey
import com.example.securechatapp.domain.model.SignalSignedPreKey

interface SignalPreKeyMaterialProvider {
    suspend fun generateOneTimePreKeys(count: Int): List<SignalPublicPreKey>

    suspend fun generateSignedPreKey(): SignalSignedPreKey
}
