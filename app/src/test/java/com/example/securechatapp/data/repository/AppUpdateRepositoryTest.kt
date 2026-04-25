package com.example.securechatapp.data.repository

import com.example.securechatapp.data.remote.api.ChatBackendApi
import com.example.securechatapp.data.remote.dto.ApiEnvelopeDto
import com.example.securechatapp.data.remote.dto.AppVersionCheckResponseDto
import com.example.securechatapp.data.remote.dto.LatestAppReleaseResponseDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRepositoryTest {

    private val api = mockk<ChatBackendApi>()
    private val json = Json { ignoreUnknownKeys = true }

    private val repository = AppUpdateRepository(
        api = api,
        json = json,
    )

    @Test
    fun `checkForUpdate maps latest release payload`() = runTest {
        coEvery { api.checkApkVersion(120) } returns ApiEnvelopeDto(
            ok = true,
            data = AppVersionCheckResponseDto(
                currentVersionCode = 120,
                latestVersionCode = 123,
                updateAvailable = true,
                release = latestReleaseResponse(),
            ),
        )

        val result = repository.checkForUpdate(120)

        assertEquals(120, result.currentVersionCode)
        assertEquals(123, result.latestVersionCode)
        assertTrue(result.updateAvailable)
        assertEquals("1.2.3", result.release.versionName)
        assertEquals("https://example.com/chat.apk", result.release.downloadUrl)
    }

    @Test
    fun `getLatestRelease maps public apk metadata`() = runTest {
        coEvery { api.getLatestApkRelease() } returns ApiEnvelopeDto(
            ok = true,
            data = latestReleaseResponse(),
        )

        val result = repository.getLatestRelease()

        assertEquals(123, result.versionCode)
        assertEquals("chat.apk", result.fileName)
        assertEquals(1024L, result.fileSize)
        assertEquals(300, result.downloadUrlExpiresIn)
    }

    private fun latestReleaseResponse() = LatestAppReleaseResponseDto(
        platform = "android",
        versionName = "1.2.3",
        versionCode = 123,
        fileName = "chat.apk",
        fileSize = 1024,
        sha256 = "a".repeat(64),
        changelog = "Bug fixes",
        contentType = "application/vnd.android.package-archive",
        uploadedAt = "2026-04-25T09:00:00Z",
        downloadUrl = "https://example.com/chat.apk",
        downloadUrlExpiresIn = 300,
    )
}
