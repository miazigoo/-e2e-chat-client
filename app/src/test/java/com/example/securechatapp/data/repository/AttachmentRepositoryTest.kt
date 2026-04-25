package com.example.securechatapp.data.repository

import com.example.securechatapp.data.remote.api.ChatBackendApi
import com.example.securechatapp.data.remote.dto.ApiEnvelopeDto
import com.example.securechatapp.data.remote.dto.AttachmentMetadataItemDto
import com.example.securechatapp.data.remote.dto.GetAttachmentResponseDto
import com.example.securechatapp.data.remote.dto.ListMessageAttachmentsResponseDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentRepositoryTest {

    private val api = mockk<ChatBackendApi>()
    private val json = Json { ignoreUnknownKeys = true }

    private val repository = AttachmentRepository(
        api = api,
        json = json,
    )

    @Test
    fun `listMessageAttachments falls back to generated name and keeps downloadable linked attachment`() = runTest {
        coEvery { api.listMessageAttachments(55) } returns ApiEnvelopeDto(
            ok = true,
            data = ListMessageAttachmentsResponseDto(
                messageId = 55,
                items = listOf(
                    attachmentMetadataItem(
                        attachmentId = 7,
                        encryptedFileName = "",
                        mimeHint = "image/png",
                        uploadStatus = "linked",
                        deletedAt = null,
                    )
                ),
            ),
        )

        val result = repository.listMessageAttachments(55)

        assertEquals(1, result.size)
        assertEquals("image_7", result.first().fileName)
        assertTrue(result.first().canDownload)
    }

    @Test
    fun `listMessageAttachments marks init attachment as unavailable`() = runTest {
        coEvery { api.listMessageAttachments(56) } returns ApiEnvelopeDto(
            ok = true,
            data = ListMessageAttachmentsResponseDto(
                messageId = 56,
                items = listOf(
                    attachmentMetadataItem(
                        attachmentId = 8,
                        uploadStatus = "init",
                        deletedAt = null,
                    )
                ),
            ),
        )

        val result = repository.listMessageAttachments(56)

        assertEquals(1, result.size)
        assertFalse(result.first().canDownload)
    }

    @Test
    fun `getAttachmentDownloadInfo returns null when backend says attachment is unavailable`() = runTest {
        coEvery { api.getAttachmentMetadata(11) } returns ApiEnvelopeDto(
            ok = true,
            data = attachmentResponse(
                attachmentId = 11,
                canDownload = false,
                downloadUrl = "https://files.example.test/11",
            ),
        )

        val result = repository.getAttachmentDownloadInfo(11)

        assertNull(result)
    }

    @Test
    fun `getAttachmentDownloadInfo falls back to generated file name when encrypted name missing`() = runTest {
        coEvery { api.getAttachmentMetadata(12) } returns ApiEnvelopeDto(
            ok = true,
            data = attachmentResponse(
                attachmentId = 12,
                encryptedFileName = null,
                mimeHint = "audio/ogg",
                canDownload = true,
                downloadUrl = "https://files.example.test/12",
            ),
        )

        val result = repository.getAttachmentDownloadInfo(12)

        requireNotNull(result)
        assertEquals("audio_12", result.fileName)
        assertEquals("https://files.example.test/12", result.downloadUrl)
    }

    @Test
    fun `getAttachmentDownloadInfo returns null when download url is blank`() = runTest {
        coEvery { api.getAttachmentMetadata(13) } returns ApiEnvelopeDto(
            ok = true,
            data = attachmentResponse(
                attachmentId = 13,
                canDownload = true,
                downloadUrl = "   ",
            ),
        )

        val result = repository.getAttachmentDownloadInfo(13)

        assertNull(result)
    }

    private fun attachmentMetadataItem(
        attachmentId: Int,
        encryptedFileName: String? = "file.enc",
        mimeHint: String? = "application/pdf",
        uploadStatus: String = "linked",
        deletedAt: String? = null,
    ) = AttachmentMetadataItemDto(
        attachmentId = attachmentId,
        attachmentUuid = "att-$attachmentId",
        messageId = 100,
        encryptedFileName = encryptedFileName,
        encryptedMetadata = null,
        fileSize = 2048,
        mimeHint = mimeHint,
        sha256EncryptedBlob = "a".repeat(64),
        bucketName = "attachments",
        storageKey = "attachments/$attachmentId",
        uploadStatus = uploadStatus,
        createdAt = "2026-01-01T00:00:00Z",
        expiresAt = null,
        deletedAt = deletedAt,
    )

    private fun attachmentResponse(
        attachmentId: Int,
        encryptedFileName: String? = "file.enc",
        mimeHint: String? = "application/pdf",
        canDownload: Boolean = true,
        downloadUrl: String? = "https://files.example.test/$attachmentId",
    ) = GetAttachmentResponseDto(
        attachmentId = attachmentId,
        attachmentUuid = "att-$attachmentId",
        messageId = 100,
        encryptedFileName = encryptedFileName,
        encryptedMetadata = null,
        fileSize = 2048,
        mimeHint = mimeHint,
        sha256EncryptedBlob = "b".repeat(64),
        bucketName = "attachments",
        storageKey = "attachments/$attachmentId",
        uploadStatus = "linked",
        createdAt = "2026-01-01T00:00:00Z",
        expiresAt = null,
        deletedAt = null,
        canDownload = canDownload,
        downloadUrl = downloadUrl,
    )
}
