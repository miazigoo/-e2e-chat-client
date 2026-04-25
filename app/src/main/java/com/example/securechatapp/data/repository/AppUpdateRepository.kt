package com.example.securechatapp.data.repository

import com.example.securechatapp.data.remote.api.ChatBackendApi
import com.example.securechatapp.domain.model.AppReleaseInfo
import com.example.securechatapp.domain.model.AppVersionCheck
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class AppUpdateRepository @Inject constructor(
    private val api: ChatBackendApi,
    json: Json,
) : BaseApiRepository(json) {

    suspend fun checkForUpdate(versionCode: Int): AppVersionCheck {
        return safe { api.checkApkVersion(versionCode).data }.toDomain()
    }

    suspend fun getLatestRelease(): AppReleaseInfo {
        return safe { api.getLatestApkRelease().data }.toDomain()
    }
}

private fun com.example.securechatapp.data.remote.dto.LatestAppReleaseResponseDto.toDomain(): AppReleaseInfo {
    return AppReleaseInfo(
        platform = platform,
        versionName = versionName,
        versionCode = versionCode,
        fileName = fileName,
        fileSize = fileSize,
        sha256 = sha256,
        changelog = changelog,
        contentType = contentType,
        uploadedAt = uploadedAt,
        downloadUrl = downloadUrl,
        downloadUrlExpiresIn = downloadUrlExpiresIn,
    )
}

private fun com.example.securechatapp.data.remote.dto.AppVersionCheckResponseDto.toDomain(): AppVersionCheck {
    return AppVersionCheck(
        currentVersionCode = currentVersionCode,
        latestVersionCode = latestVersionCode,
        updateAvailable = updateAvailable,
        release = release.toDomain(),
    )
}
