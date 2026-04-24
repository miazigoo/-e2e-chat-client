package com.example.securechatapp.data.files

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.example.securechatapp.core.crypto.AttachmentCryptoEngine

@Singleton
class EncryptedAttachmentFileManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val attachmentCryptoEngine: AttachmentCryptoEngine,
) {
    private val prefs by lazy {
        context.getSharedPreferences("encrypted_attachment_files", Context.MODE_PRIVATE)
    }

    suspend fun downloadAndDecryptBytes(
        downloadUrl: String,
        blobKeyBase64: String,
        blobNonceBase64: String,
    ): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(downloadUrl)
            .get()
            .build()

        val encryptedBytes = okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Failed to download encrypted attachment: HTTP ${response.code}")
            }
            response.body?.bytes() ?: error("Empty encrypted attachment response body")
        }

        attachmentCryptoEngine.decryptBlob(
            ciphertext = encryptedBytes,
            keyBase64 = blobKeyBase64,
            nonceBase64 = blobNonceBase64,
        )
    }

    suspend fun saveDecryptedAttachmentToDownloads(
        attachmentId: Int,
        downloadUrl: String,
        fileName: String,
        mimeType: String?,
        blobKeyBase64: String,
        blobNonceBase64: String,
    ): Uri? = withContext(Dispatchers.IO) {
        val plainBytes = downloadAndDecryptBytes(
            downloadUrl = downloadUrl,
            blobKeyBase64 = blobKeyBase64,
            blobNonceBase64 = blobNonceBase64,
        )

        val resolver = context.contentResolver
        val outputFileName = buildOutputFileName(
            fileName = fileName,
            mimeType = mimeType,
        )

        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, outputFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType ?: "application/octet-stream")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/SecureChat"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, values) ?: return@withContext null

        try {
            resolver.openOutputStream(uri)?.use { output ->
                output.write(plainBytes)
                output.flush()
            } ?: error("Failed to open output stream for decrypted attachment")

            val completeValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, completeValues, null, null)

            saveRecord(
                attachmentId = attachmentId,
                uri = uri.toString(),
                mimeType = mimeType,
                fileName = outputFileName,
            )

            uri
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    fun getAttachmentState(attachmentId: Int): AttachmentLocalState {
        val record = getRecord(attachmentId) ?: return AttachmentLocalState.NOT_DOWNLOADED
        val parsedUri = runCatching { Uri.parse(record.uri) }.getOrNull()
            ?: return AttachmentLocalState.NOT_DOWNLOADED

        return if (uriExists(parsedUri)) {
            AttachmentLocalState.DOWNLOADED
        } else {
            clearRecord(attachmentId)
            AttachmentLocalState.NOT_DOWNLOADED
        }
    }

    fun openSavedAttachment(attachmentId: Int): Boolean {
        val record = getRecord(attachmentId) ?: return false
        val uri = runCatching { Uri.parse(record.uri) }.getOrNull() ?: return false

        if (!uriExists(uri)) {
            clearRecord(attachmentId)
            return false
        }

        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, record.mimeType ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val resolved = intent.resolveActivity(context.packageManager) != null
        if (!resolved) return false

        context.startActivity(intent)
        return true
    }

    private fun uriExists(uri: Uri): Boolean {
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
    }

    private fun saveRecord(
        attachmentId: Int,
        uri: String,
        mimeType: String?,
        fileName: String,
    ) {
        prefs.edit()
            .putString(keyUri(attachmentId), uri)
            .putString(keyMimeType(attachmentId), mimeType)
            .putString(keyFileName(attachmentId), fileName)
            .apply()
    }

    private fun getRecord(attachmentId: Int): SavedAttachmentRecord? {
        val uri = prefs.getString(keyUri(attachmentId), null) ?: return null
        return SavedAttachmentRecord(
            uri = uri,
            mimeType = prefs.getString(keyMimeType(attachmentId), null),
            fileName = prefs.getString(keyFileName(attachmentId), null).orEmpty(),
        )
    }

    private fun clearRecord(attachmentId: Int) {
        prefs.edit()
            .remove(keyUri(attachmentId))
            .remove(keyMimeType(attachmentId))
            .remove(keyFileName(attachmentId))
            .apply()
    }

    private fun keyUri(attachmentId: Int) = "secure_attachment_uri_$attachmentId"
    private fun keyMimeType(attachmentId: Int) = "secure_attachment_mime_$attachmentId"
    private fun keyFileName(attachmentId: Int) = "secure_attachment_name_$attachmentId"

    private fun buildOutputFileName(
        fileName: String,
        mimeType: String?,
    ): String {
        val sanitized = sanitizeFileName(fileName)
            .ifBlank { "attachment_${System.currentTimeMillis()}" }

        val originalExtension = sanitized
            .substringAfterLast('.', "")
            .takeIf { it.isNotBlank() }

        val baseName = if (originalExtension != null) {
            sanitized.substringBeforeLast('.', sanitized)
        } else {
            sanitized
        }

        val resolvedExtension = originalExtension
            ?: mimeType
                ?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                ?.takeIf { it.isNotBlank() }

        val timestamp = System.currentTimeMillis()

        return if (resolvedExtension.isNullOrBlank()) {
            "${baseName}_$timestamp"
        } else {
            "${baseName}_$timestamp.$resolvedExtension"
        }
    }

    private fun sanitizeFileName(value: String): String {
        return value
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
    }

    private data class SavedAttachmentRecord(
        val uri: String,
        val mimeType: String?,
        val fileName: String,
    )
}
