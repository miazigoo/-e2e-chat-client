package com.example.securechatapp.data.remote.api

import com.example.securechatapp.data.remote.dto.ApiEnvelopeDto
import com.example.securechatapp.data.remote.dto.BootstrapDeviceRequestDto
import com.example.securechatapp.data.remote.dto.BootstrapDeviceResponseDto
import com.example.securechatapp.data.remote.dto.CompleteUploadSessionRequestDto
import com.example.securechatapp.data.remote.dto.CompleteUploadSessionResponseDto
import com.example.securechatapp.data.remote.dto.ConversationEventsResponseDto
import com.example.securechatapp.data.remote.dto.CreateConversationRequestDto
import com.example.securechatapp.data.remote.dto.CreateConversationResponseDto
import com.example.securechatapp.data.remote.dto.CreateUploadSessionRequestDto
import com.example.securechatapp.data.remote.dto.CreateUploadSessionResponseDto
import com.example.securechatapp.data.remote.dto.DeleteMessagesRequestDto
import com.example.securechatapp.data.remote.dto.DeleteMessagesResponseDto
import com.example.securechatapp.data.remote.dto.DeleteMessageReactionResponseDto
import com.example.securechatapp.data.remote.dto.DeviceHeartbeatResponseDto
import com.example.securechatapp.data.remote.dto.GetConversationResponseDto
import com.example.securechatapp.data.remote.dto.InitAttachmentsRequestDto
import com.example.securechatapp.data.remote.dto.InitAttachmentsResponseDto
import com.example.securechatapp.data.remote.dto.ListConversationsResponseDto
import com.example.securechatapp.data.remote.dto.ListMessagesResponseDto
import com.example.securechatapp.data.remote.dto.LoginRequestDto
import com.example.securechatapp.data.remote.dto.LoginResponseDto
import com.example.securechatapp.data.remote.dto.MarkDeliveredRequestDto
import com.example.securechatapp.data.remote.dto.MarkDeliveredResponseDto
import com.example.securechatapp.data.remote.dto.MarkReadRequestDto
import com.example.securechatapp.data.remote.dto.MarkReadResponseDto
import com.example.securechatapp.data.remote.dto.PinMessageResponseDto
import com.example.securechatapp.data.remote.dto.RefreshRequestDto
import com.example.securechatapp.data.remote.dto.RefreshResponseDto
import com.example.securechatapp.data.remote.dto.RegisterRequestDto
import com.example.securechatapp.data.remote.dto.RegisterResponseDto
import com.example.securechatapp.data.remote.dto.RevokeCurrentDeviceResponseDto
import com.example.securechatapp.data.remote.dto.SearchMessagesResponseDto
import com.example.securechatapp.data.remote.dto.SendMessageRequestDto
import com.example.securechatapp.data.remote.dto.SendMessageResponseDto
import com.example.securechatapp.data.remote.dto.SetMessageReactionRequestDto
import com.example.securechatapp.data.remote.dto.SetMessageReactionResponseDto
import com.example.securechatapp.data.remote.dto.SharedMessagesResponseDto
import com.example.securechatapp.data.remote.dto.UpdateFcmTokenRequestDto
import com.example.securechatapp.data.remote.dto.ConversationSettingsResponseDto
import com.example.securechatapp.data.remote.dto.UpdateConversationSettingsRequestDto
import com.example.securechatapp.data.remote.dto.UpdateFcmTokenResponseDto
import com.example.securechatapp.data.remote.dto.UserSafetyResponseDto
import com.example.securechatapp.data.remote.dto.UserSearchResponseDto
import com.example.securechatapp.data.remote.dto.VerifyEmailCodeRequestDto
import com.example.securechatapp.data.remote.dto.VerifyEmailCodeResponseDto
import com.example.securechatapp.data.remote.dto.GetAttachmentResponseDto
import com.example.securechatapp.data.remote.dto.ListMessageAttachmentsResponseDto
import com.example.securechatapp.data.remote.dto.UpdateUserProfileRequestDto
import com.example.securechatapp.data.remote.dto.UserProfileResponseDto
import com.example.securechatapp.data.remote.dto.UserPublicProfileResponseDto
import com.example.securechatapp.data.remote.dto.AppVersionCheckResponseDto
import com.example.securechatapp.data.remote.dto.LatestAppReleaseResponseDto
import com.example.securechatapp.data.remote.dto.LogoutAllResponseDto
import com.example.securechatapp.data.remote.dto.LogoutResponseDto
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Part
import retrofit2.http.Query

