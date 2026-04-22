package com.example.securechatapp.domain.repository

import com.example.securechatapp.core.result.AppResult
import com.example.securechatapp.domain.model.LoginResult
import com.example.securechatapp.domain.model.RegisterResult
import com.example.securechatapp.domain.model.VerifyEmailCodeResult

interface AuthRepository {
    suspend fun register(
        nickname: String,
        password: String,
        email: String?,
        email2faEnabled: Boolean,
    ): AppResult<RegisterResult>

    suspend fun login(
        nickname: String,
        password: String,
        deviceUuid: String?,
    ): AppResult<LoginResult>

    suspend fun verifyEmailCode(
        loginChallengeId: String,
        code: String,
        deviceUuid: String?,
    ): AppResult<VerifyEmailCodeResult>
}
