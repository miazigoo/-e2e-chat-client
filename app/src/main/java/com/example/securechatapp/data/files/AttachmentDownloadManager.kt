package com.example.securechatapp.data.files

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

enum class AttachmentLocalState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED,
}

data class AttachmentDownloadEvent(
    val attachmentId: Int,
    val state: AttachmentLocalState,
)

@Singleton
class AttachmentDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableSharedFlow<AttachmentDownloadEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<AttachmentDownloadEvent> = _events.asSharedFlow()

    private val downloadManager: DownloadManager
        get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val prefs by lazy {
        context.getSharedPreferences("attachment_downloads", Context.MODE_PRIVATE)
    }

    private var receiverRegistered = false

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (downloadId <= 0L) return

            val attachmentId = getAttachmentIdByDownloadId(downloadId) ?: return
            val state = getAttachmentState(attachmentId)

            scope.launch {
                _events.emit(
                    AttachmentDownloadEvent(
                        attachmentId = attachmentId,
                        state = state,
                    )
                )
            }
        }
    }

    init {
        ensureReceiverRegistered()
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

        scope.launch {
            _events.emit(
                AttachmentDownloadEvent(
                    attachmentId = attachmentId,
                    state = AttachmentLocalState.DOWNLOADING,
                )
            )
        }

        return downloadId
    }

    fun getAttachmentState(attachmentId: Int): AttachmentLocalState {
        val record = getRecord(attachmentId) ?: return AttachmentLocalState.NOT_DOWNLOADED
        val status = queryDownloadStatus(record.downloadId) ?: run {
            clearRecord(attachmentId, record.downloadId)
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

    private fun ensureReceiverRegistered() {
        if (receiverRegistered) return

        ContextCompat.registerReceiver(
            context,
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
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
            .putInt(keyAttachmentIdByDownloadId(downloadId), attachmentId)
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

    private fun getAttachmentIdByDownloadId(downloadId: Long): Int? {
        val value = prefs.getInt(keyAttachmentIdByDownloadId(downloadId), -1)
        return value.takeIf { it > 0 }
    }

    private fun clearRecord(attachmentId: Int, downloadId: Long? = null) {
        val resolvedDownloadId = downloadId ?: prefs.getLong(keyDownloadId(attachmentId), -1L)

        prefs.edit().apply {
            remove(keyDownloadId(attachmentId))
            remove(keyFileName(attachmentId))
            remove(keyMimeType(attachmentId))
            if (resolvedDownloadId > 0L) {
                remove(keyAttachmentIdByDownloadId(resolvedDownloadId))
            }
        }.apply()
    }

    private fun keyDownloadId(attachmentId: Int) = "attachment_download_id_$attachmentId"
    private fun keyFileName(attachmentId: Int) = "attachment_file_name_$attachmentId"
    private fun keyMimeType(attachmentId: Int) = "attachment_mime_type_$attachmentId"
    private fun keyAttachmentIdByDownloadId(downloadId: Long) = "download_attachment_id_$downloadId"

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