interface ChatBackendApi {
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

    @GET("users/search")
    suspend fun searchUsers(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20,
    ): ApiEnvelopeDto<UserSearchResponseDto>

    @GET("users/me")
    suspend fun getMyProfile(): ApiEnvelopeDto<UserProfileResponseDto>

    @PATCH("users/me")
    suspend fun updateMyProfile(
        @Body body: UpdateUserProfileRequestDto,
    ): ApiEnvelopeDto<UserProfileResponseDto>

    @Multipart
    @POST("users/me/avatar")
    suspend fun uploadMyAvatar(
        @Part file: MultipartBody.Part,
    ): ApiEnvelopeDto<UserProfileResponseDto>

    @DELETE("users/me/avatar")
    suspend fun deleteMyAvatar(): ApiEnvelopeDto<UserProfileResponseDto>

    @GET("users/{userId}/safety")
    suspend fun getUserSafety(
        @Path("userId") userId: Int,
    ): ApiEnvelopeDto<UserSafetyResponseDto>

    @GET("users/{userId}/profile")
    suspend fun getUserProfile(
        @Path("userId") userId: Int,
    ): ApiEnvelopeDto<UserPublicProfileResponseDto>

    @GET("conversations")
    suspend fun listConversations(): ApiEnvelopeDto<ListConversationsResponseDto>

    @POST("conversations")
    suspend fun createConversation(
        @Body body: CreateConversationRequestDto,
    ): ApiEnvelopeDto<CreateConversationResponseDto>

    @GET("conversations/{conversationId}")
    suspend fun getConversation(
        @Path("conversationId") conversationId: Int,
    ): ApiEnvelopeDto<GetConversationResponseDto>

    @PATCH("conversations/{conversationId}/settings")
    suspend fun updateConversationSettings(
        @Path("conversationId") conversationId: Int,
        @Body body: UpdateConversationSettingsRequestDto,
    ): ApiEnvelopeDto<ConversationSettingsResponseDto>

    @GET("messages/conversations/{conversationId}")
    suspend fun listMessages(
        @Path("conversationId") conversationId: Int,
        @Query("before_id") beforeId: Int? = null,
        @Query("limit") limit: Int = 100,
    ): ApiEnvelopeDto<ListMessagesResponseDto>

    @GET("messages/conversations/{conversationId}/search")
    suspend fun searchMessages(
        @Path("conversationId") conversationId: Int,
        @Query("q") query: String,
        @Query("limit") limit: Int = 50,
    ): ApiEnvelopeDto<SearchMessagesResponseDto>

    @GET("messages/conversations/{conversationId}/shared")
    suspend fun listSharedMessages(
        @Path("conversationId") conversationId: Int,
        @Query("tab") tab: String,
        @Query("before_message_id") beforeMessageId: Int? = null,
        @Query("limit") limit: Int = 50,
    ): ApiEnvelopeDto<SharedMessagesResponseDto>

    @POST("messages/send")
    suspend fun sendMessage(
        @Body body: SendMessageRequestDto,
    ): ApiEnvelopeDto<SendMessageResponseDto>

    @POST("messages/{messageId}/delivered")
    suspend fun markDelivered(
        @Path("messageId") messageId: Int,
        @Body body: MarkDeliveredRequestDto,
    ): ApiEnvelopeDto<MarkDeliveredResponseDto>

    @POST("messages/{messageId}/read")
    suspend fun markRead(
        @Path("messageId") messageId: Int,
        @Body body: MarkReadRequestDto,
    ): ApiEnvelopeDto<MarkReadResponseDto>

