package com.example.securechatapp.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechatapp.data.files.AttachmentDownloadEvent
import com.example.securechatapp.data.files.AttachmentDownloadManager
import com.example.securechatapp.data.files.AttachmentLocalState
import com.example.securechatapp.data.files.AttachmentUploadManager
import com.example.securechatapp.data.files.EncryptedAttachmentFileManager
import com.example.securechatapp.data.remote.websocket.RealtimeEvent
import com.example.securechatapp.data.remote.websocket.RealtimeWebSocketManager
import com.example.securechatapp.data.repository.AttachmentRepository
import com.example.securechatapp.data.repository.ConversationRepository
import com.example.securechatapp.data.repository.MessageRepository
import com.example.securechatapp.data.repository.SessionRepository
import com.example.securechatapp.domain.model.AttachmentItem
import com.example.securechatapp.domain.model.ChatMessage
import com.example.securechatapp.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ConversationUiState(
    val conversationId: Int? = null,
    val isLoading: Boolean = false,
    val isLoggingOut: Boolean = false,
    val isUploadingAttachment: Boolean = false,
    val deletingMessageIds: Set<Int> = emptySet(),
    val error: String? = null,
    val info: String? = null,
    val title: String = "",
    val peerUserId: Int? = null,
    val protectionMode: String = "normal",
    val messages: List<ChatMessage> = emptyList(),
    val attachmentSheetMessageId: Int? = null,
    val attachmentLocalStates: Map<Int, AttachmentLocalState> = emptyMap(),
    val selectedMessageAttachments: List<AttachmentItem> = emptyList(),
    val isLoadingAttachments: Boolean = false,
    val downloadingAttachmentId: Int? = null,
    val imagePreviewAttachmentId: Int? = null,
    val imagePreviewUrl: String? = null,
    val imagePreviewBytes: ByteArray? = null,
    val imagePreviewFileName: String? = null,
    val imagePreviewAttachment: AttachmentItem? = null,
    val isLoadingImagePreview: Boolean = false,
)

