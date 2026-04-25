package com.example.securechatapp.data.files

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.example.securechatapp.core.crypto.AttachmentCryptoEngine
import com.example.securechatapp.data.remote.api.ChatBackendApi
import com.example.securechatapp.data.remote.dto.ApiEnvelopeDto
import com.example.securechatapp.data.remote.dto.AttachmentInitItemDto
import com.example.securechatapp.data.remote.dto.CompleteUploadSessionResponseDto
import com.example.securechatapp.data.remote.dto.CreateUploadSessionResponseDto
import com.example.securechatapp.data.remote.dto.InitAttachmentsResponseDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentUploadManagerTest {

    private val context = mockk<Context>()
    private val resolver = mockk<ContentResolver>()
    private val api = mockk<ChatBackendApi>()
    private val crypto = AttachmentCryptoEngine()
    private val json = Json { ignoreUnknownKeys = true }
    private val uri = mockk<Uri>()

    @Test
    fun `uploadSingleEncryptedAttachment uploads encrypted blob and completes session`() = runTest {
        val storageClient = storageClient(statusCode = 200)
        val manager = AttachmentUploadManager(
            context = context,
            api = api,
            attachmentCryptoEngine = crypto,
            json = json,
            uploadHttpClient = storageClient,
        )

        stubFile(
            bytes = "secret file payload".toByteArray(),
            fileName = "report.txt",
            mimeType = "text/plain",
            size = 19L,
        )

        coEvery { api.createUploadSession(any()) } returns ApiEnvelopeDto(
            ok = true,
            data = CreateUploadSessionResponseDto(
                sessionId = 41,
                sessionUuid = "session-41",
                conversationId = 9,
                filesExpectedCount = 1,
                filesUploadedCount = 0,
                status = "init",
                expiresAt = "2026-01-01T00:00:00Z",
            ),
        )
        coEvery { api.initAttachments(41, any()) } returns ApiEnvelopeDto(
            ok = true,
            data = InitAttachmentsResponseDto(
                sessionId = 41,
                sessionUuid = "session-41",
                items = listOf(
                    AttachmentInitItemDto(
                        attachmentId = 77,
                        attachmentUuid = "att-77",
                        storageKey = "attachments/77",
                        bucketName = "attachments",
                        uploadStatus = "init",
                        expiresAt = "2026-01-01T00:00:00Z",
                        uploadUrl = "https://storage.example.test/upload/77",
                        uploadMethod = "PUT",
                        uploadHeaders = mapOf("x-test-header" to "value"),
                    )
                ),
            ),
        )
        coEvery { api.completeUploadSession(41, any()) } returns ApiEnvelopeDto(
            ok = true,
            data = CompleteUploadSessionResponseDto(
                sessionId = 41,
                sessionUuid = "session-41",
                status = "completed",
                filesExpectedCount = 1,
                filesUploadedCount = 1,
                completedAt = "2026-01-01T00:00:00Z",
            ),
        )

        val result = manager.uploadSingleEncryptedAttachment(
            conversationId = 9,
            uri = uri,
        )

        assertEquals(77, result.attachmentId)
        assertEquals(77, result.descriptor.attachmentId)
        assertEquals("text/plain", result.descriptor.mimeType)
        assertEquals(19L, result.descriptor.fileSize)
        assertNotNull(result.descriptor.encryptedFileName)
        assertTrue(result.descriptor.blobKeyBase64.isNotBlank())
        assertTrue(result.descriptor.blobNonceBase64.isNotBlank())
        coVerify(exactly = 1) { api.completeUploadSession(41, any()) }
    }

    @Test
    fun `uploadSingleEncryptedAttachment fails when storage rejects upload`() = runTest {
        val storageClient = storageClient(statusCode = 500)
        val manager = AttachmentUploadManager(
            context = context,
            api = api,
            attachmentCryptoEngine = crypto,
            json = json,
            uploadHttpClient = storageClient,
        )

        stubFile(
            bytes = "payload".toByteArray(),
            fileName = "report.txt",
            mimeType = "text/plain",
            size = 7L,
        )

        coEvery { api.createUploadSession(any()) } returns ApiEnvelopeDto(
            ok = true,
            data = CreateUploadSessionResponseDto(
                sessionId = 42,
                sessionUuid = "session-42",
                conversationId = 10,
                filesExpectedCount = 1,
                filesUploadedCount = 0,
                status = "init",
                expiresAt = "2026-01-01T00:00:00Z",
            ),
        )
        coEvery { api.initAttachments(42, any()) } returns ApiEnvelopeDto(
            ok = true,
            data = InitAttachmentsResponseDto(
                sessionId = 42,
                sessionUuid = "session-42",
                items = listOf(
                    AttachmentInitItemDto(
                        attachmentId = 78,
                        attachmentUuid = "att-78",
                        storageKey = "attachments/78",
                        bucketName = "attachments",
                        uploadStatus = "init",
                        expiresAt = "2026-01-01T00:00:00Z",
                        uploadUrl = "https://storage.example.test/upload/78",
                        uploadMethod = "PUT",
                        uploadHeaders = emptyMap(),
                    )
                ),
            ),
        )

        val error = runCatching {
            manager.uploadSingleEncryptedAttachment(
                conversationId = 10,
                uri = uri,
            )
        }.exceptionOrNull()

        requireNotNull(error)
        assertTrue(error.message.orEmpty().contains("Не удалось загрузить encrypted blob в storage: HTTP 500"))
        coVerify(exactly = 0) { api.completeUploadSession(any(), any()) }
    }

    private fun stubFile(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        size: Long,
    ) {
        val displayNameCursor = mockk<Cursor>()
        val sizeCursor = mockk<Cursor>()

        every { context.contentResolver } returns resolver
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(bytes)
        every { resolver.getType(uri) } returns mimeType
        every {
            resolver.query(
                eq(uri),
                any(),
                isNull(),
                isNull(),
                isNull(),
            )
        } answers {
            val projection = secondArg<Array<String>>()
            when (projection.single()) {
                OpenableColumns.DISPLAY_NAME -> displayNameCursor
                OpenableColumns.SIZE -> sizeCursor
                else -> null
            }
        }

        every { displayNameCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
        every { displayNameCursor.moveToFirst() } returns true
        every { displayNameCursor.getString(0) } returns fileName
        every { displayNameCursor.close() } returns Unit

        every { sizeCursor.getColumnIndex(OpenableColumns.SIZE) } returns 0
        every { sizeCursor.moveToFirst() } returns true
        every { sizeCursor.isNull(0) } returns false
        every { sizeCursor.getLong(0) } returns size
        every { sizeCursor.close() } returns Unit
    }

    private fun storageClient(statusCode: Int): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(statusCode)
                    .message(if (statusCode in 200..299) "OK" else "FAIL")
                    .body("{}".toResponseBody())
                    .build()
            }
            .build()
    }
}
