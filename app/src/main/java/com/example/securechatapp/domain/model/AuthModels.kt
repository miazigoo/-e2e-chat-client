package com.example.securechatapp.domain.model

data class RegisterResult(
    val userId: Int,
    val nickname: String,
    val requiresDeviceRegistration: Boolean,
    val bootstrapToken: String?,
    val bootstrapExpiresIn: Int?,
)

data class LoginResult(
    val requiresEmailCode: Boolean,
    val requiresBootstrap: Boolean,
    val loginChallengeId: String?,
    val emailMasked: String?,
    val debugCode: String?,
    val bootstrapToken: String?,
    val bootstrapExpiresIn: Int?,
    val accessToken: String?,
    val refreshToken: String?,
    val expiresIn: Int?,
)

data class VerifyEmailCodeResult(
    val requiresBootstrap: Boolean,
    val bootstrapToken: String?,
    val bootstrapExpiresIn: Int?,
    val accessToken: String?,
    val refreshToken: String?,
    val expiresIn: Int?,
)
