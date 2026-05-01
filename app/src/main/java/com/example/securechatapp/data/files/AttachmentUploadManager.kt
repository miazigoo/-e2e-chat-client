package com.example.securechatapp.data.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.securechatapp.app.di.StorageHttpClient
import com.example.securechatapp.core.crypto.AttachmentCryptoEngine
import com.example.securechatapp.core.crypto.EncryptedAttachmentDescriptor
import com.example.securechatapp.data.remote.api.ChatBackendApi
import com.example.securechatapp.data.remote.dto.CompleteUploadSessionRequestDto
import com.example.securechatapp.data.remote.dto.CreateUploadSessionRequestDto
import com.example.securechatapp.data.remote.dto.InitAttachmentItemRequestDto
import com.example.securechatapp.data.remote.dto.InitAttachmentsRequestDto
import com.example.securechatapp.data.repository.parseBackendApiException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import retrofit2.HttpException
import okio.Buffer
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
    private val json: Json,
    @StorageHttpClient private val uploadHttpClient: OkHttpClient,
) {
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

        val session = apiCall {
            api.createUploadSession(
                CreateUploadSessionRequestDto(
                    conversationId = conversationId,
                    filesExpectedCount = 1,
                )
            ).data
        }

        val init = apiCall {
            api.initAttachments(
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
                        ),
                    )
                )
            ).data
        }

        val item = init.items.firstOrNull() ?: error("Сервер не вернул attachment item")
        val uploadUrl = item.uploadUrl ?: error("Сервер не вернул upload_url")

        val requestBuilder = Request.Builder()
            .url(uploadUrl)
            .put(fileBytes.toProgressRequestBody(mimeType))

        item.uploadHeaders.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }

        val response = uploadHttpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            response.close()
            error("Не удалось загрузить файл в storage: HTTP ${response.code}")
        }
        response.close()

        apiCall {
            api.completeUploadSession(
                sessionId = session.sessionId,
                body = CompleteUploadSessionRequestDto(
                    attachmentIds = listOf(item.attachmentId)
                )
            )
        }

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
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): UploadedEncryptedAttachment = withContext(Dispatchers.IO) {
        val plainBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Не удалось прочитать файл")

        val fileName = queryDisplayName(uri) ?: "file.bin"
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val fileSize = querySize(uri) ?: plainBytes.size.toLong()

        val encryptedBlob = attachmentCryptoEngine.encryptBlob(plainBytes)
        val encryptedFileName = attachmentCryptoEngine.encryptText(fileName)

        val encryptedFileNameBase64 = attachmentCryptoEngine.toBase64(
            encryptedFileName.ciphertext
        )

        val session = apiCall {
            api.createUploadSession(
                CreateUploadSessionRequestDto(
                    conversationId = conversationId,
                    filesExpectedCount = 1,
                )
            ).data
        }

        val init = apiCall {
            api.initAttachments(
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
                        ),
                    )
                )
            ).data
        }

        val item = init.items.firstOrNull() ?: error("Сервер не вернул attachment item")
        val uploadUrl = item.uploadUrl ?: error("Сервер не вернул upload_url")

        val requestBuilder = Request.Builder()
            .url(uploadUrl)
            .put(
                encryptedBlob.ciphertext.toProgressRequestBody(mimeType) { written, total ->
                    onProgress(written, total)
                }
            )

        item.uploadHeaders.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }

        val response = uploadHttpClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            response.close()
            error("Не удалось загрузить encrypted blob в storage: HTTP ${response.code}")
        }
        response.close()

        apiCall {
            api.completeUploadSession(
                sessionId = session.sessionId,
                body = CompleteUploadSessionRequestDto(
                    attachmentIds = listOf(item.attachmentId)
                )
            )
        }

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

    suspend fun uploadEncryptedAttachments(
        conversationId: Int,
        uris: List<Uri>,
        onProgress: (Int) -> Unit = {},
    ): List<UploadedEncryptedAttachment> {
        if (uris.isEmpty()) {
            onProgress(100)
            return emptyList()
        }

        val weights = uris.map { uri ->
            querySize(uri)?.coerceAtLeast(1L) ?: 1L
        }
        val totalWeight = weights.sum().coerceAtLeast(1L)
        var uploadedWeight = 0L
        onProgress(0)

        return uris.mapIndexed { index, uri ->
            val currentWeight = weights[index]
            uploadSingleEncryptedAttachment(
                conversationId = conversationId,
                uri = uri,
                onProgress = { written, total ->
                    val fraction = if (total <= 0L) 0.0 else written.toDouble() / total.toDouble()
                    val weightedUploaded = uploadedWeight + (currentWeight * fraction).toLong()
                    val percent = ((weightedUploaded.toDouble() / totalWeight.toDouble()) * 100.0)
                        .toInt()
                        .coerceIn(0, 100)
                    onProgress(percent)
                },
            )
                .also {
                    uploadedWeight += currentWeight
                    val percent = ((uploadedWeight.toDouble() / totalWeight.toDouble()) * 100.0)
                        .toInt()
                        .coerceIn(0, 100)
                    onProgress(percent)
                }
        }
    }

    private fun ByteArray.toProgressRequestBody(
        mimeType: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): RequestBody {
        val contentType = mimeType.toMediaTypeOrNull()
        val data = this
        return object : RequestBody() {
            override fun contentType() = contentType

            override fun contentLength(): Long = data.size.toLong()

            override fun writeTo(sink: BufferedSink) {
                val total = data.size.toLong()
                var written = 0L
                val buffer = Buffer()
                val chunkSize = 8 * 1024

                while (written < total) {
                    val remaining = (total - written).toInt()
                    val byteCount = minOf(chunkSize, remaining)
                    buffer.write(data, written.toInt(), byteCount)
                    sink.write(buffer, byteCount.toLong())
                    written += byteCount
                    onProgress(written, total)
                }
            }
        }
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

    private suspend fun <T> apiCall(block: suspend () -> T): T {
        return try {
            block()
        } catch (error: HttpException) {
            throw parseBackendApiException(json, error)
        }
    }
}
