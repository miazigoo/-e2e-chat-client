package com.example.securechatapp.data.repository

import android.content.ContentResolver
import android.content.Context
import com.example.securechatapp.data.remote.api.ChatBackendApi
import com.example.securechatapp.data.remote.dto.ApiEnvelopeDto
import com.example.securechatapp.data.remote.dto.UpdateUserProfileRequestDto
import com.example.securechatapp.data.remote.dto.UserProfileResponseDto
import com.example.securechatapp.data.remote.dto.UserProfileSettingsDto
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserProfileRepositoryTest {

    private val context = mockk<Context>()
    private val contentResolver = mockk<ContentResolver>(relaxed = true)
    private val api = mockk<ChatBackendApi>()
    private val json = Json { ignoreUnknownKeys = true }

    private val repository = UserProfileRepository(
        context = context,
        api = api,
        json = json,
    )

    init {
        every { context.contentResolver } returns contentResolver
    }

    @Test
    fun `getMyProfile maps server response to domain model`() = runTest {
        coEvery { api.getMyProfile() } returns ApiEnvelopeDto(
            ok = true,
            data = profileResponse(),
        )

        val result = repository.getMyProfile()

        assertEquals(7, result.userId)
        assertEquals("public-7", result.publicId)
        assertEquals("alice", result.nickname)
        assertEquals("ru", result.settings.languageCode)
        assertTrue(result.settings.pushNotificationsEnabled)
    }

    @Test
    fun `updateMyProfile normalizes outgoing values and returns updated profile`() = runTest {
        coEvery { api.updateMyProfile(any()) } returns ApiEnvelopeDto(
            ok = true,
            data = profileResponse(
                nickname = "alice-updated",
                theme = "dark",
                pushNotificationsEnabled = false,
            ),
        )

        val result = repository.updateMyProfile(
            UpdateUserProfileInput(
                nickname = "  alice-updated  ",
                fullName = "  Alice Example  ",
                bio = "  bio  ",
                languageCode = " EN ",
                theme = " DARK ",
                pushNotificationsEnabled = false,
                apkUpdateNotificationsEnabled = true,
            )
        )

        assertEquals("alice-updated", result.nickname)
        assertEquals("dark", result.settings.theme)
        assertFalse(result.settings.pushNotificationsEnabled)
        io.mockk.coVerify {
            api.updateMyProfile(
                UpdateUserProfileRequestDto(
                    nickname = "alice-updated",
                    fullName = "Alice Example",
                    bio = "bio",
                    languageCode = "en",
                    theme = "dark",
                    pushNotificationsEnabled = false,
                    apkUpdateNotificationsEnabled = true,
                )
            )
        }
    }

    private fun profileResponse(
        nickname: String = "alice",
        theme: String = "system",
        pushNotificationsEnabled: Boolean = true,
    ) = UserProfileResponseDto(
        userId = 7,
        publicId = "public-7",
        nickname = nickname,
        fullName = "Alice Example",
        bio = "hello",
        avatarUrl = "https://files.example.test/avatar.jpg",
        avatarUpdatedAt = "2026-04-25T09:00:00Z",
        createdAt = "2026-01-01T00:00:00Z",
        settings = UserProfileSettingsDto(
            languageCode = "ru",
            theme = theme,
            pushNotificationsEnabled = pushNotificationsEnabled,
            apkUpdateNotificationsEnabled = true,
        ),
    )
}
