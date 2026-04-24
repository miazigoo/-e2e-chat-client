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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
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

    suspend fun uploadSingleEncryptedAttachment(
        conversationId: Int,
        uri: Uri,
        onProgress: (Float) -> Unit = {},
    ): UploadedEncryptedAttachment = withContext(Dispatchers.IO) {
        onProgress(0f)

        val plainBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Не удалось прочитать файл")

        val fileName = queryDisplayName(uri) ?: "file.bin"
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val plainFileSize = querySize(uri) ?: plainBytes.size.toLong()

        val encryptedBlob = attachmentCryptoEngine.encryptBlob(plainBytes)
        val encryptedFileName = attachmentCryptoEngine.encryptText(fileName)
        val encryptedFileNameBase64 = attachmentCryptoEngine.toBase64(
            encryptedFileName.ciphertext,
        )

        val session = api.createUploadSession(
            CreateUploadSessionRequestDto(
                conversationId = conversationId,
                filesExpectedCount = 1,
            )
        ).data

        val init = api.initAttachments(
            sessionId = session.sessionId,
            body = InitAttachmentsRequestDto(
                items = listOf(
                    InitAttachmentItemRequestDto(
                        encryptedFileName = encryptedFileNameBase64,
                        fileSize = encryptedBlob.ciphertext.size.toLong(),
                        mimeHint = mimeType,
                        sha256EncryptedBlob = encryptedBlob.sha256EncryptedBlob,
                        encryptedMetadata = mapOf(
                            "transport" to "encrypted_blob_v1",
                            "plain_file_size" to plainFileSize.toString(),
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
                ChunkedProgressRequestBody(
                    bytes = encryptedBlob.ciphertext,
                    contentType = mimeType.toMediaTypeOrNull(),
                    onProgress = onProgress,
                )
            )

        item.uploadHeaders.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }

        uploadHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                error("Не удалось загрузить encrypted blob в storage: HTTP ${response.code}")
            }
        }

        api.completeUploadSession(
            sessionId = session.sessionId,
            body = CompleteUploadSessionRequestDto(
                attachmentIds = listOf(item.attachmentId),
            )
        )

        onProgress(1f)

        UploadedEncryptedAttachment(
            attachmentId = item.attachmentId,
            descriptor = EncryptedAttachmentDescriptor(
                attachmentId = item.attachmentId,
                encryptedFileName = encryptedFileNameBase64,
                fileNameKeyBase64 = encryptedFileName.keyBase64,
                fileNameNonceBase64 = encryptedFileName.nonceBase64,
                mimeType = mimeType,
                fileSize = plainFileSize,
                sha256EncryptedBlob = encryptedBlob.sha256EncryptedBlob,
                blobKeyBase64 = encryptedBlob.keyBase64,
                blobNonceBase64 = encryptedBlob.nonceBase64,
            )
        )
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

    private class ChunkedProgressRequestBody(
        private val bytes: ByteArray,
        private val contentType: MediaType?,
        private val onProgress: (Float) -> Unit,
    ) : RequestBody() {

        override fun contentType(): MediaType? = contentType

        override fun contentLength(): Long = bytes.size.toLong()

        override fun writeTo(sink: BufferedSink) {
            var offset = 0
            while (offset < bytes.size) {
                val count = minOf(CHUNK_SIZE_BYTES, bytes.size - offset)
                sink.write(bytes, offset, count)
                offset += count
                onProgress(offset.toFloat() / bytes.size.toFloat())
            }
        }

        private companion object {
            const val CHUNK_SIZE_BYTES = 64 * 1024
        }
    }
}
