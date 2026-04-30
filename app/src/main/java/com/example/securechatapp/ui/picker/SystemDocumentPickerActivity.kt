package com.example.securechatapp.ui.picker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class SystemDocumentPickerActivity : ComponentActivity() {

    private val pickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            finish()
            return@registerForActivityResult
        }

        val data = result.data
        val requestKey = intent.getStringExtra(EXTRA_REQUEST_KEY).orEmpty()
        val uris = buildList {
            data?.data?.let(::add)
            val clipData = data?.clipData
            if (clipData != null) {
                for (index in 0 until clipData.itemCount) {
                    clipData.getItemAt(index)?.uri?.let(::add)
                }
            }
        }.distinctBy(Uri::toString)

        uris.forEach(::persistReadPermission)
        if (requestKey.isNotBlank()) {
            SystemDocumentPickerBus.publish(
                requestKey = requestKey,
                uris = uris.map(Uri::toString),
            )
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            finish()
            return
        }

        val mimeTypes = intent.getStringArrayExtra(EXTRA_MIME_TYPES)
            ?.takeIf { it.isNotEmpty() }
            ?: arrayOf("*/*")
        val allowMultiple = intent.getBooleanExtra(EXTRA_ALLOW_MULTIPLE, false)

        val pickerIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (mimeTypes.size == 1) mimeTypes[0] else "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }

        pickerLauncher.launch(pickerIntent)
    }

    private fun persistReadPermission(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    companion object {
        private const val EXTRA_MIME_TYPES = "extra_mime_types"
        private const val EXTRA_ALLOW_MULTIPLE = "extra_allow_multiple"
        private const val EXTRA_REQUEST_KEY = "extra_request_key"
        const val REQUEST_ATTACHMENTS = "request_attachments"
        const val REQUEST_AVATAR = "request_avatar"

        fun createIntent(
            activity: Activity,
            mimeTypes: Array<String>,
            allowMultiple: Boolean,
            requestKey: String,
        ): Intent {
            return Intent(activity, SystemDocumentPickerActivity::class.java).apply {
                putExtra(EXTRA_MIME_TYPES, mimeTypes)
                putExtra(EXTRA_ALLOW_MULTIPLE, allowMultiple)
                putExtra(EXTRA_REQUEST_KEY, requestKey)
            }
        }
    }
}
