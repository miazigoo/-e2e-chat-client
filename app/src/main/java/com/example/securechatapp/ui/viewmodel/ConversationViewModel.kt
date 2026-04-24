package com.example.securechatapp.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechatapp.core.common.ConversationsRefreshBus
import com.example.securechatapp.crypto.sharedsecret.ConversationSharedSecretCrypto
import com.example.securechatapp.data.files.AttachmentDownloadEvent
import com.example.securechatapp.data.files.AttachmentDownloadManager
import com.example.securechatapp.data.files.AttachmentLocalState
import com.example.securechatapp.data.files.AttachmentUploadManager
import com.example.securechatapp.data.files.EncryptedAttachmentFileManager
import com.example.securechatapp.data.remote.websocket.RealtimeEvent
import com.example.securechatapp.data.remote.websocket.RealtimeWebSocketManager
import com.example.securechatapp.data.repository.AttachmentRepository
import com.example.securechatapp.data.repository.ChatCacheRepository
import com.example.securechatapp.data.repository.ConversationRepository
import com.example.securechatapp.data.repository.MessageRepository
import com.example.securechatapp.data.repository.OutboxDispatcher
import com.example.securechatapp.data.repository.OutboxRepository
import com.example.securechatapp.data.repository.SessionRepository
import com.example.securechatapp.data.repository.SyncRepository
import com.example.securechatapp.domain.model.AttachmentItem
import com.example.securechatapp.domain.model.ChatMessage
import com.example.securechatapp.domain.model.ConversationEventTypes
import com.example.securechatapp.domain.model.ConversationSyncEvent
import com.example.securechatapp.domain.model.MessageSendStatus
import com.example.securechatapp.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.OffsetDateTime
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    val conversationUuid: String = "",
    val protectionMode: String = "normal",
    val sharedSecretEnabled: Boolean = false,
    val sharedSecretFingerprint: String? = null,
    val localSharedSecretEnabled: Boolean = false,
    val localSharedSecretFingerprint: String? = null,
    val peerSharedSecretEnabled: Boolean = false,
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
    private val chatCacheRepository: ChatCacheRepository,
    private val outboxRepository: OutboxRepository,
    private val outboxDispatcher: OutboxDispatcher,
    private val syncRepository: SyncRepository,
    private val sessionRepository: SessionRepository,
    private val attachmentUploadManager: AttachmentUploadManager,
    private val attachmentDownloadManager: AttachmentDownloadManager,
    private val encryptedAttachmentFileManager: EncryptedAttachmentFileManager,
    private val sharedSecretCrypto: ConversationSharedSecretCrypto,
    private val realtimeWebSocketManager: RealtimeWebSocketManager,
    private val conversationsRefreshBus: ConversationsRefreshBus,
) : ViewModel() {

    private val conversationId: Int = checkNotNull(
        stateHandle.get<Int>(Routes.ConversationArg)
    )

    private val _state = MutableStateFlow(
        ConversationUiState(conversationId = conversationId)
    )
    val state: StateFlow<ConversationUiState> = _state.asStateFlow()

    private var outboxFallbackJob: Job? = null
    private val syncMutex = Mutex()

    init {
        observeCachedConversation()
        observeMergedMessages()
        observeRealtimeEvents()
        observeAttachmentDownloadEvents()
        loadConversation()
    }

    fun retryLoad() {
        loadConversation()
    }


fun enableSharedSecret(token: String) {
    val uuid = _state.value.conversationUuid
    val currentConversationId = _state.value.conversationId ?: return
    if (uuid.isBlank()) {
        _state.value = _state.value.copy(error = "conversation_uuid ещё не загружен")
        return
    }

    viewModelScope.launch {
        runCatching {
            val localState = sharedSecretCrypto.enable(
                conversationUuid = uuid,
                token = token,
            )

            val details = conversationRepository.updateSharedSecretSettings(
                conversationId = currentConversationId,
                enabled = true,
                fingerprint = localState.fingerprint,
            )

            chatCacheRepository.upsertConversationDetails(details)
            refreshSharedSecretState(details)
            conversationsRefreshBus.requestRefresh()
        }.onSuccess {
            _state.value = _state.value.copy(info = "Доп. шифрование включено для этого чата")
            reloadMessages(markDelivered = false, markRead = false)
        }.onFailure {
            _state.value = _state.value.copy(error = it.message ?: "Не удалось включить доп. шифрование")
        }
    }
}

fun disableSharedSecret() {
    val uuid = _state.value.conversationUuid
    val currentConversationId = _state.value.conversationId ?: return
    if (uuid.isBlank()) return

    viewModelScope.launch {
        runCatching {
            sharedSecretCrypto.disable(uuid)

            val details = conversationRepository.updateSharedSecretSettings(
                conversationId = currentConversationId,
                enabled = false,
                fingerprint = null,
            )

            chatCacheRepository.upsertConversationDetails(details)
            refreshSharedSecretState(details)
            conversationsRefreshBus.requestRefresh()
        }.onSuccess {
            _state.value = _state.value.copy(info = "Доп. шифрование выключено для этого чата")
            reloadMessages(markDelivered = false, markRead = false)
        }.onFailure {
            _state.value = _state.value.copy(error = it.message ?: "Не удалось выключить доп. шифрование")
        }
    }
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
            _state.value = _state.value.copy(
                error = null,
                info = null,
                isUploadingAttachment = attachmentUri != null,
            )

            try {
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

                outboxRepository.enqueuePendingMessage(
                    conversationId = currentConversationId,
                    recipientUserId = peerUserId,
                    plainText = text,
                    attachmentIds = uploadedEncryptedAttachments.map { it.attachmentId },
                    attachmentDescriptors = uploadedEncryptedAttachments.map { it.descriptor },
                )

                _state.value = _state.value.copy(
                    isUploadingAttachment = false,
                )

                outboxDispatcher.drainConversation(currentConversationId)
                runCatching {
                    reloadMessages(
                        markDelivered = false,
                        markRead = false,
                    )
                }

                onSent()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isUploadingAttachment = false,
                    error = e.message ?: "Не удалось подготовить сообщение",
                )
            }
        }
    }

    fun retryFailedMessage(messageId: Int) {
        if (messageId >= 0) return

        viewModelScope.launch {
            outboxRepository.requeueFailedMessage(messageId)
            outboxDispatcher.drainMessage(messageId)
            runCatching {
                reloadMessages(
                    markDelivered = false,
                    markRead = false,
                )
            }
        }
    }

    fun removePendingMessage(messageId: Int) {
        if (messageId >= 0) return

        viewModelScope.launch {
            outboxRepository.deletePendingMessage(messageId)
        }
    }

    fun deleteMessageLocal(messageId: Int) {
        val currentConversationId = _state.value.conversationId ?: return

        if (messageId < 0) {
            removePendingMessage(messageId)
            return
        }

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
        if (messageId < 0) return

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

            outboxFallbackJob?.cancel()
            realtimeWebSocketManager.disconnect()
            sessionRepository.logoutSession()

            _state.value = ConversationUiState()
            onLoggedOut()
        }
    }

    private fun loadConversation() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                error = null,
                info = null,
            )

            try {
                val conversation = conversationRepository.getConversation(conversationId)
                chatCacheRepository.upsertConversationDetails(conversation)
                refreshSharedSecretState(conversation)

                outboxRepository.requeueSendingMessages(conversationId)
                catchUpCursorToLatest()

                realtimeWebSocketManager.connectIfNeeded()
                realtimeWebSocketManager.subscribeConversation(conversationId)

                reloadMessages(
                    markDelivered = true,
                    markRead = true,
                )

                outboxDispatcher.drainConversation(conversationId)
                startOutboxFallback()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message,
                )
            }
        }
    }


