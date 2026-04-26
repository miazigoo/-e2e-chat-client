package com.example.securechatapp.data.remote.dto.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequestDto(
    val nickname: String,
    val password: String,
    val email: String? = null,
    @SerialName("email_2fa_enabled")
    val email2faEnabled: Boolean = false,
)

@Serializable
data class RegisterResponseDto(
    @SerialName("user_id")
    val userId: Int = 0,
    val nickname: String = "",
    @SerialName("requires_device_registration")
    val requiresDeviceRegistration: Boolean = false,
    @SerialName("bootstrap_token")
    val bootstrapToken: String? = null,
    @SerialName("bootstrap_expires_in")
    val bootstrapExpiresIn: Int? = null,
)

@Serializable
data class LoginRequestDto(
    val nickname: String,
    val password: String,
    @SerialName("device_uuid")
    val deviceUuid: String? = null,
    @SerialName("totp_code")
    val totpCode: String? = null,
)

@Serializable
data class LoginResponseDto(
    @SerialName("requires_email_code")
    val requiresEmailCode: Boolean,
    @SerialName("requires_totp")
    val requiresTotp: Boolean = false,
    @SerialName("requires_bootstrap")
    val requiresBootstrap: Boolean = false,
    @SerialName("login_challenge_id")
    val loginChallengeId: String? = null,
    @SerialName("email_masked")
    val emailMasked: String? = null,
    @SerialName("debug_code")
    val debugCode: String? = null,
    @SerialName("bootstrap_token")
    val bootstrapToken: String? = null,
    @SerialName("bootstrap_expires_in")
    val bootstrapExpiresIn: Int? = null,
    @SerialName("access_token")
    val accessToken: String? = null,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    @SerialName("expires_in")
    val expiresIn: Int? = null,
)

@Serializable
data class VerifyEmailCodeRequestDto(
    @SerialName("login_challenge_id")
    val loginChallengeId: String,
    val code: String,
    @SerialName("device_uuid")
    val deviceUuid: String? = null,
)

@Serializable
data class VerifyEmailCodeResponseDto(
    @SerialName("requires_bootstrap")
    val requiresBootstrap: Boolean = false,
    @SerialName("bootstrap_token")
    val bootstrapToken: String? = null,
    @SerialName("bootstrap_expires_in")
    val bootstrapExpiresIn: Int? = null,
    @SerialName("access_token")
    val accessToken: String? = null,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    @SerialName("expires_in")
    val expiresIn: Int? = null,
)

@Serializable
data class RefreshRequestDto(
    @SerialName("refresh_token")
    val refreshToken: String,
)

@Serializable
data class RefreshResponseDto(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String,
    @SerialName("expires_in")
    val expiresIn: Int,
)

@Serializable
data class Google2FASetupResponseDto(
    val secret: String,
    @SerialName("provisioning_uri")
    val provisioningUri: String,
    val issuer: String,
    @SerialName("account_name")
    val accountName: String,
)

@Serializable
data class Google2FAConfirmRequestDto(
    val code: String,
)

@Serializable
data class Google2FAStatusResponseDto(
    val enabled: Boolean,
    @SerialName("confirmed_at")
    val confirmedAt: String? = null,
)

@Serializable
data class OneTimePreKeyDto(
    @SerialName("prekey_id")
    val prekeyId: Int,
    @SerialName("public_prekey")
    val publicPrekey: String,
)

@Serializable
data class BootstrapDeviceRequestDto(
    @SerialName("device_uuid")
    val deviceUuid: String,
    @SerialName("device_name")
    val deviceName: String,
    val platform: String,
    @SerialName("app_version")
    val appVersion: String,
    @SerialName("fcm_token")
    val fcmToken: String? = null,
    @SerialName("registration_id")
    val registrationId: Int,
    @SerialName("public_identity_key")
    val publicIdentityKey: String,
    @SerialName("public_signing_key")
    val publicSigningKey: String,
    @SerialName("signed_prekey_id")
    val signedPrekeyId: Int,
    @SerialName("signed_prekey")
    val signedPrekey: String,
    @SerialName("signed_prekey_signature")
    val signedPrekeySignature: String,
    @SerialName("one_time_prekeys")
    val oneTimePrekeys: List<OneTimePreKeyDto>,
)

@Serializable
data class BootstrapDeviceResponseDto(
    @SerialName("device_id")
    val deviceId: Int,
    @SerialName("device_uuid")
    val deviceUuid: String,
    @SerialName("is_active")
    val isActive: Boolean,
    @SerialName("prekeys_count")
    val prekeysCount: Int,
    @SerialName("last_seen_at")
    val lastSeenAt: String? = null,
)
