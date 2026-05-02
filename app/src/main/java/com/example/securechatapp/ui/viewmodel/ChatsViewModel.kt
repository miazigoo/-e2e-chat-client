package com.example.securechatapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechatapp.BuildConfig
import com.example.securechatapp.core.common.ConversationsRefreshBus
import com.example.securechatapp.data.files.ApkUpdateInstallState
import com.example.securechatapp.data.files.ApkUpdateManager
import com.example.securechatapp.data.remote.websocket.RealtimeEvent
import com.example.securechatapp.data.remote.websocket.RealtimeWebSocketManager
import com.example.securechatapp.data.repository.AppUpdateRepository
import com.example.securechatapp.data.repository.AppUpdateStateRepository
import com.example.securechatapp.data.repository.ChatCacheRepository
import com.example.securechatapp.data.repository.ConversationRepository
import com.example.securechatapp.data.repository.SessionRepository
import com.example.securechatapp.domain.model.AppReleaseInfo
import com.example.securechatapp.domain.model.ConversationEventTypes
import com.example.securechatapp.domain.model.ConversationListItem
import com.example.securechatapp.domain.model.UserSearchItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatsUiState(
    val isLoading: Boolean = false,
    val isLoggingOut: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val users: List<UserSearchItem> = emptyList(),
    val conversations: List<ConversationListItem> = emptyList(),
    val updateRelease: AppReleaseInfo? = null,
    val updateInstallState: ApkUpdateInstallState = ApkUpdateInstallState(),
)