@HiltViewModel
class ConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val attachmentRepository: AttachmentRepository,
    private val sessionRepository: SessionRepository,
    private val attachmentUploadManager: AttachmentUploadManager,
    private val attachmentDownloadManager: AttachmentDownloadManager,
    private val encryptedAttachmentFileManager: EncryptedAttachmentFileManager,
    private val realtimeWebSocketManager: RealtimeWebSocketManager,
) : ViewModel() {

    private val conversationId: Int = checkNotNull(
        savedStateHandle.get<Int>(Routes.ConversationArg)
    )

    private val _state = MutableStateFlow(
        ConversationUiState(conversationId = conversationId)
    )
    val state: StateFlow<ConversationUiState> = _state.asStateFlow()

    private var pollingJob: Job? = null
    private var wsPingJob: Job? = null

    init {
        observeRealtimeEvents()
        observeAttachmentDownloadEvents()
        loadConversation()
    }

    fun retryLoad() {
        loadConversation()
    }

    fun sendMessage(
        text: String,
        attachmentUri: Uri? = null,
        onSent: () -> Unit = {},
    ) {
        val currentConversationId = _state.value.conversationId ?: return
        val peerUserId = _state.value.peerUserId ?: return
        if (text.isBlank() && attachmentUri == null) return

        viewModelScope.launch {
            runCatching {
                _state.value = _state.value.copy(
                    error = null,
                    info = null,
                    isUploadingAttachment = attachmentUri != null,
                )

                val uploadedEncryptedAttachments = if (attachmentUri != null) {
                    listOf(
                        attachmentUploadManager.uploadSingleEncryptedAttachment(
                            conversationId = currentConversationId,
                            uri = attachmentUri,
                        )
                    )
                } else {
                    emptyList()
                }

                messageRepository.sendMessage(
                    conversationId = currentConversationId,
                    recipientUserId = peerUserId,
                    plainText = text,
                    attachmentIds = uploadedEncryptedAttachments.map { it.attachmentId },
                    attachmentDescriptors = uploadedEncryptedAttachments.map { it.descriptor },
                )

                _state.value = _state.value.copy(isUploadingAttachment = false)

                refreshMessages(
                    markDelivered = false,
                    markRead = false,
                )
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
        val currentConversationId = _state.value.conversationId ?: return
        mutateDeleting(messageId, true)

        viewModelScope.launch {
            runCatching {
                messageRepository.deleteMessagesLocal(
                    conversationId = currentConversationId,
                    messageIds = listOf(messageId),
                )
                refreshMessages(
                    markDelivered = false,
                    markRead = false,
                )
                _state.value = _state.value.copy(info = "Сообщение скрыто локально")
            }.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
            mutateDeleting(messageId, false)
        }
    }

    fun deleteMessageGlobal(messageId: Int) {
        val currentConversationId = _state.value.conversationId ?: return
        mutateDeleting(messageId, true)

        viewModelScope.launch {
            runCatching {
                messageRepository.deleteMessagesGlobal(
                    conversationId = currentConversationId,
                    messageIds = listOf(messageId),
                )
                refreshMessages(
                    markDelivered = false,
                    markRead = false,
                )
                _state.value = _state.value.copy(info = "Сообщение удалено для всех")
            }.onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
            mutateDeleting(messageId, false)
        }
    }

    fun showMessageAttachments(message: ChatMessage) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                attachmentSheetMessageId = message.messageId,
                selectedMessageAttachments = emptyList(),
                attachmentLocalStates = emptyMap(),
                isLoadingAttachments = true,
                downloadingAttachmentId = null,
                imagePreviewAttachmentId = null,
                imagePreviewUrl = null,
                imagePreviewBytes = null,
                imagePreviewFileName = null,
                imagePreviewAttachment = null,
                isLoadingImagePreview = false,
                error = null,
            )

            runCatching {
                if (message.attachments.isNotEmpty()) {
                    message.attachments
                } else {
                    attachmentRepository.listMessageAttachments(message.messageId)
                }
            }.onSuccess { attachments ->
                val localStates = attachments.associate { attachment ->
                    attachment.attachmentId to resolveLocalAttachmentState(attachment)
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
        attachment: AttachmentItem,
    ) {
        val localState = resolveLocalAttachmentState(attachment)

        _state.value = _state.value.copy(
            attachmentLocalStates = _state.value.attachmentLocalStates + (
                    attachment.attachmentId to localState
                    )
        )

        when (localState) {
            AttachmentLocalState.DOWNLOADED -> {
                val opened = if (attachment.hasEncryptedBlobKeys) {
                    encryptedAttachmentFileManager.openSavedAttachment(attachment.attachmentId)
                } else {
                    attachmentDownloadManager.openDownloadedAttachment(attachment.attachmentId)
                }

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
                    if (attachment.hasEncryptedBlobKeys) {
                        downloadEncryptedAttachment(attachment)
                    } else {
                        downloadAttachment(attachment.attachmentId)
                    }
                }
            }
        }
    }

    fun dismissImagePreview() {
        _state.value = _state.value.copy(
            imagePreviewAttachmentId = null,
            imagePreviewUrl = null,
            imagePreviewBytes = null,
            imagePreviewFileName = null,
            imagePreviewAttachment = null,
            isLoadingImagePreview = false,
        )
    }

    fun downloadCurrentPreview() {
        val attachment = _state.value.imagePreviewAttachment ?: return
        if (attachment.hasEncryptedBlobKeys) {
            downloadEncryptedAttachment(attachment)
        } else {
            downloadAttachment(attachment.attachmentId)
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

            _state.value = ConversationUiState()
            onLoggedOut()
        }
    }

    private fun loadConversation() {
        viewModelScope.launch {
            runCatching {
                _state.value = _state.value.copy(
                    isLoading = true,
                    error = null,
                    info = null,
                )

                val conversation = conversationRepository.getConversation(conversationId)

                _state.value = _state.value.copy(
                    conversationId = conversation.conversationId,
                    title = conversation.title,
                    peerUserId = conversation.peerUserId,
                    protectionMode = conversation.protectionMode,
                    messages = emptyList(),
                )

                realtimeWebSocketManager.connectIfNeeded()
                realtimeWebSocketManager.subscribeConversation(conversationId)

                refreshMessages(
                    markDelivered = true,
                    markRead = true,
                )
                startPolling()
                startWsPing()
            }.onFailure {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = it.message,
                )
            }
        }
    }

    private fun observeRealtimeEvents() {
        viewModelScope.launch {
            realtimeWebSocketManager.events.collect { event ->
                when (event) {
                    is RealtimeEvent.ConversationEvent -> {
                        if (event.conversationId == conversationId) {
                            refreshMessages(
                                markDelivered = true,
                                markRead = true,
                            )
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
                refreshMessages(
                    markDelivered = true,
                    markRead = true,
                )
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

    private suspend fun refreshMessages(
        markDelivered: Boolean,
        markRead: Boolean,
    ) {
        val currentConversationId = _state.value.conversationId ?: return
        val peerUserId = _state.value.peerUserId ?: return

        runCatching {
            val messages = messageRepository.listMessages(
                conversationId = currentConversationId,
                peerUserId = peerUserId,
            )

            _state.value = _state.value.copy(
                isLoading = false,
                messages = messages,
                error = null,
            )

            val deliveredCount = if (markDelivered) {
                messageRepository.markIncomingMessagesAsDelivered(messages)
            } else {
                0
            }

            val readCount = if (markRead) {
                messageRepository.markIncomingMessagesAsRead(messages)
            } else {
                0
            }

            if (deliveredCount > 0 || readCount > 0) {
                val refreshedMessages = messageRepository.listMessages(
                    conversationId = currentConversationId,
                    peerUserId = peerUserId,
                )

                _state.value = _state.value.copy(
                    messages = refreshedMessages,
                )
            }
        }.onFailure {
            _state.value = _state.value.copy(
                isLoading = false,
                error = it.message,
            )
        }
    }

    private fun previewImageAttachment(
        attachment: AttachmentItem,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                attachmentSheetMessageId = null,
                selectedMessageAttachments = emptyList(),
                isLoadingAttachments = false,
                downloadingAttachmentId = null,
                imagePreviewAttachmentId = attachment.attachmentId,
                imagePreviewUrl = null,
                imagePreviewBytes = null,
                imagePreviewFileName = attachment.fileName,
                imagePreviewAttachment = attachment,
                isLoadingImagePreview = true,
                error = null,
                info = null,
            )

            if (attachment.hasEncryptedBlobKeys) {
                runCatching {
                    val downloadInfo = attachmentRepository.getAttachmentDownloadInfo(attachment.attachmentId)
                        ?: error("Не удалось получить ссылку на encrypted attachment")

                    encryptedAttachmentFileManager.downloadAndDecryptBytes(
                        downloadUrl = downloadInfo.downloadUrl,
                        blobKeyBase64 = attachment.blobKeyBase64.orEmpty(),
                        blobNonceBase64 = attachment.blobNonceBase64.orEmpty(),
                    )
                }.onSuccess { bytes ->
                    _state.value = _state.value.copy(
                        imagePreviewAttachmentId = attachment.attachmentId,
                        imagePreviewUrl = null,
                        imagePreviewBytes = bytes,
                        imagePreviewFileName = attachment.fileName,
                        imagePreviewAttachment = attachment,
                        isLoadingImagePreview = false,
                    )
                }.onFailure {
                    _state.value = _state.value.copy(
                        imagePreviewAttachmentId = null,
                        imagePreviewUrl = null,
                        imagePreviewBytes = null,
                        imagePreviewFileName = null,
                        imagePreviewAttachment = null,
                        isLoadingImagePreview = false,
                        error = it.message,
                    )
                }
            } else {
                runCatching {
                    attachmentRepository.getAttachmentDownloadInfo(attachment.attachmentId)
                }.onSuccess { previewInfo ->
                    if (previewInfo == null) {
                        _state.value = _state.value.copy(
                            imagePreviewAttachmentId = null,
                            imagePreviewUrl = null,
                            imagePreviewBytes = null,
                            imagePreviewFileName = null,
                            imagePreviewAttachment = null,
                            isLoadingImagePreview = false,
                            error = "Не удалось получить превью изображения",
                        )
                        return@onSuccess
                    }

                    _state.value = _state.value.copy(
                        imagePreviewAttachmentId = previewInfo.attachmentId,
                        imagePreviewUrl = previewInfo.downloadUrl,
                        imagePreviewBytes = null,
                        imagePreviewFileName = previewInfo.fileName,
                        imagePreviewAttachment = attachment,
                        isLoadingImagePreview = false,
                    )
                }.onFailure {
                    _state.value = _state.value.copy(
                        imagePreviewAttachmentId = null,
                        imagePreviewUrl = null,
                        imagePreviewBytes = null,
                        imagePreviewFileName = null,
                        imagePreviewAttachment = null,
                        isLoadingImagePreview = false,
                        error = it.message,
                    )
                }
            }
        }
    }

    private fun downloadEncryptedAttachment(
        attachment: AttachmentItem,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                downloadingAttachmentId = attachment.attachmentId,
                attachmentLocalStates = _state.value.attachmentLocalStates + (
                        attachment.attachmentId to AttachmentLocalState.DOWNLOADING
                        ),
                error = null,
                info = null,
            )

            runCatching {
                val downloadInfo = attachmentRepository.getAttachmentDownloadInfo(attachment.attachmentId)
                    ?: error("Не удалось получить данные для скачивания")

                encryptedAttachmentFileManager.saveDecryptedAttachmentToDownloads(
                    attachmentId = attachment.attachmentId,
                    downloadUrl = downloadInfo.downloadUrl,
                    fileName = attachment.fileName,
                    mimeType = attachment.mimeType,
                    blobKeyBase64 = attachment.blobKeyBase64.orEmpty(),
                    blobNonceBase64 = attachment.blobNonceBase64.orEmpty(),
                )
            }.onSuccess { uri ->
                if (uri == null) {
                    _state.value = _state.value.copy(
                        downloadingAttachmentId = null,
                        attachmentLocalStates = _state.value.attachmentLocalStates + (
                                attachment.attachmentId to AttachmentLocalState.FAILED
                                ),
                        error = "Не удалось сохранить расшифрованный файл",
                    )
                    return@onSuccess
                }

                _state.value = _state.value.copy(
                    downloadingAttachmentId = null,
                    attachmentLocalStates = _state.value.attachmentLocalStates + (
                            attachment.attachmentId to AttachmentLocalState.DOWNLOADED
                            ),
                    info = "Файл расшифрован и сохранён в Downloads",
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    downloadingAttachmentId = null,
                    attachmentLocalStates = _state.value.attachmentLocalStates + (
                            attachment.attachmentId to AttachmentLocalState.FAILED
                            ),
                    error = it.message ?: "Не удалось скачать и расшифровать файл",
                )
            }
        }
    }

    private fun downloadAttachment(attachmentId: Int) {
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
                attachmentRepository.getAttachmentDownloadInfo(attachmentId)
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

    private fun resolveLocalAttachmentState(
        attachment: AttachmentItem,
    ): AttachmentLocalState {
        return if (attachment.hasEncryptedBlobKeys) {
            encryptedAttachmentFileManager.getAttachmentState(attachment.attachmentId)
        } else {
            attachmentDownloadManager.getAttachmentState(attachment.attachmentId)
        }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        wsPingJob?.cancel()
        realtimeWebSocketManager.unsubscribeConversation(conversationId)
        super.onCleared()
    }
}
