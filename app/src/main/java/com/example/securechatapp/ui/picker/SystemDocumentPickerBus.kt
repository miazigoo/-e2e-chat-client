package com.example.securechatapp.ui.picker

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class SystemDocumentPickerResult(
    val requestKey: String,
    val uris: List<String>,
)

object SystemDocumentPickerBus {
    private val _results = MutableSharedFlow<SystemDocumentPickerResult>(
        extraBufferCapacity = 16,
    )
    val results = _results.asSharedFlow()

    fun publish(
        requestKey: String,
        uris: List<String>,
    ) {
        _results.tryEmit(
            SystemDocumentPickerResult(
                requestKey = requestKey,
                uris = uris,
            )
        )
    }
}
