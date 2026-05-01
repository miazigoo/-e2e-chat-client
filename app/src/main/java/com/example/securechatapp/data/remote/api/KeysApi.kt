package com.example.securechatapp.data.remote.api

import com.example.securechatapp.core.network.ApiEnvelopeDto
import com.example.securechatapp.data.remote.dto.keys.KeyBundlesResponseDto
import com.example.securechatapp.data.remote.dto.keys.KeyBundleResponseDto
import com.example.securechatapp.data.remote.dto.keys.RefillPreKeysRequestDto
import com.example.securechatapp.data.remote.dto.keys.RefillPreKeysResponseDto
import com.example.securechatapp.data.remote.dto.keys.RotateSignedPreKeyRequestDto
import com.example.securechatapp.data.remote.dto.keys.RotateSignedPreKeyResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface KeysApi {

    @GET("keys/bundle/{userId}")
    suspend fun getKeyBundle(
        @Path("userId") userId: Int,
    ): ApiEnvelopeDto<KeyBundleResponseDto>

    @GET("keys/bundles/{userId}")
    suspend fun getKeyBundles(
        @Path("userId") userId: Int,
    ): ApiEnvelopeDto<KeyBundlesResponseDto>

    @POST("keys/prekeys/refill")
    suspend fun refillPreKeys(
        @Body body: RefillPreKeysRequestDto,
    ): ApiEnvelopeDto<RefillPreKeysResponseDto>

    @POST("keys/signed-prekey/rotate")
    suspend fun rotateSignedPreKey(
        @Body body: RotateSignedPreKeyRequestDto,
    ): ApiEnvelopeDto<RotateSignedPreKeyResponseDto>
}
