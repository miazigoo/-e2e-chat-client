package com.example.securechatapp.data.remote.api

import com.example.securechatapp.core.network.ApiEnvelopeDto
import com.example.securechatapp.data.remote.dto.auth.BootstrapDeviceRequestDto
import com.example.securechatapp.data.remote.dto.auth.BootstrapDeviceResponseDto
import com.example.securechatapp.data.remote.dto.auth.LoginRequestDto
import com.example.securechatapp.data.remote.dto.auth.LoginResponseDto
import com.example.securechatapp.data.remote.dto.auth.RefreshRequestDto
import com.example.securechatapp.data.remote.dto.auth.RefreshResponseDto
import com.example.securechatapp.data.remote.dto.auth.RegisterRequestDto
import com.example.securechatapp.data.remote.dto.auth.RegisterResponseDto
import com.example.securechatapp.data.remote.dto.auth.VerifyEmailCodeRequestDto
import com.example.securechatapp.data.remote.dto.auth.VerifyEmailCodeResponseDto
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthApi {

    @POST("auth/register")
    suspend fun register(
        @Body body: RegisterRequestDto,
    ): ApiEnvelopeDto<RegisterResponseDto>

    @POST("auth/login")
    suspend fun login(
        @Body body: LoginRequestDto,
    ): ApiEnvelopeDto<LoginResponseDto>

    @POST("auth/login/verify-email-code")
    suspend fun verifyEmailCode(
        @Body body: VerifyEmailCodeRequestDto,
    ): ApiEnvelopeDto<VerifyEmailCodeResponseDto>

    @POST("auth/refresh")
    suspend fun refresh(
        @Body body: RefreshRequestDto,
    ): ApiEnvelopeDto<RefreshResponseDto>

    @POST("auth/bootstrap")
    suspend fun bootstrap(
        @Header("Authorization") authorization: String,
        @Body body: BootstrapDeviceRequestDto,
    ): ApiEnvelopeDto<BootstrapDeviceResponseDto>
}
