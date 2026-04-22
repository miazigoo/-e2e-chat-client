package com.example.securechatapp.data.files

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttachmentDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun enqueueDownload(
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

        val downloadManager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        return downloadManager.enqueue(request)
    }

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
}
