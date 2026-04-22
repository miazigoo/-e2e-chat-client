package com.example.securechatapp.data.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.securechatapp.data.remote.api.ChatBackendApi
import com.example.securechatapp.data.remote.dto.CompleteUploadSessionRequestDto
import com.example.securechatapp.data.remote.dto.CreateUploadSessionRequestDto
import com.example.securechatapp.data.remote.dto.InitAttachmentItemRequestDto
import com.example.securechatapp.data.remote.dto.InitAttachmentsRequestDto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class AttachmentUploadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ChatBackendApi,
) {
    private val uploadHttpClient = OkHttpClient.Builder().build()

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
}