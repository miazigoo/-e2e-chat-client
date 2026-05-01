package com.example.securechatapp.data.remote.api

import com.example.securechatapp.core.network.ApiEnvelopeDto
import com.example.securechatapp.data.remote.dto.devices.ListDeviceAuthorizationRequestsResponseDto
import com.example.securechatapp.data.remote.dto.devices.ListDevicesResponseDto
import com.example.securechatapp.data.remote.dto.devices.ResolveDeviceAuthorizationRequestResponseDto
import com.example.securechatapp.data.remote.dto.devices.RevokeDeviceResponseDto
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface DevicesApi {

    @GET("devices")
    suspend fun listDevices(): ApiEnvelopeDto<ListDevicesResponseDto>

    @GET("devices/authorization-requests")
    suspend fun listAuthorizationRequests(): ApiEnvelopeDto<ListDeviceAuthorizationRequestsResponseDto>

    @POST("devices/authorization-requests/{requestId}/approve")
    suspend fun approveAuthorizationRequest(
        @Path("requestId") requestId: String,
    ): ApiEnvelopeDto<ResolveDeviceAuthorizationRequestResponseDto>

    @POST("devices/authorization-requests/{requestId}/deny")
    suspend fun denyAuthorizationRequest(
        @Path("requestId") requestId: String,
    ): ApiEnvelopeDto<ResolveDeviceAuthorizationRequestResponseDto>

    @DELETE("devices/{deviceId}")
    suspend fun revokeDevice(
        @Path("deviceId") deviceId: Int,
    ): ApiEnvelopeDto<RevokeDeviceResponseDto>
}