private fun refreshSharedSecretState(
    details: com.example.securechatapp.domain.model.ConversationDetails,
) {
    val localState = sharedSecretCrypto.getState(details.conversationUuid)
    _state.value = _state.value.copy(
        conversationId = details.conversationId,
        conversationUuid = details.conversationUuid,
        title = details.title,
        peerUserId = details.peerUserId,
        protectionMode = details.protectionMode,
        sharedSecretEnabled = details.sharedSecretEnabled,
        sharedSecretFingerprint = details.sharedSecretFingerprint,
        peerSharedSecretEnabled = details.peerSharedSecretEnabled,
        localSharedSecretEnabled = localState.enabled,
        localSharedSecretFingerprint = localState.fingerprint,
    )
}

    private fun observeCachedConversation() {
        viewModelScope.launch {
            chatCacheRepository.observeConversationDetails(conversationId).collect { details ->
                if (details != null) {
                    _state.value = _state.value.copy(
                        conversationId = details.conversationId,
                        conversationUuid = details.conversationUuid,
                        title = details.title,
                        peerUserId = details.peerUserId,
                        protectionMode = details.protectionMode,
                        sharedSecretEnabled = details.sharedSecretEnabled,
                        sharedSecretFingerprint = details.sharedSecretFingerprint,
                        peerSharedSecretEnabled = details.peerSharedSecretEnabled,
                        localSharedSecretEnabled = sharedSecretCrypto.getState(details.conversationUuid).enabled,
                        localSharedSecretFingerprint = sharedSecretCrypto.getState(details.conversationUuid).fingerprint,
                    )
                }
            }
        }
    }

    private fun observeMergedMessages() {
        viewModelScope.launch {
            combine(
                chatCacheRepository.observeMessages(conversationId),
                outboxRepository.observePendingMessages(conversationId),
            ) { sentMessages, pendingMessages ->
                mergeMessages(
                    sentMessages = sentMessages,
                    pendingMessages = pendingMessages,
                )
            }.collect { merged ->
                _state.value = _state.value.copy(
                    messages = merged,
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

    private fun startOutboxFallback() {
        outboxFallbackJob?.cancel()
        outboxFallbackJob = viewModelScope.launch {
            while (isActive) {
                outboxDispatcher.drainConversation(conversationId)
                delay(OUTBOX_FALLBACK_INTERVAL_MS)
                syncConversation()
            }
        }
    }

    private suspend fun catchUpCursorToLatest() {
        var cursor = chatCacheRepository.getLastEventId(conversationId)

        while (true) {
            val page = syncRepository.getConversationEvents(
                conversationId = conversationId,
                afterEventId = cursor,
                limit = EVENTS_PAGE_SIZE,
            )

            val last = page.items.lastOrNull() ?: break
            cursor = last.eventId
            chatCacheRepository.setLastEventId(conversationId, last.eventId)

            if (!page.hasMore) break
        }
    }

    private suspend fun syncConversation() {
        val currentConversationId = _state.value.conversationId ?: return

        syncMutex.withLock {
            var cursor = chatCacheRepository.getLastEventId(currentConversationId)
            var requiresReload = false
            var processedAnyEvents = false

            while (true) {
                val page = syncRepository.getConversationEvents(
                    conversationId = currentConversationId,
                    afterEventId = cursor,
                    limit = EVENTS_PAGE_SIZE,
                )

                val events = page.items
                if (events.isEmpty()) break

                processedAnyEvents = true

                events.forEach { event ->
                    cursor = event.eventId
                    chatCacheRepository.setLastEventId(currentConversationId, event.eventId)
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

    private suspend fun processConversationEvent(
        event: ConversationSyncEvent,
    ): Boolean {
        return when (event.eventType) {
            ConversationEventTypes.MESSAGE_DELIVERED -> {
                val messageId = event.targetMessageId ?: event.payload?.intValue("message_id")
                val deliveredAt = event.payload?.stringValue("delivered_at") ?: event.createdAt

                if (messageId != null) {
                    chatCacheRepository.markMessageDelivered(
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
                    chatCacheRepository.markMessageRead(
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

        chatCacheRepository.replaceMessages(
            conversationId = currentConversationId,
            messages = messages,
        )

        _state.value = _state.value.copy(
            isLoading = false,
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

            chatCacheRepository.replaceMessages(
                conversationId = currentConversationId,
                messages = refreshedMessages,
            )

            conversationsRefreshBus.requestRefresh()
        }
    }

    private fun mergeMessages(
        sentMessages: List<ChatMessage>,
        pendingMessages: List<ChatMessage>,
    ): List<ChatMessage> {
        val byKey = linkedMapOf<String, ChatMessage>()

        (sentMessages + pendingMessages)
            .sortedWith(
                compareBy<ChatMessage>(
                    { parseMillis(it.createdAt) },
                    { if (it.sendStatus == MessageSendStatus.SENT) 0 else 1 },
                    { it.messageId },
                )
            )
            .forEach { message ->
                val key = message.messageUuid?.let { "uuid:$it" } ?: "id:${message.messageId}"
                val existing = byKey[key]

                if (existing == null) {
                    byKey[key] = message
                } else {
                    val preferred = when {
                        existing.sendStatus != MessageSendStatus.SENT &&
                                message.sendStatus == MessageSendStatus.SENT -> message
                        else -> existing
                    }
                    byKey[key] = preferred
                }
            }

        return byKey.values.sortedWith(
            compareBy<ChatMessage>(
                { parseMillis(it.createdAt) },
                { it.messageId },
            )
        )
    }

    private fun parseMillis(raw: String): Long {
        return runCatching {
            OffsetDateTime.parse(raw).toInstant().toEpochMilli()
        }.getOrDefault(Long.MAX_VALUE)
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
        outboxFallbackJob?.cancel()
        realtimeWebSocketManager.unsubscribeConversation(conversationId)
        super.onCleared()
    }

    private companion object {
        const val EVENTS_PAGE_SIZE = 200
        const val OUTBOX_FALLBACK_INTERVAL_MS = 6_000L
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
