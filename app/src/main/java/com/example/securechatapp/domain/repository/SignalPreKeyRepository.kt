package com.example.securechatapp.domain.repository

import com.example.securechatapp.core.result.AppResult
import com.example.securechatapp.domain.model.SignalKeyBundle
import com.example.securechatapp.domain.model.SignalPreKeyRefillResult
import com.example.securechatapp.domain.model.SignalPublicPreKey
import com.example.securechatapp.domain.model.SignalSignedPreKey
import com.example.securechatapp.domain.model.SignalSignedPreKeyRotationResult

interface SignalPreKeyRepository {
    suspend fun getBundleForUser(userId: Int): AppResult<SignalKeyBundle>

    suspend fun refillOneTimePreKeys(
        preKeys: List<SignalPublicPreKey>,
    ): AppResult<SignalPreKeyRefillResult>

    suspend fun rotateSignedPreKey(
        signedPreKey: SignalSignedPreKey,
    ): AppResult<SignalSignedPreKeyRotationResult>
}
