package com.example.securechatapp.data.files

import android.content.Context
import com.example.securechatapp.core.crypto.AttachmentCryptoEngine
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedAttachmentFileManagerTest {

    private val crypto = AttachmentCryptoEngine()

    @Test
    fun `downloadAndDecryptBytes returns original bytes for encrypted attachment`() = runTest {
        val plainBytes = "classified attachment".toByteArray()
        val encrypted = crypto.encryptBlob(plainBytes)
        val okHttpClient = respondingClient(
            statusCode = 200,
            body = encrypted.ciphertext,
        )
        val manager = EncryptedAttachmentFileManager(
            context = mockk<Context>(relaxed = true),
            okHttpClient = okHttpClient,
            attachmentCryptoEngine = crypto,
        )

        val result = manager.downloadAndDecryptBytes(
            downloadUrl = "https://storage.example.test/download/1",
            blobKeyBase64 = encrypted.keyBase64,
            blobNonceBase64 = encrypted.nonceBase64,
        )

        assertArrayEquals(plainBytes, result)
    }

    @Test
    fun `downloadAndDecryptBytes fails on storage http error`() = runTest {
        val okHttpClient = respondingClient(
            statusCode = 404,
            body = "missing".toByteArray(),
        )
        val manager = EncryptedAttachmentFileManager(
            context = mockk<Context>(relaxed = true),
            okHttpClient = okHttpClient,
            attachmentCryptoEngine = crypto,
        )

        val error = runCatching {
            manager.downloadAndDecryptBytes(
                downloadUrl = "https://storage.example.test/download/404",
                blobKeyBase64 = "unused",
                blobNonceBase64 = "unused",
            )
        }.exceptionOrNull()

        requireNotNull(error)
        assertTrue(error.message.orEmpty().contains("Failed to download encrypted attachment: HTTP 404"))
    }

    private fun respondingClient(
        statusCode: Int,
        body: ByteArray,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(statusCode)
                    .message(if (statusCode in 200..299) "OK" else "FAIL")
                    .body(body.toResponseBody())
                    .build()
            }
            .build()
    }
}
