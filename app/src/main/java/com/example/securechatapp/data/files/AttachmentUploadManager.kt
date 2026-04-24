package com.example.securechatapp.data.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.securechatapp.core.crypto.AttachmentCryptoEngine
import com.example.securechatapp.core.crypto.EncryptedAttachmentDescriptor
import com.example.securechatapp.data.remote.api.ChatBackendApi
import com.example.securechatapp.data.remote.dto.CompleteUploadSessionRequestDto
import com.example.securechatapp.data.remote.dto.CreateUploadSessionRequestDto
import com.example.securechatapp.data.remote.dto.InitAttachmentItemRequestDto
import com.example.securechatapp.data.remote.dto.InitAttachmentsRequestDto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink

data class UploadedEncryptedAttachment(
    val attachmentId: Int,
    val descriptor: EncryptedAttachmentDescriptor,
)

@Singleton
class AttachmentUploadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ChatBackendApi,
    private val attachmentCryptoEngine: AttachmentCryptoEngine,
) {
    private val uploadHttpClient = OkHttpClient.Builder().build()

    /**
     * Legacy dev/plain upload path.
     * Kept for compatibility until encrypted attachment transport is fully wired.
     */
    suspend fun uploadSingleAttachment(
        conversationId: Int,
        uri: Uri,
    ): Int = withContext(Dispatchers.IO) {
        val fileBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Не удалось прочитать файл")

        val fileName = queryDisplayName(uri) ?: "file.bin"
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val fileSize = querySize(uri) ?: fileBytes.size.toLong()
        val sha256 = sha256Hex(fileBytes)

        val session = api.createUploadSession(
            CreateUploadSessionRequestDto(
                conversationId = conversationId,
                filesExpectedCount = 1,
            )
        ).data

        onProgress(35)

        val init = api.initAttachments(
            sessionId = session.sessionId,
            body = InitAttachmentsRequestDto(
                items = listOf(
                    InitAttachmentItemRequestDto(
                        encryptedFileName = fileName,
                        fileSize = fileSize,
                        mimeHint = mimeType,
                        sha256EncryptedBlob = sha256,
                        encryptedMetadata = mapOf(
                            "dev_attachment" to "true",
                            "original_name" to fileName,
                        ),
                    )
                )
            )
        ).data

        val item = init.items.firstOrNull() ?: error("Сервер не вернул attachment item")
        val uploadUrl = item.uploadUrl ?: error("Сервер не вернул upload_url")

        val requestBuilder = Request.Builder()
            .url(uploadUrl)
            .put(fileBytes.toRequestBody(mimeType.toMediaTypeOrNull()))

        item.uploadHeaders.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }

        val response = uploadHttpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            response.close()
            error("Не удалось загрузить файл в storage: HTTP ${response.code}")
        }
        response.close()

        api.completeUploadSession(
            sessionId = session.sessionId,
            body = CompleteUploadSessionRequestDto(
                attachmentIds = listOf(item.attachmentId)
            )
        )

        item.attachmentId
    }

    /**
     * Encrypted blob upload path.
     * This prepares production-style attachment transport but should be wired
     * into message send/decrypt flow in the next step.
     */
    suspend fun uploadSingleEncryptedAttachment(
        conversationId: Int,
        uri: Uri,
        onProgress: (Int) -> Unit = {},
    ): UploadedEncryptedAttachment = withContext(Dispatchers.IO) {
        val plainBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Не удалось прочитать файл")

        val fileName = queryDisplayName(uri) ?: "file.bin"
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val fileSize = querySize(uri) ?: plainBytes.size.toLong()

        onProgress(5)

        val encryptedBlob = attachmentCryptoEngine.encryptBlob(plainBytes)
        onProgress(20)
        val encryptedFileName = attachmentCryptoEngine.encryptText(fileName)

        val encryptedFileNameBase64 = attachmentCryptoEngine.toBase64(
            encryptedFileName.ciphertext
        )

        val session = api.createUploadSession(
            CreateUploadSessionRequestDto(
                conversationId = conversationId,
                filesExpectedCount = 1,
            )
        ).data

        onProgress(35)

        val init = api.initAttachments(
            sessionId = session.sessionId,
            body = InitAttachmentsRequestDto(
                items = listOf(
                    InitAttachmentItemRequestDto(
                        encryptedFileName = encryptedFileNameBase64,
                        fileSize = fileSize,
                        mimeHint = mimeType,
                        sha256EncryptedBlob = encryptedBlob.sha256EncryptedBlob,
                        encryptedMetadata = mapOf(
                            "transport" to "encrypted_blob_v1",
                        ),
                    )
                )
            )
        ).data

        val item = init.items.firstOrNull() ?: error("Сервер не вернул attachment item")
        val uploadUrl = item.uploadUrl ?: error("Сервер не вернул upload_url")

        val requestBuilder = Request.Builder()
            .url(uploadUrl)
            .put(
                ProgressRequestBody(
                    bytes = encryptedBlob.ciphertext,
                    mediaType = mimeType.toMediaTypeOrNull(),
                ) { uploaded, total ->
                    val percent = if (total <= 0L) {
                        70
                    } else {
                        40 + ((uploaded.coerceAtMost(total) * 50) / total).toInt()
                    }
                    onProgress(percent.coerceIn(40, 90))
                }
            )

        item.uploadHeaders.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }

        executeUploadWithRetry(requestBuilder.build())
        onProgress(92)

        api.completeUploadSession(
            sessionId = session.sessionId,
            body = CompleteUploadSessionRequestDto(
                attachmentIds = listOf(item.attachmentId)
            )
        )

        onProgress(100)

        UploadedEncryptedAttachment(
            attachmentId = item.attachmentId,
            descriptor = EncryptedAttachmentDescriptor(
                attachmentId = item.attachmentId,
                encryptedFileName = encryptedFileNameBase64,
                fileNameKeyBase64 = encryptedFileName.keyBase64,
                fileNameNonceBase64 = encryptedFileName.nonceBase64,
                mimeType = mimeType,
                fileSize = fileSize,
                sha256EncryptedBlob = encryptedBlob.sha256EncryptedBlob,
                blobKeyBase64 = encryptedBlob.keyBase64,
                blobNonceBase64 = encryptedBlob.nonceBase64,
            )
        )
    }

    private fun executeUploadWithRetry(
        request: Request,
    ) {
        var lastError: Throwable? = null

        repeat(MAX_UPLOAD_ATTEMPTS) { attemptIndex ->
            try {
                uploadHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return
                    lastError = IOException("Storage upload failed: HTTP ${response.code}")
                }
            } catch (e: IOException) {
                lastError = e
            }

            if (attemptIndex < MAX_UPLOAD_ATTEMPTS - 1) {
                Thread.sleep(calculateUploadRetryDelayMillis(attemptIndex + 1))
            }
        }

        throw IOException(lastError?.message ?: "Storage upload failed")
    }

    private fun calculateUploadRetryDelayMillis(
        attemptNumber: Int,
    ): Long {
        return (1_000L shl (attemptNumber - 1)).coerceAtMost(8_000L)
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

    private fun querySize(uri: Uri): Long? {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) cursor.getLong(idx) else null
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { b -> "%02x".format(b) }
    }

    private class ProgressRequestBody(
        private val bytes: ByteArray,
        private val mediaType: MediaType?,
        private val onProgress: (uploaded: Long, total: Long) -> Unit,
    ) : RequestBody() {

        override fun contentType(): MediaType? = mediaType

        override fun contentLength(): Long = bytes.size.toLong()

        override fun writeTo(sink: BufferedSink) {
            val total = bytes.size.toLong()
            var offset = 0
            while (offset < bytes.size) {
                val bytesToWrite = minOf(UPLOAD_CHUNK_SIZE_BYTES, bytes.size - offset)
                sink.write(bytes, offset, bytesToWrite)
                offset += bytesToWrite
                onProgress(offset.toLong(), total)
            }
        }
    }

    private companion object {
        const val MAX_UPLOAD_ATTEMPTS = 3
        const val UPLOAD_CHUNK_SIZE_BYTES = 64 * 1024
    }
}
