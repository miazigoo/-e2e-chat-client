package com.example.securechatapp.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechatapp.core.common.ConversationsRefreshBus
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
import com.example.securechatapp.data.repository.SyncRepository
import com.example.securechatapp.domain.model.AttachmentItem
import com.example.securechatapp.domain.model.ChatMessage
import com.example.securechatapp.domain.model.ConversationEventTypes
import com.example.securechatapp.domain.model.ConversationSyncEvent
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

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
    stateHandle: SavedStateHandle,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val attachmentRepository: AttachmentRepository,
    private val syncRepository: SyncRepository,
    private val sessionRepository: SessionRepository,
    private val attachmentUploadManager: AttachmentUploadManager,
    private val attachmentDownloadManager: AttachmentDownloadManager,
    private val encryptedAttachmentFileManager: EncryptedAttachmentFileManager,
    private val realtimeWebSocketManager: RealtimeWebSocketManager,
    private val conversationsRefreshBus: ConversationsRefreshBus,
) : ViewModel() {

    private val savedStateHandle = stateHandle

    private val conversationId: Int = checkNotNull(
        savedStateHandle.get<Int>(Routes.ConversationArg)
    )

    private var lastEventId: Int?
        get() = savedStateHandle.get<Int>(KEY_LAST_EVENT_ID)
        set(value) {
            savedStateHandle[KEY_LAST_EVENT_ID] = value
        }

    private val _state = MutableStateFlow(
        ConversationUiState(conversationId = conversationId)
    )
    val state: StateFlow<ConversationUiState> = _state.asStateFlow()

    private var syncFallbackJob: Job? = null
    private val syncMutex = Mutex()

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

                reloadMessages(
                    markDelivered = false,
                    markRead = false,
                )
                conversationsRefreshBus.requestRefresh()
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
                reloadMessages(
                    markDelivered = false,
                    markRead = false,
                )
                conversationsRefreshBus.requestRefresh()
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
                reloadMessages(
                    markDelivered = false,
                    markRead = false,
                )
                conversationsRefreshBus.requestRefresh()
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

            syncFallbackJob?.cancel()
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

                catchUpCursorToLatest()

                realtimeWebSocketManager.connectIfNeeded()
                realtimeWebSocketManager.subscribeConversation(conversationId)

                reloadMessages(
                    markDelivered = true,
                    markRead = true,
                )

                syncConversation()
                startSyncFallback()
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
                    is RealtimeEvent.Connected -> {
                        syncConversation()
                    }

                    is RealtimeEvent.ConversationEvent -> {
                        if (event.conversationId == conversationId) {
                            syncConversation()
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

    private fun startSyncFallback() {
        syncFallbackJob?.cancel()
        syncFallbackJob = viewModelScope.launch {
            while (isActive) {
                delay(SYNC_FALLBACK_INTERVAL_MS)
                syncConversation()
            }
        }
    }

    private suspend fun catchUpCursorToLatest() {
        var cursor = lastEventId

        while (true) {
            val page = syncRepository.getConversationEvents(
                conversationId = conversationId,
                afterEventId = cursor,
                limit = SYNC_PAGE_SIZE,
            )

            val last = page.items.lastOrNull() ?: break
            cursor = last.eventId
            lastEventId = last.eventId

            if (!page.hasMore) break
        }
    }

    private suspend fun syncConversation() {
        val currentConversationId = _state.value.conversationId ?: return

        syncMutex.withLock {
            var cursor = lastEventId
            var requiresReload = false
            var processedAnyEvents = false

            while (true) {
                val page = syncRepository.getConversationEvents(
                    conversationId = currentConversationId,
                    afterEventId = cursor,
                    limit = SYNC_PAGE_SIZE,
                )

                val events = page.items
                if (events.isEmpty()) break

                processedAnyEvents = true

                events.forEach { event ->
                    cursor = event.eventId
                    lastEventId = event.eventId
                    requiresReload = processConversationEvent(event) || requiresReload
                }

                if (!page.hasMore) break
            }

            if (requiresReload) {
                reloadMessages(
                    markDelivered = true,
                    markRead = true,
                )
            }

            if (processedAnyEvents) {
                conversationsRefreshBus.requestRefresh()
            }
        }
    }

    private fun processConversationEvent(
        event: ConversationSyncEvent,
    ): Boolean {
        return when (event.eventType) {
            ConversationEventTypes.MESSAGE_DELIVERED -> {
                val messageId = event.targetMessageId ?: event.payload?.intValue("message_id")
                val deliveredAt = event.payload?.stringValue("delivered_at") ?: event.createdAt

                if (messageId != null) {
                    applyDeliveredLocally(
                        messageId = messageId,
                        deliveredAt = deliveredAt,
                    )
                }
                false
            }

            ConversationEventTypes.MESSAGE_READ -> {
                val messageId = event.targetMessageId ?: event.payload?.intValue("message_id")
                val readAt = event.payload?.stringValue("read_at") ?: event.createdAt

                if (messageId != null) {
                    applyReadLocally(
                        messageId = messageId,
                        readAt = readAt,
                    )
                }
                false
            }

            ConversationEventTypes.PARTICIPANT_KEY_CHANGED -> {
                _state.value = _state.value.copy(
                    info = "Ключи собеседника были обновлены"
                )
                false
            }

            ConversationEventTypes.MESSAGE_CREATED,
            ConversationEventTypes.MESSAGE_DELETED_GLOBAL,
            ConversationEventTypes.MESSAGE_HIDDEN_FOR_USER,
            ConversationEventTypes.CONVERSATION_CLEARED_LOCAL,
            ConversationEventTypes.CONVERSATION_CLEARED_GLOBAL,
            ConversationEventTypes.FILE_UPLOADED,
            ConversationEventTypes.FILE_DELETED,
            ConversationEventTypes.CONVERSATION_PURGED -> true

            else -> true
        }
    }

    private fun applyDeliveredLocally(
        messageId: Int,
        deliveredAt: String,
    ) {
        var changed = false

        val updated = _state.value.messages.map { message ->
            if (message.messageId != messageId) return@map message

            val nextDeliveredAt = message.deliveredAt ?: deliveredAt
            if (nextDeliveredAt == message.deliveredAt) {
                message
            } else {
                changed = true
                message.copy(deliveredAt = nextDeliveredAt)
            }
        }

        if (changed) {
            _state.value = _state.value.copy(messages = updated)
        }
    }

    private fun applyReadLocally(
        messageId: Int,
        readAt: String,
    ) {
        var changed = false

        val updated = _state.value.messages.map { message ->
            if (message.messageId != messageId) return@map message

            val nextDeliveredAt = message.deliveredAt ?: readAt
            val nextReadAt = message.readAt ?: readAt

            if (nextDeliveredAt == message.deliveredAt && nextReadAt == message.readAt) {
                message
            } else {
                changed = true
                message.copy(
                    deliveredAt = nextDeliveredAt,
                    readAt = nextReadAt,
                )
            }
        }

        if (changed) {
            _state.value = _state.value.copy(messages = updated)
        }
    }

    private suspend fun reloadMessages(
        markDelivered: Boolean,
        markRead: Boolean,
    ) {
        val currentConversationId = _state.value.conversationId ?: return
        val peerUserId = _state.value.peerUserId ?: return

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
            conversationsRefreshBus.requestRefresh()
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
        syncFallbackJob?.cancel()
        realtimeWebSocketManager.unsubscribeConversation(conversationId)
        super.onCleared()
    }

    private companion object {
        const val KEY_LAST_EVENT_ID = "last_event_id"
        const val SYNC_PAGE_SIZE = 200
        const val SYNC_FALLBACK_INTERVAL_MS = 20_000L
    }
}

private fun JsonObject.stringValue(key: String): String? {
    return runCatching {
        this[key]?.jsonPrimitive?.content
    }.getOrNull()
}

private fun JsonObject.intValue(key: String): Int? {
    return stringValue(key)?.toIntOrNull()
}
