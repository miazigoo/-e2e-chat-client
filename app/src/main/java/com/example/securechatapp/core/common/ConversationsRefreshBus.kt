package com.example.securechatapp.core.common

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class ConversationsRefreshBus @Inject constructor() {
    private val _events = MutableSharedFlow<Unit>(
        replay = 1,
        extraBufferCapacity = 1,
    )

    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun requestRefresh() {
        _events.tryEmit(Unit)
    }
}
