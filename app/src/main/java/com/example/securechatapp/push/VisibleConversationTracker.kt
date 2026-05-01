package com.example.securechatapp.push

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

@Singleton
class VisibleConversationTracker @Inject constructor() {
    private val appVisible = MutableStateFlow(false)
    private val conversationId = MutableStateFlow<Int?>(null)

    fun setAppVisible(visible: Boolean) {
        appVisible.value = visible
    }

    fun setConversationId(value: Int?) {
        conversationId.value = value
    }

    fun isViewingConversation(targetConversationId: Int): Boolean {
        return appVisible.value && conversationId.value == targetConversationId
    }
}
