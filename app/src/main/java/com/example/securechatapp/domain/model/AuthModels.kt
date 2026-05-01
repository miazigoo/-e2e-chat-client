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
    val requiresTotp: Boolean,
    val requiresBootstrap: Boolean,
    val requiresDeviceApproval: Boolean,
    val deviceApprovalRequestId: String?,
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
    val requiresDeviceApproval: Boolean,
    val deviceApprovalRequestId: String?,
    val bootstrapToken: String?,
    val bootstrapExpiresIn: Int?,
    val accessToken: String?,
    val refreshToken: String?,
    val expiresIn: Int?,
)

data class Google2faSetupResult(
    val secret: String,
    val provisioningUri: String,
    val issuer: String,
    val accountName: String,
)

data class Google2faStatusResult(
    val enabled: Boolean,
    val confirmedAt: String?,
)
