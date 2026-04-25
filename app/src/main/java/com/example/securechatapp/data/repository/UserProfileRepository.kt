package com.example.securechatapp.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.securechatapp.data.remote.api.ChatBackendApi
import com.example.securechatapp.data.remote.dto.UpdateUserProfileRequestDto
import com.example.securechatapp.domain.model.UserProfile
import com.example.securechatapp.domain.model.UserProfileSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

data class UpdateUserProfileInput(
    val nickname: String,
    val fullName: String,
    val bio: String,
    val languageCode: String,
    val theme: String,
    val pushNotificationsEnabled: Boolean,
    val apkUpdateNotificationsEnabled: Boolean,
)

@Singleton
class UserProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ChatBackendApi,
    json: Json,
) : BaseApiRepository(json) {

    suspend fun getMyProfile(): UserProfile {
        return safe { api.getMyProfile().data }.toDomain()
    }

    suspend fun updateMyProfile(input: UpdateUserProfileInput): UserProfile {
        return safe {
            api.updateMyProfile(
                UpdateUserProfileRequestDto(
                    nickname = input.nickname.trim(),
                    fullName = input.fullName.trim(),
                    bio = input.bio.trim(),
                    languageCode = input.languageCode.trim().lowercase(),
                    theme = input.theme.trim().lowercase(),
                    pushNotificationsEnabled = input.pushNotificationsEnabled,
                    apkUpdateNotificationsEnabled = input.apkUpdateNotificationsEnabled,
                )
            ).data
        }.toDomain()
    }

    suspend fun uploadMyAvatar(uri: Uri): UserProfile {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Не удалось прочитать файл аватарки")

        val fileName = queryDisplayName(uri) ?: "avatar.jpg"
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

        val multipart = MultipartBody.Part.createFormData(
            name = "file",
            filename = fileName,
            body = bytes.toRequestBody(mimeType.toMediaTypeOrNull()),
        )

        return safe { api.uploadMyAvatar(multipart).data }.toDomain()
    }

    suspend fun deleteMyAvatar(): UserProfile {
        return safe { api.deleteMyAvatar().data }.toDomain()
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }
}

private fun com.example.securechatapp.data.remote.dto.UserProfileResponseDto.toDomain(): UserProfile {
    return UserProfile(
        userId = userId,
        publicId = publicId,
        nickname = nickname,
        fullName = fullName,
        bio = bio,
        avatarUrl = avatarUrl,
        avatarUpdatedAt = avatarUpdatedAt,
        createdAt = createdAt,
        settings = UserProfileSettings(
            languageCode = settings.languageCode,
            theme = settings.theme,
            pushNotificationsEnabled = settings.pushNotificationsEnabled,
            apkUpdateNotificationsEnabled = settings.apkUpdateNotificationsEnabled,
        ),
    )
}
