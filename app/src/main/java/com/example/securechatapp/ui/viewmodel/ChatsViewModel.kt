package com.example.securechatapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechatapp.core.common.ConversationsRefreshBus
import com.example.securechatapp.data.remote.websocket.RealtimeEvent
import com.example.securechatapp.data.remote.websocket.RealtimeWebSocketManager
import com.example.securechatapp.data.repository.ChatCacheRepository
import com.example.securechatapp.data.repository.ConversationRepository
import com.example.securechatapp.data.repository.SessionRepository
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
)

@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val chatCacheRepository: ChatCacheRepository,
    private val sessionRepository: SessionRepository,
    private val realtimeWebSocketManager: RealtimeWebSocketManager,
    private val conversationsRefreshBus: ConversationsRefreshBus,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatsUiState())
    val state: StateFlow<ChatsUiState> = _state.asStateFlow()

    init {
        observeCachedConversations()
        observeRefreshBus()
        observeRealtimeEvents()
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
        if (query.isBlank()) {
            _state.value = _state.value.copy(users = emptyList())
            return
        }

        viewModelScope.launch {
            runCatching {
                val users = conversationRepository.searchUsers(query)
                _state.value = _state.value.copy(
                    users = users,
                    error = null,
                )
            }.onFailure {
                _state.value = _state.value.copy(error = it.message)
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
                        refreshConversations()
                    }

                    is RealtimeEvent.Error -> {
                        _state.value = _state.value.copy(
                            info = "Realtime: ${event.message}"
                        )
                    }

                    else -> Unit
                }
            }
        }
    }
}
