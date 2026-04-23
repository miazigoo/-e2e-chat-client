package com.example.securechatapp.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechatapp.data.files.AttachmentDownloadManager
import com.example.securechatapp.data.files.AttachmentLocalState
import com.example.securechatapp.data.files.AttachmentUploadManager
import com.example.securechatapp.data.remote.dto.UserSearchItemDto
import com.example.securechatapp.data.remote.websocket.RealtimeEvent
import com.example.securechatapp.data.remote.websocket.RealtimeWebSocketManager
import com.example.securechatapp.data.repository.BackendRepository
import com.example.securechatapp.data.files.AttachmentDownloadEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
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
    val attachmentSheetMessageId: Int? = null,
    val attachmentLocalStates: Map<Int, AttachmentLocalState> = emptyMap(),
    val selectedMessageAttachments: List<BackendRepository.AttachmentUi> = emptyList(),
    val isLoadingAttachments: Boolean = false,
    val downloadingAttachmentId: Int? = null,
    val imagePreviewAttachmentId: Int? = null,
    val imagePreviewUrl: String? = null,
    val imagePreviewFileName: String? = null,
    val isLoadingImagePreview: Boolean = false,
)

@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val repo: BackendRepository,
    private val attachmentUploadManager: AttachmentUploadManager,
    private val attachmentDownloadManager: AttachmentDownloadManager,
    private val realtimeWebSocketManager: RealtimeWebSocketManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatsUiState())
    val state: StateFlow<ChatsUiState> = _state.asStateFlow()

    private var pollingJob: Job? = null
    private var heartbeatJob: Job? = null
    private var wsPingJob: Job? = null

    init {
        observeRealtimeEvents()
        observeAttachmentDownloadEvents()
        refreshConversations()
        startHeartbeat()
        startWsPing()
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

    fun createConversation(
        userId: Int,
        onCreated: (Int) -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                _state.value = _state.value.copy(isLoading = true, error = null)

                val conversationId = repo.createConversation(userId)
                val items = repo.listConversations()

                _state.value = _state.value.copy(
                    isLoading = false,
                    conversations = items,
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

    fun openConversation(conversationId: Int) {
        val previousConversationId = _state.value.activeConversationId
        if (previousConversationId != null && previousConversationId != conversationId) {
            realtimeWebSocketManager.unsubscribeConversation(previousConversationId)
        }

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

                realtimeWebSocketManager.connectIfNeeded()
                realtimeWebSocketManager.subscribeConversation(conversationId)

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

        _state.value.activeConversationId?.let {
            realtimeWebSocketManager.unsubscribeConversation(it)
        }

        _state.value = _state.value.copy(
            activeConversationId = null,
            activeConversationTitle = "",
            activePeerUserId = null,
            messages = emptyList(),
            error = null,
            info = null,
            imagePreviewAttachmentId = null,
            imagePreviewUrl = null,
            imagePreviewFileName = null,
            isLoadingImagePreview = false,
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
            repo.logoutSession()
            pollingJob?.cancel()
            heartbeatJob?.cancel()
            wsPingJob?.cancel()
            realtimeWebSocketManager.disconnect()
            _state.value = ChatsUiState()
            onLoggedOut()
        }
    }

    private fun observeRealtimeEvents() {
        viewModelScope.launch {
            realtimeWebSocketManager.events.collect { event ->
                when (event) {
                    is RealtimeEvent.ConversationEvent -> {
                        val activeConversationId = _state.value.activeConversationId
                        if (activeConversationId == event.conversationId) {
                            refreshActiveConversation(
                                markDelivered = true,
                                markRead = true,
                            )
                        } else {
                            refreshConversations()
                        }
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

    private fun observeAttachmentDownloadEvents() {
        viewModelScope.launch {
            attachmentDownloadManager.events.collect { event: AttachmentDownloadEvent ->
                _state.value = _state.value.copy(
                    attachmentLocalStates = _state.value.attachmentLocalStates + (
                            event.attachmentId to event.state
                            )
                )

                when (event.state) {
                    AttachmentLocalState.DOWNLOADED -> {
                        _state.value = _state.value.copy(
                            downloadingAttachmentId = if (_state.value.downloadingAttachmentId == event.attachmentId) {
                                null
                            } else {
                                _state.value.downloadingAttachmentId
                            },
                            info = "Файл скачан",
                        )
                    }

                    AttachmentLocalState.FAILED -> {
                        _state.value = _state.value.copy(
                            downloadingAttachmentId = if (_state.value.downloadingAttachmentId == event.attachmentId) {
                                null
                            } else {
                                _state.value.downloadingAttachmentId
                            },
                            error = "Не удалось скачать файл",
                        )
                    }

                    AttachmentLocalState.DOWNLOADING,
                    AttachmentLocalState.NOT_DOWNLOADED -> Unit
                }
            }
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
                delay(10_000)
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

    private fun startWsPing() {
        wsPingJob?.cancel()
        wsPingJob = viewModelScope.launch {
            while (isActive) {
                realtimeWebSocketManager.sendPing()
                delay(25_000)
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
        wsPingJob?.cancel()
        realtimeWebSocketManager.disconnect()
        super.onCleared()
    }

    fun showMessageAttachments(messageId: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                attachmentSheetMessageId = messageId,
                selectedMessageAttachments = emptyList(),
                attachmentLocalStates = emptyMap(),
                isLoadingAttachments = true,
                downloadingAttachmentId = null,
                imagePreviewAttachmentId = null,
                imagePreviewUrl = null,
                imagePreviewFileName = null,
                isLoadingImagePreview = false,
                error = null,
            )

            runCatching {
                repo.listMessageAttachments(messageId)
            }.onSuccess { attachments ->
                val localStates = attachments.associate { attachment ->
                    attachment.attachmentId to attachmentDownloadManager.getAttachmentState(
                        attachment.attachmentId
                    )
                }

                _state.value = _state.value.copy(
                    selectedMessageAttachments = attachments,
                    attachmentLocalStates = localStates,
                    isLoadingAttachments = false,
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    attachmentSheetMessageId = null,
                    selectedMessageAttachments = emptyList(),
                    attachmentLocalStates = emptyMap(),
                    isLoadingAttachments = false,
                    error = it.message,
                )
            }
        }
    }

    fun dismissMessageAttachments() {
        _state.value = _state.value.copy(
            attachmentSheetMessageId = null,
            selectedMessageAttachments = emptyList(),
            attachmentLocalStates = emptyMap(),
            isLoadingAttachments = false,
            downloadingAttachmentId = null,
        )
    }

    fun onAttachmentSelected(
        attachment: BackendRepository.AttachmentUi,
    ) {
        val localState = attachmentDownloadManager.getAttachmentState(attachment.attachmentId)

        _state.value = _state.value.copy(
            attachmentLocalStates = _state.value.attachmentLocalStates + (
                    attachment.attachmentId to localState
                    )
        )

        when (localState) {
            AttachmentLocalState.DOWNLOADED -> {
                val opened = attachmentDownloadManager.openDownloadedAttachment(
                    attachment.attachmentId
                )
                if (!opened) {
                    _state.value = _state.value.copy(
                        error = "Не удалось открыть скачанный файл"
                    )
                }
            }

            AttachmentLocalState.DOWNLOADING -> {
                _state.value = _state.value.copy(
                    info = "Файл уже скачивается"
                )
            }

            AttachmentLocalState.FAILED,
            AttachmentLocalState.NOT_DOWNLOADED -> {
                if (attachment.isImage) {
                    previewImageAttachment(attachment)
                } else {
                    downloadAttachment(attachment.attachmentId)
                }
            }
        }
    }

    private fun previewImageAttachment(
        attachment: BackendRepository.AttachmentUi,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                attachmentSheetMessageId = null,
                selectedMessageAttachments = emptyList(),
                isLoadingAttachments = false,
                downloadingAttachmentId = null,
                imagePreviewAttachmentId = attachment.attachmentId,
                imagePreviewUrl = null,
                imagePreviewFileName = attachment.fileName,
                isLoadingImagePreview = true,
                error = null,
                info = null,
            )

            runCatching {
                repo.getAttachmentDownloadInfo(attachment.attachmentId)
            }.onSuccess { previewInfo ->
                if (previewInfo == null) {
                    _state.value = _state.value.copy(
                        imagePreviewAttachmentId = null,
                        imagePreviewUrl = null,
                        imagePreviewFileName = null,
                        isLoadingImagePreview = false,
                        error = "Не удалось получить превью изображения",
                    )
                    return@onSuccess
                }

                _state.value = _state.value.copy(
                    imagePreviewAttachmentId = previewInfo.attachmentId,
                    imagePreviewUrl = previewInfo.downloadUrl,
                    imagePreviewFileName = previewInfo.fileName,
                    isLoadingImagePreview = false,
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    imagePreviewAttachmentId = null,
                    imagePreviewUrl = null,
                    imagePreviewFileName = null,
                    isLoadingImagePreview = false,
                    error = it.message,
                )
            }
        }
    }

    fun dismissImagePreview() {
        _state.value = _state.value.copy(
            imagePreviewAttachmentId = null,
            imagePreviewUrl = null,
            imagePreviewFileName = null,
            isLoadingImagePreview = false,
        )
    }

    fun downloadCurrentPreview() {
        val attachmentId = _state.value.imagePreviewAttachmentId ?: return
        downloadAttachment(attachmentId)
    }

    fun downloadAttachment(attachmentId: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                downloadingAttachmentId = attachmentId,
                attachmentLocalStates = _state.value.attachmentLocalStates + (
                        attachmentId to AttachmentLocalState.DOWNLOADING
                        ),
                error = null,
                info = null,
            )

            runCatching {
                repo.getAttachmentDownloadInfo(attachmentId)
            }.onSuccess { downloadInfo ->
                if (downloadInfo == null) {
                    _state.value = _state.value.copy(
                        downloadingAttachmentId = null,
                        attachmentLocalStates = _state.value.attachmentLocalStates + (
                                attachmentId to AttachmentLocalState.FAILED
                                ),
                        error = "Не удалось получить данные для скачивания",
                    )
                    return@onSuccess
                }

                runCatching {
                    attachmentDownloadManager.enqueueDownload(
                        attachmentId = downloadInfo.attachmentId,
                        url = downloadInfo.downloadUrl,
                        fileName = downloadInfo.fileName,
                        mimeType = downloadInfo.mimeType,
                    )
                }.onSuccess {
                    _state.value = _state.value.copy(
                        downloadingAttachmentId = null,
                        attachmentLocalStates = _state.value.attachmentLocalStates + (
                                attachmentId to AttachmentLocalState.DOWNLOADING
                                ),
                        info = "Скачивание начато",
                    )
                }.onFailure {
                    _state.value = _state.value.copy(
                        downloadingAttachmentId = null,
                        attachmentLocalStates = _state.value.attachmentLocalStates + (
                                attachmentId to AttachmentLocalState.FAILED
                                ),
                        error = it.message ?: "Не удалось начать скачивание",
                    )
                }
            }.onFailure {
                _state.value = _state.value.copy(
                    downloadingAttachmentId = null,
                    attachmentLocalStates = _state.value.attachmentLocalStates + (
                            attachmentId to AttachmentLocalState.FAILED
                            ),
                    error = it.message,
                )
            }
        }
    }
}
