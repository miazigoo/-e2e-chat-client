package com.example.securechatapp.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechatapp.data.files.AttachmentUploadManager
import com.example.securechatapp.data.remote.dto.UserSearchItemDto
import com.example.securechatapp.data.repository.BackendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatsUiState(
    val isLoading: Boolean = false,
    val isLoggingOut: Boolean = false,
    val isUploadingAttachment: Boolean = false,
    val deletingMessageIds: Set<Int> = emptySet(),
    val error: String? = null,
    val info: String? = null,
    val users: List<UserSearchItemDto> = emptyList(),
    val conversations: List<BackendRepository.ConversationUi> = emptyList(),
    val activeConversationId: Int? = null,
    val activeConversationTitle: String = "",
    val activePeerUserId: Int? = null,
    val messages: List<BackendRepository.MessageUi> = emptyList(),
)

@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val repo: BackendRepository,
    private val attachmentUploadManager: AttachmentUploadManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatsUiState())
    val state: StateFlow<ChatsUiState> = _state.asStateFlow()

    private var pollingJob: Job? = null
    private var heartbeatJob: Job? = null

    init {
        refreshConversations()
        startHeartbeat()
    }

    fun refreshConversations() {
        viewModelScope.launch {
            runCatching {
                _state.value = _state.value.copy(isLoading = true, error = null)
                val items = repo.listConversations()
                _state.value = _state.value.copy(
                    isLoading = false,
                    conversations = items,
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
                val users = repo.searchUsers(query)
                _state.value = _state.value.copy(users = users, error = null)
            }.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    fun createConversation(userId: Int) {
        viewModelScope.launch {
            runCatching {
                val conversationId = repo.createConversation(userId)
                refreshConversations()
                openConversation(conversationId)
            }.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
        }
    }

    fun openConversation(conversationId: Int) {
        pollingJob?.cancel()

        viewModelScope.launch {
            runCatching {
                _state.value = _state.value.copy(isLoading = true, error = null, info = null)

                val conversation = repo.getConversation(conversationId)

                _state.value = _state.value.copy(
                    activeConversationId = conversationId,
                    activeConversationTitle = conversation.title ?: "Chat $conversationId",
                    activePeerUserId = conversation.peerUserId,
                    messages = emptyList(),
                )

                refreshActiveConversation(
                    markDelivered = true,
                    markRead = true,
                )
                startPolling()
            }.onFailure {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = it.message,
                )
            }
        }
    }

    fun backToConversationList() {
        pollingJob?.cancel()
        _state.value = _state.value.copy(
            activeConversationId = null,
            activeConversationTitle = "",
            activePeerUserId = null,
            messages = emptyList(),
            error = null,
            info = null,
        )
        refreshConversations()
    }

    fun sendMessage(
        text: String,
        attachmentUri: Uri? = null,
        onSent: () -> Unit = {},
    ) {
        val conversationId = _state.value.activeConversationId ?: return
        val peerUserId = _state.value.activePeerUserId ?: return
        if (text.isBlank() && attachmentUri == null) return

        viewModelScope.launch {
            runCatching {
                _state.value = _state.value.copy(
                    error = null,
                    info = null,
                    isUploadingAttachment = attachmentUri != null,
                )

                val attachmentIds = if (attachmentUri != null) {
                    listOf(
                        attachmentUploadManager.uploadSingleAttachment(
                            conversationId = conversationId,
                            uri = attachmentUri,
                        )
                    )
                } else {
                    emptyList()
                }

                repo.sendMessage(
                    conversationId = conversationId,
                    recipientUserId = peerUserId,
                    plainText = text,
                    attachmentIds = attachmentIds,
                )

                _state.value = _state.value.copy(isUploadingAttachment = false)

                refreshActiveConversation(
                    markDelivered = false,
                    markRead = false,
                )
                refreshConversations()
                onSent()
            }.onFailure {
                _state.value = _state.value.copy(
                    isUploadingAttachment = false,
                    error = it.message,
                )
            }
        }
    }

    fun deleteMessageLocal(messageId: Int) {
        val conversationId = _state.value.activeConversationId ?: return
        mutateDeleting(messageId, true)

        viewModelScope.launch {
            runCatching {
                repo.deleteMessagesLocal(
                    conversationId = conversationId,
                    messageIds = listOf(messageId),
                )
                refreshActiveConversation(markDelivered = false, markRead = false)
                refreshConversations()
                _state.value = _state.value.copy(info = "Сообщение скрыто локально")
            }.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
            mutateDeleting(messageId, false)
        }
    }

    fun deleteMessageGlobal(messageId: Int) {
        val conversationId = _state.value.activeConversationId ?: return
        mutateDeleting(messageId, true)

        viewModelScope.launch {
            runCatching {
                repo.deleteMessagesGlobal(
                    conversationId = conversationId,
                    messageIds = listOf(messageId),
                )
                refreshActiveConversation(markDelivered = false, markRead = false)
                refreshConversations()
                _state.value = _state.value.copy(info = "Сообщение удалено для всех")
            }.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
            mutateDeleting(messageId, false)
        }
    }

    fun syncFcmToken(token: String?) {
        viewModelScope.launch {
            repo.updateFcmToken(token)
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoggingOut = true, error = null)
            repo.logout()
            pollingJob?.cancel()
            heartbeatJob?.cancel()
            _state.value = ChatsUiState()
            onLoggedOut()
        }
    }

    private fun mutateDeleting(messageId: Int, add: Boolean) {
        val current = _state.value.deletingMessageIds.toMutableSet()
        if (add) current.add(messageId) else current.remove(messageId)
        _state.value = _state.value.copy(deletingMessageIds = current)
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(3000)
                refreshActiveConversation(
                    markDelivered = true,
                    markRead = true,
                )
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (isActive) {
                repo.heartbeat()
                delay(45_000)
            }
        }
    }

    private suspend fun refreshActiveConversation(
        markDelivered: Boolean,
        markRead: Boolean,
    ) {
        val conversationId = _state.value.activeConversationId ?: return
        val peerUserId = _state.value.activePeerUserId ?: return

        runCatching {
            val messages = repo.listMessages(
                conversationId = conversationId,
                peerUserId = peerUserId,
            )

            _state.value = _state.value.copy(
                isLoading = false,
                messages = messages,
                error = null,
            )

            val deliveredCount = if (markDelivered) {
                repo.markIncomingMessagesAsDelivered(messages)
            } else {
                0
            }

            val readCount = if (markRead) {
                repo.markIncomingMessagesAsRead(messages)
            } else {
                0
            }

            if (deliveredCount > 0 || readCount > 0) {
                val refreshedMessages = repo.listMessages(
                    conversationId = conversationId,
                    peerUserId = peerUserId,
                )
                val refreshedConversations = repo.listConversations()

                _state.value = _state.value.copy(
                    messages = refreshedMessages,
                    conversations = refreshedConversations,
                )
            }
        }.onFailure {
            _state.value = _state.value.copy(
                isLoading = false,
                error = it.message,
            )
        }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        heartbeatJob?.cancel()
        super.onCleared()
    }
}