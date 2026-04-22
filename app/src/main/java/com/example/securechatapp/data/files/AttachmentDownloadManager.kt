package com.example.securechatapp.data.files

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class AttachmentLocalState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED,
}

@Singleton
class AttachmentDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val downloadManager: DownloadManager
        get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val prefs by lazy {
        context.getSharedPreferences("attachment_downloads", Context.MODE_PRIVATE)
    }

    fun enqueueDownload(
        attachmentId: Int,
        url: String,
        fileName: String,
        mimeType: String?,
    ): Long {
        val finalFileName = buildDownloadFileName(
            fileName = fileName,
            mimeType = mimeType,
        )

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(finalFileName)
            .setDescription("Загрузка вложения")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setMimeType(mimeType)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                finalFileName,
            )

        val downloadId = downloadManager.enqueue(request)
        saveRecord(
            attachmentId = attachmentId,
            downloadId = downloadId,
            fileName = finalFileName,
            mimeType = mimeType,
        )
        return downloadId
    }

    fun getAttachmentState(attachmentId: Int): AttachmentLocalState {
        val record = getRecord(attachmentId) ?: return AttachmentLocalState.NOT_DOWNLOADED
        val status = queryDownloadStatus(record.downloadId) ?: run {
            clearRecord(attachmentId)
            return AttachmentLocalState.NOT_DOWNLOADED
        }

        return when (status) {
            DownloadManager.STATUS_PENDING,
            DownloadManager.STATUS_RUNNING,
            DownloadManager.STATUS_PAUSED,
                -> AttachmentLocalState.DOWNLOADING

            DownloadManager.STATUS_SUCCESSFUL ->
                AttachmentLocalState.DOWNLOADED

            DownloadManager.STATUS_FAILED ->
                AttachmentLocalState.FAILED

            else ->
                AttachmentLocalState.NOT_DOWNLOADED
        }
    }

    fun openDownloadedAttachment(attachmentId: Int): Boolean {
        val record = getRecord(attachmentId) ?: return false
        val state = getAttachmentState(attachmentId)
        if (state != AttachmentLocalState.DOWNLOADED) return false

        val uri = downloadManager.getUriForDownloadedFile(record.downloadId) ?: return false

        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, record.mimeType ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val resolved = intent.resolveActivity(context.packageManager) != null
        if (!resolved) return false

        context.startActivity(intent)
        return true
    }

    private fun queryDownloadStatus(downloadId: Long): Int? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return cursor.getInt(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            )
        }
    }

    private fun saveRecord(
        attachmentId: Int,
        downloadId: Long,
        fileName: String,
        mimeType: String?,
    ) {
        prefs.edit()
            .putLong(keyDownloadId(attachmentId), downloadId)
            .putString(keyFileName(attachmentId), fileName)
            .putString(keyMimeType(attachmentId), mimeType)
            .apply()
    }

    private fun getRecord(attachmentId: Int): DownloadRecord? {
        val downloadId = prefs.getLong(keyDownloadId(attachmentId), -1L)
        if (downloadId <= 0L) return null

        return DownloadRecord(
            downloadId = downloadId,
            fileName = prefs.getString(keyFileName(attachmentId), null).orEmpty(),
            mimeType = prefs.getString(keyMimeType(attachmentId), null),
        )
    }

    private fun clearRecord(attachmentId: Int) {
        prefs.edit()
            .remove(keyDownloadId(attachmentId))
            .remove(keyFileName(attachmentId))
            .remove(keyMimeType(attachmentId))
            .apply()
    }

    private fun keyDownloadId(attachmentId: Int) = "attachment_download_id_$attachmentId"
    private fun keyFileName(attachmentId: Int) = "attachment_file_name_$attachmentId"
    private fun keyMimeType(attachmentId: Int) = "attachment_mime_type_$attachmentId"

    private fun buildDownloadFileName(
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

    private data class DownloadRecord(
        val downloadId: Long,
        val fileName: String,
        val mimeType: String?,
    )
}