@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val chatCacheRepository: ChatCacheRepository,
    private val sessionRepository: SessionRepository,
    private val realtimeWebSocketManager: RealtimeWebSocketManager,
    private val conversationsRefreshBus: ConversationsRefreshBus,
    private val appUpdateRepository: AppUpdateRepository,
    private val apkUpdateManager: ApkUpdateManager,
    private val appUpdateStateRepository: AppUpdateStateRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatsUiState())
    val state: StateFlow<ChatsUiState> = _state.asStateFlow()

    init {
        observeCachedConversations()
        observeRefreshBus()
        observeRealtimeEvents()
        observeUpdateInstaller()
        connectRealtime()
        refreshConversations()
    }

    fun refreshConversations() {
        viewModelScope.launch {
            runCatching {
                _state.value = _state.value.copy(
                    isLoading = true,
                    error = null,
                )

                val items = conversationRepository.listConversations()
                chatCacheRepository.replaceConversations(items)

                _state.value = _state.value.copy(
                    isLoading = false,
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = it.message,
                )
            }
        }
    }

    fun searchUsers(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            _state.value = _state.value.copy(users = emptyList())
            return
        }

        viewModelScope.launch {
            runCatching {
                _state.value = _state.value.copy(
                    isLoading = true,
                    error = null,
                )

                val users = conversationRepository.searchUsers(normalizedQuery)
                _state.value = _state.value.copy(
                    isLoading = false,
                    users = users,
                    error = null,
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = it.message,
                )
            }
        }
    }

    fun createConversation(
        userId: Int,
        onCreated: (Int) -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                _state.value = _state.value.copy(
                    isLoading = true,
                    error = null,
                )

                val safety = conversationRepository.getUserSafety(userId)
                if (!safety.canStartConversation) {
                    val message = when {
                        safety.pendingDeletion -> "Пользователь запланировал удаление аккаунта"
                        !safety.hasActiveDevice -> "У пользователя нет активного устройства"
                        !safety.supportsEncryptedChat -> "Пользователь пока не готов к защищенному чату"
                        else -> "Нельзя начать чат с этим пользователем"
                    }
                    error(message)
                }

                val conversationId = conversationRepository.createConversation(userId)
                val items = conversationRepository.listConversations()
                chatCacheRepository.replaceConversations(items)

                _state.value = _state.value.copy(
                    isLoading = false,
                    users = emptyList(),
                    error = null,
                )

                onCreated(conversationId)
            }.onFailure {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = it.message,
                )
            }
        }
    }

    fun toggleConversationPin(
        conversationId: Int,
        isPinned: Boolean,
    ) {
        viewModelScope.launch {
            runCatching {
                _state.value = _state.value.copy(
                    isLoading = true,
                    error = null,
                    info = null,
                )

                val items = conversationRepository.pinConversation(
                    conversationId = conversationId,
                    isPinned = !isPinned,
                )
                chatCacheRepository.replaceConversations(items)
                _state.value = _state.value.copy(
                    isLoading = false,
                    info = if (isPinned) "Чат откреплён" else "Чат закреплён",
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = it.message,
                )
            }
        }
    }

    fun deleteConversation(
        conversationId: Int,
    ) {
        viewModelScope.launch {
            runCatching {
                _state.value = _state.value.copy(
                    isLoading = true,
                    error = null,
                    info = null,
                )

                val items = conversationRepository.deleteConversation(conversationId)
                chatCacheRepository.replaceConversations(items)
                _state.value = _state.value.copy(
                    isLoading = false,
                    info = "Чат удалён",
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = it.message,
                )
            }
        }
    }

    fun syncFcmToken(token: String?) {
        viewModelScope.launch {
            sessionRepository.updateFcmToken(token)
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoggingOut = true,
                error = null,
            )

            realtimeWebSocketManager.disconnect()
            sessionRepository.logoutSession()

            _state.value = ChatsUiState()
            onLoggedOut()
        }
    }

    fun dismissUpdateBanner() {
        _state.value = _state.value.copy(
            updateRelease = null,
        )
    }

    fun startAppUpdate() {
        val release = _state.value.updateRelease ?: return
        apkUpdateManager.startOrInstall(release)
    }

    private fun connectRealtime() {
        viewModelScope.launch {
            realtimeWebSocketManager.connectIfNeeded()
        }
    }

    private fun observeCachedConversations() {
        viewModelScope.launch {
            chatCacheRepository.observeConversations().collect { conversations ->
                _state.value = _state.value.copy(
                    conversations = conversations,
                )
            }
        }
    }

    private fun observeRefreshBus() {
        viewModelScope.launch {
            conversationsRefreshBus.events.collect {
                refreshConversations()
            }
        }
    }

    private fun observeRealtimeEvents() {
        viewModelScope.launch {
            realtimeWebSocketManager.events.collect { event ->
                when (event) {
                    is RealtimeEvent.Connected -> {
                        refreshConversations()
                    }

                    is RealtimeEvent.ConversationEvent -> {
                        if (shouldRefreshConversations(event.eventType)) {
                            refreshConversations()
                        }
                    }

                    is RealtimeEvent.DeviceApprovalRequested -> {
                        _state.value = _state.value.copy(
                            info = "Новое устройство запрашивает доступ. Откройте настройки, чтобы подтвердить или отклонить вход.",
                        )
                    }

                    is RealtimeEvent.Error -> {
                        _state.value = _state.value.copy(
                            info = event.message
                        )
                    }

                    is RealtimeEvent.AppUpdateAvailable -> {
                        handleAppUpdateAvailable(event.release)
                    }

                    else -> Unit
                }
            }
        }
    }

    private fun observeUpdateInstaller() {
        viewModelScope.launch {
            apkUpdateManager.state.collect { installState ->
                _state.value = _state.value.copy(
                    updateInstallState = installState,
                )
            }
        }
    }

    private fun handleAppUpdateAvailable(release: AppReleaseInfo) {
        viewModelScope.launch {
            val enrichedRelease = runCatching {
                appUpdateRepository.getLatestRelease()
            }.getOrElse { release }

            if (enrichedRelease.versionCode <= BuildConfig.VERSION_CODE) {
                _state.value = _state.value.copy(
                    updateRelease = null,
                    error = null,
                )
                apkUpdateManager.syncState()
                appUpdateStateRepository.clearIfInstalled()
                return@launch
            }

            apkUpdateManager.syncState()
            appUpdateStateRepository.publishRelease(enrichedRelease)

            _state.value = _state.value.copy(
                updateRelease = enrichedRelease,
                error = null,
            )
        }
    }

    private fun shouldRefreshConversations(
        eventType: String,
    ): Boolean {
        return when (eventType) {
            ConversationEventTypes.MESSAGE_CREATED,
            ConversationEventTypes.MESSAGE_FORWARDED,
            ConversationEventTypes.MESSAGE_DELETED_GLOBAL,
            ConversationEventTypes.MESSAGE_HIDDEN_FOR_USER,
            ConversationEventTypes.CONVERSATION_CLEARED_LOCAL,
            ConversationEventTypes.CONVERSATION_CLEARED_GLOBAL,
            ConversationEventTypes.CONVERSATION_PURGED,
            ConversationEventTypes.CONVERSATION_PINNED,
            ConversationEventTypes.CONVERSATION_UNPINNED,
            ConversationEventTypes.CONVERSATION_DELETED -> true

            else -> false
        }
    }
}