    @POST("messages/delete-local")
    suspend fun deleteLocalMessages(
        @Body body: DeleteMessagesRequestDto,
    ): ApiEnvelopeDto<DeleteMessagesResponseDto>

    @POST("messages/delete-global")
    suspend fun deleteGlobalMessages(
        @Body body: DeleteMessagesRequestDto,
    ): ApiEnvelopeDto<DeleteMessagesResponseDto>

    @POST("messages/{messageId}/reaction")
    suspend fun setMessageReaction(
        @Path("messageId") messageId: Int,
        @Body body: SetMessageReactionRequestDto,
    ): ApiEnvelopeDto<SetMessageReactionResponseDto>

    @DELETE("messages/{messageId}/reaction")
    suspend fun deleteMessageReaction(
        @Path("messageId") messageId: Int,
    ): ApiEnvelopeDto<DeleteMessageReactionResponseDto>

    @POST("messages/conversations/{conversationId}/pin/{messageId}")
    suspend fun pinMessage(
        @Path("conversationId") conversationId: Int,
        @Path("messageId") messageId: Int,
    ): ApiEnvelopeDto<PinMessageResponseDto>

    @DELETE("messages/conversations/{conversationId}/pin")
    suspend fun unpinMessage(
        @Path("conversationId") conversationId: Int,
    ): ApiEnvelopeDto<PinMessageResponseDto>

    @POST("files/upload-sessions")
    suspend fun createUploadSession(
        @Body body: CreateUploadSessionRequestDto,
    ): ApiEnvelopeDto<CreateUploadSessionResponseDto>

    @POST("files/upload-sessions/{sessionId}/attachments/init")
    suspend fun initAttachments(
        @Path("sessionId") sessionId: Int,
        @Body body: InitAttachmentsRequestDto,
    ): ApiEnvelopeDto<InitAttachmentsResponseDto>

    @POST("files/upload-sessions/{sessionId}/complete")
    suspend fun completeUploadSession(
        @Path("sessionId") sessionId: Int,
        @Body body: CompleteUploadSessionRequestDto,
    ): ApiEnvelopeDto<CompleteUploadSessionResponseDto>

    @POST("devices/heartbeat")
    suspend fun sendHeartbeat(): ApiEnvelopeDto<DeviceHeartbeatResponseDto>

    @POST("devices/fcm-token")
    suspend fun updateFcmToken(
        @Body body: UpdateFcmTokenRequestDto,
    ): ApiEnvelopeDto<UpdateFcmTokenResponseDto>

    @DELETE("devices/current")
    suspend fun revokeCurrentDevice(): ApiEnvelopeDto<RevokeCurrentDeviceResponseDto>

    @GET("files/messages/{messageId}/attachments")
    suspend fun listMessageAttachments(
        @Path("messageId") messageId: Int,
    ): ApiEnvelopeDto<ListMessageAttachmentsResponseDto>

    @GET("files/attachments/{attachmentId}")
    suspend fun getAttachmentMetadata(
        @Path("attachmentId") attachmentId: Int,
    ): ApiEnvelopeDto<GetAttachmentResponseDto>

    @GET("files/apk/latest")
    suspend fun getLatestApkRelease(): ApiEnvelopeDto<LatestAppReleaseResponseDto>

    @GET("files/apk/check")
    suspend fun checkApkVersion(
        @Query("version_code") versionCode: Int,
    ): ApiEnvelopeDto<AppVersionCheckResponseDto>

    @POST("auth/logout")
    suspend fun logoutSession(): ApiEnvelopeDto<LogoutResponseDto>

    @POST("auth/logout-all")
    suspend fun logoutAllSessions(): ApiEnvelopeDto<LogoutAllResponseDto>

    @GET("sync/conversations/{conversationId}/events")
    suspend fun getConversationEvents(
        @Path("conversationId") conversationId: Int,
        @Query("after_event_id") afterEventId: Int? = null,
        @Query("limit") limit: Int = 200,
    ): ApiEnvelopeDto<ConversationEventsResponseDto>

}
