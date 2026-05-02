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
import com.example.securechatapp.data.repository.MediaTagRepository
import com.example.securechatapp.data.repository.MessageRepository
import com.example.securechatapp.data.repository.OutboxDispatcher
import com.example.securechatapp.data.repository.OutboxRepository
import com.example.securechatapp.data.repository.SessionRepository
import com.example.securechatapp.data.repository.SyncRepository
import com.example.securechatapp.domain.model.AttachmentItem
import com.example.securechatapp.domain.model.ChatMessage
import com.example.securechatapp.domain.model.ConversationEventTypes
import com.example.securechatapp.domain.model.ConversationListItem
import com.example.securechatapp.domain.model.ConversationSyncEvent
import com.example.securechatapp.domain.model.MediaTag
import com.example.securechatapp.domain.model.MessagePreview
import com.example.securechatapp.domain.model.MessageSendStatus
import com.example.securechatapp.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.OffsetDateTime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
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
    val isSubmittingMessage: Boolean = false,
    val isLoadingOlderMessages: Boolean = false,
    val isLoadingNewerMessages: Boolean = false,
    val deletingMessageIds: Set<Int> = emptySet(),
    val error: String? = null,
    val info: String? = null,
    val title: String = "",
    val isSavedMessages: Boolean = false,
    val peerUserId: Int? = null,
    val conversationUuid: String = "",
    val protectionMode: String = "normal",
    val messageTtlDays: Int? = null,
    val deleteAfterReadSeconds: Int? = null,
    val isConversationActive: Boolean = true,
    val isConversationPurged: Boolean = false,
    val sharedSecretEnabled: Boolean = false,
    val sharedSecretFingerprint: String? = null,
    val localSharedSecretEnabled: Boolean = false,
    val localSharedSecretFingerprint: String? = null,
    val peerSharedSecretEnabled: Boolean = false,
    val conversationMediaTags: List<MediaTag> = emptyList(),
    val isLoadingMediaTags: Boolean = false,
    val pinnedMessage: MessagePreview? = null,
    val replyingTo: MessagePreview? = null,
    val messages: List<ChatMessage> = emptyList(),
    val forwardingMessageId: Int? = null,
    val forwardTargets: List<ConversationListItem> = emptyList(),
    val isLoadingForwardTargets: Boolean = false,
    val isForwardingMessage: Boolean = false,
    val attachmentSheetMessageId: Int? = null,
    val attachmentLocalStates: Map<Int, AttachmentLocalState> = emptyMap(),
    val selectedMessageAttachments: List<AttachmentItem> = emptyList(),
    val isLoadingAttachments: Boolean = false,
    val downloadingAttachmentId: Int? = null,
    val inlineAttachmentPreviews: Map<Int, InlineAttachmentPreviewUi> = emptyMap(),
    val imagePreviewAttachmentId: Int? = null,
    val imagePreviewUrl: String? = null,
    val imagePreviewBytes: ByteArray? = null,
    val imagePreviewFileName: String? = null,
    val imagePreviewAttachment: AttachmentItem? = null,
    val isLoadingImagePreview: Boolean = false,
    val anchoredMessageId: Int? = null,
    val scrollToMessageId: Int? = null,
)

data class InlineAttachmentPreviewUi(
    val imageUrl: String? = null,
    val imageBytes: ByteArray? = null,
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
)

private const val INLINE_PREVIEW_MAX_ATTEMPTS = 4
private const val INLINE_PREVIEW_RETRY_DELAY_MS = 650L

private fun resolveConversationTitle(
    rawTitle: String,
    isSavedMessages: Boolean,
    peerNickname: String?,
    peerUserId: Int,
): String {
    return when {
        isSavedMessages -> rawTitle.ifBlank { "Избранное" }
        !rawTitle.isBlank() && !rawTitle.startsWith("Пользователь #") -> rawTitle
        !peerNickname.isNullOrBlank() -> peerNickname
        !rawTitle.isBlank() -> rawTitle
        else -> "Пользователь #$peerUserId"
    }
}

private enum class MessageViewportMode {
    LATEST,
    ANCHORED,
}

@HiltViewModel
class ConversationViewModel @Inject constructor(
    stateHandle: SavedStateHandle,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val attachmentRepository: AttachmentRepository,
    private val mediaTagRepository: MediaTagRepository,
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
    private var messageViewportMode = MessageViewportMode.LATEST
    private var beforeBoundaryId: Int? = null
    private var beforeCursor: String? = null
    private var afterBoundaryId: Int? = null
    private var afterCursor: String? = null
    private val inlinePreviewJobs = mutableMapOf<Int, Job>()

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

    fun ensureConversationMediaTagsLoaded() {
        val current = _state.value
        if (current.isLoadingMediaTags) return
        if (current.conversationMediaTags.isNotEmpty()) return
        refreshConversationMediaTags()
    }

    fun refreshConversationMediaTags() {
        val currentConversationId = _state.value.conversationId ?: return
        viewModelScope.launch {
            val cachedTags = mediaTagRepository.listCachedConversationTags(currentConversationId)
            _state.value = _state.value.copy(
                conversationMediaTags = if (cachedTags.isNotEmpty()) cachedTags else _state.value.conversationMediaTags,
                isLoadingMediaTags = true,
                error = null,
            )

            runCatching {
                mediaTagRepository.listConversationTags(currentConversationId)
            }.onSuccess { tags ->
                _state.value = _state.value.copy(
                    conversationMediaTags = tags,
                    isLoadingMediaTags = false,
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    isLoadingMediaTags = false,
                    error = if (cachedTags.isEmpty()) {
                        it.message ?: "Не удалось загрузить теги медиа"
                    } else {
                        null
                    },
                )
            }
        }
    }

    fun createConversationMediaTag(
        name: String,
        color: String?,
    ) {
        val currentConversationId = _state.value.conversationId ?: return
        viewModelScope.launch {
            runCatching {
                mediaTagRepository.createTag(
                    conversationId = currentConversationId,
                    name = name,
                    color = color,
                )
            }.onSuccess { created ->
                _state.value = _state.value.copy(
                    conversationMediaTags = (_state.value.conversationMediaTags + created)
                        .sortedBy { it.name.lowercase() },
                    info = "Тег создан",
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    error = it.message ?: "Не удалось создать тег",
                )
            }
        }
    }

    fun updateConversationMediaTag(
        tagId: Int,
        name: String,
        color: String?,
    ) {
        val currentConversationId = _state.value.conversationId ?: return
        viewModelScope.launch {
            runCatching {
                mediaTagRepository.updateTag(
                    conversationId = currentConversationId,
                    tagId = tagId,
                    name = name,
                    color = color,
                )
            }.onSuccess { updated ->
                _state.value = _state.value.copy(
                    conversationMediaTags = _state.value.conversationMediaTags
                        .map { tag -> if (tag.tagId == updated.tagId) updated else tag }
                        .sortedBy { it.name.lowercase() },
                    info = "Тег обновлён",
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    error = it.message ?: "Не удалось обновить тег",
                )
            }
        }
    }

    fun deleteConversationMediaTag(
        tagId: Int,
    ) {
        val currentConversationId = _state.value.conversationId ?: return
        viewModelScope.launch {
            runCatching {
                mediaTagRepository.deleteTag(
                    conversationId = currentConversationId,
                    tagId = tagId,
                )
            }.onSuccess {
                _state.value = _state.value.copy(
                    conversationMediaTags = _state.value.conversationMediaTags
                        .filterNot { it.tagId == tagId },
                    selectedMessageAttachments = _state.value.selectedMessageAttachments.map { attachment ->
                        if (attachment.mediaTags.none { tag -> tag.tagId == tagId }) {
                            attachment
                        } else {
                            attachment.copy(
                                mediaTags = attachment.mediaTags.filterNot { tag -> tag.tagId == tagId }
                            )
                        }
                    },
                    info = "Тег удалён",
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    error = it.message ?: "Не удалось удалить тег",
                )
            }
        }
    }

    fun setAttachmentTags(
        attachmentId: Int,
        tagIds: List<Int>,
    ) {
        viewModelScope.launch {
            runCatching {
                attachmentRepository.setAttachmentTags(
                    attachmentId = attachmentId,
                    tagIds = tagIds,
                )
            }.onSuccess { updatedTags ->
                fun updateAttachment(attachment: AttachmentItem): AttachmentItem {
                    return if (attachment.attachmentId == attachmentId) {
                        attachment.copy(mediaTags = updatedTags)
                    } else {
                        attachment
                    }
                }

                _state.value = _state.value.copy(
                    selectedMessageAttachments = _state.value.selectedMessageAttachments.map(::updateAttachment),
                    messages = _state.value.messages.map { message ->
                        if (message.attachments.any { it.attachmentId == attachmentId }) {
                            message.copy(
                                attachments = message.attachments.map(::updateAttachment),
                            )
                        } else {
                            message
                        }
                    },
                    info = "Теги обновлены",
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    error = it.message ?: "Не удалось обновить теги вложения",
                )
            }
        }
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
        attachmentTagIds: List<Int> = emptyList(),
        attachmentDrafts: List<AttachmentItem> = emptyList(),
        attachmentUris: List<Uri> = emptyList(),
        onQueued: () -> Unit = {},
    ) {
        val currentConversationId = _state.value.conversationId ?: return
        val peerUserId = _state.value.peerUserId ?: return
        val replyingTo = _state.value.replyingTo
        if (_state.value.isSubmittingMessage) return
        if (text.isBlank() && attachmentUris.isEmpty()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                error = null,
                info = null,
                isSubmittingMessage = true,
            )

            try {
                val localMessageId = outboxRepository.enqueuePendingMessage(
                    conversationId = currentConversationId,
                    recipientUserId = peerUserId,
                    plainText = text,
                    replyToMessageId = replyingTo?.messageId,
                    replyPreview = replyingTo,
                    localAttachmentUris = attachmentUris.map(Uri::toString),
                    attachmentTagIds = attachmentTagIds,
                    attachmentPreviews = attachmentDrafts,
                    attachmentIds = emptyList(),
                    attachmentDescriptors = emptyList(),
                )

                _state.value = _state.value.copy(
                    isSubmittingMessage = false,
                    replyingTo = null,
                )
                onQueued()

                outboxDispatcher.drainMessage(localMessageId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSubmittingMessage = false,
                    error = e.message ?: "Не удалось подготовить сообщение",
                )
            }
        }
    }

    fun startReply(message: ChatMessage) {
        if (message.messageId <= 0) return

        _state.value = _state.value.copy(
            replyingTo = message.toPreview(),
            error = null,
            info = null,
        )
    }

    fun cancelReply() {
        _state.value = _state.value.copy(replyingTo = null)
    }

    fun openForwardPicker(message: ChatMessage) {
        if (message.messageId <= 0) return

        viewModelScope.launch {
            val cachedTargets = buildForwardTargets(
                chatCacheRepository.getCachedConversations()
            )
            _state.value = _state.value.copy(
                forwardingMessageId = message.messageId,
                forwardTargets = cachedTargets,
                isLoadingForwardTargets = true,
                isForwardingMessage = false,
                error = null,
                info = null,
            )

            runCatching {
                conversationRepository.listConversations()
                    .also { chatCacheRepository.replaceConversations(it) }
            }.onSuccess { conversations ->
                _state.value = _state.value.copy(
                    forwardTargets = buildForwardTargets(conversations),
                    isLoadingForwardTargets = false,
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    forwardTargets = cachedTargets,
                    isLoadingForwardTargets = false,
                    error = if (cachedTargets.isEmpty()) {
                        it.message ?: "Не удалось загрузить список чатов"
                    } else {
                        null
                    },
                    info = if (cachedTargets.isNotEmpty()) {
                        "Не удалось обновить список чатов. Показаны локальные данные."
                    } else {
                        null
                    },
                )
            }
        }
    }

    fun dismissForwardPicker() {
        _state.value = _state.value.copy(
            forwardingMessageId = null,
            forwardTargets = emptyList(),
            isLoadingForwardTargets = false,
            isForwardingMessage = false,
        )
    }

    fun forwardSelectedMessage(targetConversationId: Int) {
        val sourceMessageId = _state.value.forwardingMessageId ?: return
        val targetConversation = _state.value.forwardTargets.firstOrNull {
            it.conversationId == targetConversationId
        } ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                isForwardingMessage = true,
                error = null,
                info = null,
            )

            runCatching {
                messageRepository.forwardMessages(
                    conversationId = targetConversation.conversationId,
                    recipientUserId = targetConversation.peerUserId,
                    messageIds = listOf(sourceMessageId),
                )
            }.onSuccess {
                dismissForwardPicker()
                conversationsRefreshBus.requestRefresh()
                if (targetConversation.conversationId == conversationId) {
                    reloadMessages(
                        markDelivered = false,
                        markRead = false,
                    )
                }
                _state.value = _state.value.copy(info = "Сообщение переслано")
            }.onFailure {
                _state.value = _state.value.copy(
                    isForwardingMessage = false,
                    error = it.message ?: "Не удалось переслать сообщение",
                )
            }
        }
    }

    fun retryFailedMessage(messageId: Int) {
        if (messageId >= 0) return

        viewModelScope.launch {
            outboxRepository.requeueFailedMessage(messageId)
            outboxDispatcher.drainMessage(messageId)
        }
    }

    fun removePendingMessage(messageId: Int) {
        if (messageId >= 0) return

        viewModelScope.launch {
            outboxRepository.deletePendingMessage(messageId)
        }
    }



    fun setMessageReaction(messageId: Int, reaction: String) {
        if (messageId <= 0) return

        viewModelScope.launch {
            runCatching {
                messageRepository.setMessageReaction(
                    messageId = messageId,
                    reaction = reaction,
                )
                reloadMessages(
                    markDelivered = false,
                    markRead = false,
                )
            }.onFailure {
                _state.value = _state.value.copy(error = it.message ?: "Не удалось поставить реакцию")
            }
        }
    }

    fun removeMessageReaction(messageId: Int) {
        if (messageId <= 0) return

        viewModelScope.launch {
            runCatching {
                messageRepository.deleteMessageReaction(messageId = messageId)
                reloadMessages(
                    markDelivered = false,
                    markRead = false,
                )
            }.onFailure {
                _state.value = _state.value.copy(error = it.message ?: "Не удалось убрать реакцию")
            }
        }
    }

    fun pinMessage(messageId: Int) {
        val currentConversationId = _state.value.conversationId ?: return
        if (messageId <= 0) return

        viewModelScope.launch {
            runCatching {
                val details = conversationRepository.pinMessage(
                    conversationId = currentConversationId,
                    messageId = messageId,
                )
                chatCacheRepository.upsertConversationDetails(details)
                refreshSharedSecretState(details)
                conversationsRefreshBus.requestRefresh()
                _state.value = _state.value.copy(info = "Сообщение закреплено")
            }.onFailure {
                _state.value = _state.value.copy(error = it.message ?: "Не удалось закрепить сообщение")
            }
        }
    }

    fun unpinMessage() {
        val currentConversationId = _state.value.conversationId ?: return

        viewModelScope.launch {
            runCatching {
                val details = conversationRepository.unpinMessage(currentConversationId)
                chatCacheRepository.upsertConversationDetails(details)
                refreshSharedSecretState(details)
                conversationsRefreshBus.requestRefresh()
                _state.value = _state.value.copy(info = "Закреп снят")
            }.onFailure {
                _state.value = _state.value.copy(error = it.message ?: "Не удалось снять закреп")
            }
        }
    }

    fun openPinnedMessageWindow() {
        val currentConversationId = _state.value.conversationId ?: return
        val peerUserId = _state.value.peerUserId ?: return
        val pinnedMessageId = _state.value.pinnedMessage?.messageId ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                error = null,
                info = null,
            )

            runCatching {
                val page = messageRepository.listMessageWindow(
                    conversationId = currentConversationId,
                    peerUserId = peerUserId,
                    anchorId = pinnedMessageId,
                )
                messageViewportMode = MessageViewportMode.ANCHORED
                val anchorMessageId = page.anchorMessageId ?: pinnedMessageId
                applyWindowMetadata(page, anchorMessageId = anchorMessageId, requestScroll = true)
                chatCacheRepository.replaceMessages(
                    conversationId = currentConversationId,
                    messages = page.messages,
                )
                markMessagesAroundViewport(page.messages)
            }.onFailure {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = it.message ?: "Не удалось открыть закреплённое сообщение",
                )
            }
        }
    }

    fun loadOlderMessages() {
        if (messageViewportMode != MessageViewportMode.ANCHORED) return
        if (_state.value.isLoadingOlderMessages) return
        val currentConversationId = _state.value.conversationId ?: return
        val peerUserId = _state.value.peerUserId ?: return
        val requestBeforeId = beforeBoundaryId ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingOlderMessages = true)

            runCatching {
                val page = messageRepository.listMessageWindow(
                    conversationId = currentConversationId,
                    peerUserId = peerUserId,
                    beforeId = requestBeforeId,
                    beforeCursor = beforeCursor,
                )
                val merged = mergeMessagesForViewport(
                    existing = _state.value.messages,
                    incoming = page.messages,
                )
                applyWindowMetadata(
                    page = page,
                    anchorMessageId = _state.value.anchoredMessageId,
                    requestScroll = false,
                )
                chatCacheRepository.replaceMessages(
                    conversationId = currentConversationId,
                    messages = merged,
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    error = it.message ?: "Не удалось загрузить предыдущие сообщения",
                )
            }

            _state.value = _state.value.copy(isLoadingOlderMessages = false)
        }
    }

    fun loadNewerMessages() {
        if (messageViewportMode != MessageViewportMode.ANCHORED) return
        if (_state.value.isLoadingNewerMessages) return
        val currentConversationId = _state.value.conversationId ?: return
        val peerUserId = _state.value.peerUserId ?: return
        val requestAfterId = afterBoundaryId ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingNewerMessages = true)

            runCatching {
                val page = messageRepository.listMessageWindow(
                    conversationId = currentConversationId,
                    peerUserId = peerUserId,
                    afterId = requestAfterId,
                    afterCursor = afterCursor,
                )
                val merged = mergeMessagesForViewport(
                    existing = _state.value.messages,
                    incoming = page.messages,
                )
                applyWindowMetadata(
                    page = page,
                    anchorMessageId = _state.value.anchoredMessageId,
                    requestScroll = false,
                )
                chatCacheRepository.replaceMessages(
                    conversationId = currentConversationId,
                    messages = merged,
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    error = it.message ?: "Не удалось загрузить новые сообщения",
                )
            }

            _state.value = _state.value.copy(isLoadingNewerMessages = false)
        }
    }

    fun onScrollToMessageHandled() {
        _state.value = _state.value.copy(scrollToMessageId = null)
    }

    fun dismissError(expected: String? = null) {
        val current = _state.value.error ?: return
        if (expected != null && current != expected) return
        _state.value = _state.value.copy(error = null)
    }

    fun dismissInfo(expected: String? = null) {
        val current = _state.value.info ?: return
        if (expected != null && current != expected) return
        _state.value = _state.value.copy(info = null)
    }

    fun ensureInlineImagePreview(
        attachment: AttachmentItem,
    ) {
        if (!attachment.isImage || !attachment.canDownload) return

        val attachmentId = attachment.attachmentId
        val currentPreview = _state.value.inlineAttachmentPreviews[attachmentId]
        if (
            currentPreview?.isLoading == true ||
            currentPreview?.imageBytes != null ||
            !currentPreview?.imageUrl.isNullOrBlank() ||
            inlinePreviewJobs.containsKey(attachmentId)
        ) {
            return
        }

        _state.value = _state.value.copy(
            inlineAttachmentPreviews = _state.value.inlineAttachmentPreviews + (
                    attachmentId to InlineAttachmentPreviewUi(isLoading = true)
                    )
        )

        inlinePreviewJobs[attachmentId] = viewModelScope.launch {
            runCatching {
                loadInlineAttachmentPreviewWithRetry(attachment)
            }.onSuccess { preview ->
                _state.value = _state.value.copy(
                    inlineAttachmentPreviews = _state.value.inlineAttachmentPreviews + (
                            attachmentId to preview
                            )
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    inlineAttachmentPreviews = _state.value.inlineAttachmentPreviews + (
                            attachmentId to InlineAttachmentPreviewUi(hasError = true)
                            )
                )
            }

            inlinePreviewJobs.remove(attachmentId)
        }
    }

    fun previewInlineImageAttachment(
        attachment: AttachmentItem,
    ) {
        if (!attachment.isImage) return
        previewImageAttachment(attachment)
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
        ensureConversationMediaTagsLoaded()
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
                refreshConversationMediaTags()

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
    val resolvedTitle = resolveConversationTitle(
        rawTitle = details.title,
        isSavedMessages = details.isSavedMessages,
        peerNickname = details.peerNickname,
        peerUserId = details.peerUserId,
    )
    _state.value = _state.value.copy(
        conversationId = details.conversationId,
        conversationUuid = details.conversationUuid,
        title = resolvedTitle,
        isSavedMessages = details.isSavedMessages,
        peerUserId = details.peerUserId,
        protectionMode = details.protectionMode,
        messageTtlDays = details.messageTtlDays,
        deleteAfterReadSeconds = details.deleteAfterReadSeconds,
        isConversationActive = details.isActive,
        isConversationPurged = details.isPurged,
        sharedSecretEnabled = details.sharedSecretEnabled,
        sharedSecretFingerprint = details.sharedSecretFingerprint,
        peerSharedSecretEnabled = details.peerSharedSecretEnabled,
        pinnedMessage = details.pinnedMessage,
        localSharedSecretEnabled = localState.enabled,
        localSharedSecretFingerprint = localState.fingerprint,
    )
}

    private fun observeCachedConversation() {
        launchGuarded {
            chatCacheRepository.observeConversationDetails(conversationId).collect { details ->
                if (details != null) {
                    val resolvedTitle = resolveConversationTitle(
                        rawTitle = details.title,
                        isSavedMessages = details.isSavedMessages,
                        peerNickname = details.peerNickname,
                        peerUserId = details.peerUserId,
                    )
                    _state.value = _state.value.copy(
                        conversationId = details.conversationId,
                        conversationUuid = details.conversationUuid,
                        title = resolvedTitle,
                        isSavedMessages = details.isSavedMessages,
                        peerUserId = details.peerUserId,
                        protectionMode = details.protectionMode,
                        messageTtlDays = details.messageTtlDays,
                        deleteAfterReadSeconds = details.deleteAfterReadSeconds,
                        isConversationActive = details.isActive,
                        isConversationPurged = details.isPurged,
                        sharedSecretEnabled = details.sharedSecretEnabled,
                        sharedSecretFingerprint = details.sharedSecretFingerprint,
                        peerSharedSecretEnabled = details.peerSharedSecretEnabled,
                        pinnedMessage = details.pinnedMessage,
                        localSharedSecretEnabled = sharedSecretCrypto.getState(details.conversationUuid).enabled,
                        localSharedSecretFingerprint = sharedSecretCrypto.getState(details.conversationUuid).fingerprint,
                    )
                }
            }
        }
    }

    private fun observeMergedMessages() {
        launchGuarded {
            combine(
                chatCacheRepository.observeMessages(conversationId),
                outboxRepository.observePendingMessages(conversationId),
            ) { sentMessages, pendingMessages ->
                mergeMessages(
                    sentMessages = sentMessages,
                    pendingMessages = pendingMessages,
                )
            }.collect { merged ->
                merged.lastOrNull()?.let { message ->
                    chatCacheRepository.updateConversationLastMessagePreview(
                        conversationId = conversationId,
                        lastMessagePreview = buildLastMessagePreview(message),
                        updatedAt = message.createdAt,
                    )
                }
                val pinnedPreview = hydratePreviewFromMessages(
                    preview = _state.value.pinnedMessage,
                    messages = merged,
                )
                _state.value = _state.value.copy(
                    messages = merged,
                    pinnedMessage = pinnedPreview,
                )
            }
        }
    }

    private fun observeRealtimeEvents() {
        launchGuarded {
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
                            info = event.message
                        )
                    }

                    else -> Unit
                }
            }
        }
    }

    private fun observeAttachmentDownloadEvents() {
        launchGuarded {
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
                            error = event.errorMessage ?: "Не удалось скачать файл",
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

    private fun buildForwardTargets(
        conversations: List<ConversationListItem>,
    ): List<ConversationListItem> {
        val currentConversation = currentConversationForwardTarget()
        return buildList {
            addAll(
                conversations.filter { it.isActive && !it.isPurged }
            )
            if (currentConversation != null) {
                add(currentConversation)
            }
        }.distinctBy { it.conversationId }
            .sortedWith(
                compareByDescending<ConversationListItem> { it.isSavedMessages }
                    .thenBy { it.title.lowercase() }
            )
    }

    private fun currentConversationForwardTarget(): ConversationListItem? {
        val currentState = _state.value
        val currentConversationId = currentState.conversationId ?: return null
        val currentPeerUserId = currentState.peerUserId ?: return null
        if (currentState.isConversationPurged || !currentState.isConversationActive) {
            return null
        }

        val title = currentState.title.ifBlank {
            if (currentState.isSavedMessages) "Избранное" else "Диалог"
        }
        val peerNickname = if (currentState.isSavedMessages) {
            "Избранное"
        } else {
            title
        }
        val lastPreview = currentState.messages.lastOrNull()?.let { message ->
            buildLastMessagePreview(message)
        } ?: "Нет сообщений"

        return ConversationListItem(
            conversationId = currentConversationId,
            conversationUuid = currentState.conversationUuid,
            title = title,
            isSavedMessages = currentState.isSavedMessages,
            peerUserId = currentPeerUserId,
            peerNickname = peerNickname,
            unreadCount = 0,
            lastMessagePreview = lastPreview,
            protectionMode = currentState.protectionMode,
            messageTtlDays = currentState.messageTtlDays,
            deleteAfterReadSeconds = currentState.deleteAfterReadSeconds,
            isActive = currentState.isConversationActive,
            isPurged = currentState.isConversationPurged,
            updatedAt = currentState.messages.lastOrNull()?.createdAt,
            sharedSecretEnabled = currentState.sharedSecretEnabled,
            sharedSecretFingerprint = currentState.sharedSecretFingerprint,
            peerSharedSecretEnabled = currentState.peerSharedSecretEnabled,
            pinnedMessage = currentState.pinnedMessage,
        )
    }

    private fun buildLastMessagePreview(
        message: ChatMessage,
    ): String {
        return message.text
            .takeIf { it.isNotBlank() && it != "[attachment]" }
            ?: if (message.hasAttachments) {
                val attachments = message.attachments
                when {
                    attachments.isEmpty() -> "Вложение"
                    attachments.size == 1 -> "📎 ${attachments.first().fileName}"
                    else -> "📎 ${attachments.first().fileName} +${attachments.size - 1}"
                }
            } else {
                "Сообщение"
            }
    }

    private fun startOutboxFallback() {
        outboxFallbackJob?.cancel()
        outboxFallbackJob = launchGuarded {
            while (currentCoroutineContext().isActive) {
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

            ConversationEventTypes.CONVERSATION_SETTINGS_UPDATED -> {
                refreshConversationDetails()

                if (event.actorUserId == _state.value.peerUserId) {
                    val enabled = event.payload?.stringValue("shared_secret_enabled")?.toBooleanStrictOrNull()
                    _state.value = _state.value.copy(
                        info = when (enabled) {
                            true -> "Собеседник включил дополнительное шифрование"
                            false -> "Собеседник выключил дополнительное шифрование"
                            null -> "Настройки чата обновлены"
                        }
                    )
                }
                false
            }

            ConversationEventTypes.MESSAGE_PINNED,
            ConversationEventTypes.MESSAGE_UNPINNED,
            ConversationEventTypes.CONVERSATION_PURGED -> {
                refreshConversationDetails()
                true
            }

            ConversationEventTypes.MESSAGE_CREATED,
            ConversationEventTypes.MESSAGE_FORWARDED,
            ConversationEventTypes.MESSAGE_DELETED_GLOBAL,
            ConversationEventTypes.MESSAGE_HIDDEN_FOR_USER,
            ConversationEventTypes.CONVERSATION_CLEARED_LOCAL,
            ConversationEventTypes.CONVERSATION_CLEARED_GLOBAL,
            ConversationEventTypes.MESSAGE_REACTION_SET,
            ConversationEventTypes.MESSAGE_REACTION_REMOVED,
            ConversationEventTypes.FILE_UPLOADED,
            ConversationEventTypes.FILE_DELETED -> true

            else -> true
        }
    }

    private suspend fun refreshConversationDetails() {
        val currentConversationId = _state.value.conversationId ?: return
        runCatching {
            val details = conversationRepository.getConversation(currentConversationId)
            chatCacheRepository.upsertConversationDetails(details)
            refreshSharedSecretState(details)
        }.onFailure {
            _state.value = _state.value.copy(
                error = it.message ?: "Не удалось обновить данные чата",
            )
        }
    }

    private suspend fun reloadMessages(
        markDelivered: Boolean,
        markRead: Boolean,
    ) {
        val currentConversationId = _state.value.conversationId ?: return
        val peerUserId = _state.value.peerUserId ?: return

        val page = when (messageViewportMode) {
            MessageViewportMode.LATEST -> {
                messageRepository.listMessageWindow(
                    conversationId = currentConversationId,
                    peerUserId = peerUserId,
                )
            }

            MessageViewportMode.ANCHORED -> {
                val anchorMessageId = _state.value.anchoredMessageId
                    ?: _state.value.pinnedMessage?.messageId
                    ?: return
                messageRepository.listMessageWindow(
                    conversationId = currentConversationId,
                    peerUserId = peerUserId,
                    anchorId = anchorMessageId,
                )
            }
        }

        val messages = page.messages
        val anchorMessageId = when (messageViewportMode) {
            MessageViewportMode.LATEST -> null
            MessageViewportMode.ANCHORED -> page.anchorMessageId ?: _state.value.anchoredMessageId
        }
        applyWindowMetadata(
            page = page,
            anchorMessageId = anchorMessageId,
            requestScroll = false,
        )

        chatCacheRepository.replaceMessages(
            conversationId = currentConversationId,
            messages = messages,
        )

        _state.value = _state.value.copy(
            isLoading = false,
            error = null,
        )

        val deliveredUpdates = if (markDelivered) {
            messageRepository.markIncomingMessagesAsDelivered(messages)
        } else {
            emptyList()
        }

        val readUpdates = if (markRead) {
            messageRepository.markIncomingMessagesAsRead(messages)
        } else {
            emptyList()
        }

        deliveredUpdates.forEach { update ->
            chatCacheRepository.markMessageDelivered(
                messageId = update.messageId,
                deliveredAt = update.timestamp,
            )
        }
        readUpdates.forEach { update ->
            chatCacheRepository.markMessageRead(
                messageId = update.messageId,
                readAt = update.timestamp,
            )
        }
        if (readUpdates.isNotEmpty()) {
            chatCacheRepository.updateConversationUnreadCount(
                conversationId = currentConversationId,
                unreadCount = 0,
            )
        }
    }

    private suspend fun markMessagesAroundViewport(
        messages: List<ChatMessage>,
    ) {
        val deliveredUpdates = messageRepository.markIncomingMessagesAsDelivered(messages)
        val readUpdates = messageRepository.markIncomingMessagesAsRead(messages)
        deliveredUpdates.forEach { update ->
            chatCacheRepository.markMessageDelivered(
                messageId = update.messageId,
                deliveredAt = update.timestamp,
            )
        }
        readUpdates.forEach { update ->
            chatCacheRepository.markMessageRead(
                messageId = update.messageId,
                readAt = update.timestamp,
            )
        }
        if (readUpdates.isNotEmpty()) {
            val currentConversationId = _state.value.conversationId ?: return
            chatCacheRepository.updateConversationUnreadCount(
                conversationId = currentConversationId,
                unreadCount = 0,
            )
        }
    }

    private fun applyWindowMetadata(
        page: MessageRepository.MessageWindowPage,
        anchorMessageId: Int?,
        requestScroll: Boolean,
    ) {
        beforeBoundaryId = page.beforeId
        beforeCursor = page.beforeCursor
        afterBoundaryId = page.afterId
        afterCursor = page.afterCursor

        _state.value = _state.value.copy(
            anchoredMessageId = anchorMessageId,
            scrollToMessageId = when {
                requestScroll -> anchorMessageId
                anchorMessageId == null -> null
                else -> _state.value.scrollToMessageId
            },
            isLoading = false,
            error = null,
        )
    }

    private fun mergeMessagesForViewport(
        existing: List<ChatMessage>,
        incoming: List<ChatMessage>,
    ): List<ChatMessage> {
        val merged = linkedMapOf<String, ChatMessage>()
        (existing + incoming)
            .sortedWith(
                compareBy<ChatMessage>(
                    { parseMillis(it.createdAt) },
                    { it.messageId },
                )
            )
            .forEach { message ->
                val key = message.messageUuid?.let { "uuid:$it" } ?: "id:${message.messageId}"
                merged[key] = message
            }
        return merged.values.toList()
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

    private fun hydratePreviewFromMessages(
        preview: MessagePreview?,
        messages: List<ChatMessage>,
    ): MessagePreview? {
        if (preview == null || preview.text.isNotBlank()) {
            return preview
        }

        val source = messages.firstOrNull { it.messageId == preview.messageId } ?: return preview
        return source.toPreview()
    }

    private fun ChatMessage.toPreview(): MessagePreview {
        return MessagePreview(
            messageId = messageId,
            messageUuid = messageUuid.orEmpty(),
            senderUserId = if (isMine) 0 else (_state.value.peerUserId ?: 0),
            messageType = messageType,
            text = text.takeUnless { it == "[attachment]" }.orEmpty(),
            hasAttachments = hasAttachments,
            clientCreatedAt = clientCreatedAt ?: createdAt,
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
            val cachedPreview = _state.value.inlineAttachmentPreviews[attachment.attachmentId]
            if (cachedPreview?.imageBytes != null || !cachedPreview?.imageUrl.isNullOrBlank()) {
                _state.value = _state.value.copy(
                    attachmentSheetMessageId = null,
                    selectedMessageAttachments = emptyList(),
                    isLoadingAttachments = false,
                    downloadingAttachmentId = null,
                    imagePreviewAttachmentId = attachment.attachmentId,
                    imagePreviewUrl = cachedPreview.imageUrl,
                    imagePreviewBytes = cachedPreview.imageBytes,
                    imagePreviewFileName = attachment.fileName,
                    imagePreviewAttachment = attachment,
                    isLoadingImagePreview = false,
                )
                return@launch
            }

            if (!attachment.canDownload) {
                _state.value = _state.value.copy(
                    info = "Изображение ещё подготавливается на сервере"
                )
                return@launch
            }

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

            runCatching {
                loadInlineAttachmentPreviewWithRetry(attachment)
            }.onSuccess { preview ->
                _state.value = _state.value.copy(
                    inlineAttachmentPreviews = _state.value.inlineAttachmentPreviews + (
                            attachment.attachmentId to preview
                            )
                )
                _state.value = _state.value.copy(
                    imagePreviewAttachmentId = attachment.attachmentId,
                    imagePreviewUrl = preview.imageUrl,
                    imagePreviewBytes = preview.imageBytes,
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
        }
    }

    private suspend fun loadInlineAttachmentPreviewWithRetry(
        attachment: AttachmentItem,
    ): InlineAttachmentPreviewUi {
        var lastError: Throwable? = null

        repeat(INLINE_PREVIEW_MAX_ATTEMPTS) { attempt ->
            try {
                return loadInlineAttachmentPreview(attachment)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                lastError = throwable

                val shouldRetry = attempt < INLINE_PREVIEW_MAX_ATTEMPTS - 1 &&
                        currentCoroutineContext().isActive
                if (shouldRetry) {
                    delay(INLINE_PREVIEW_RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }

        throw (lastError ?: IllegalStateException("Не удалось загрузить превью"))
    }

    private suspend fun loadInlineAttachmentPreview(
        attachment: AttachmentItem,
    ): InlineAttachmentPreviewUi {
        val downloadInfo = attachmentRepository.getAttachmentDownloadInfo(attachment.attachmentId)
            ?: error(
                if (attachment.hasEncryptedBlobKeys) {
                    "Вложение больше недоступно на сервере"
                } else {
                    "Изображение больше недоступно на сервере"
                }
            )

        return if (attachment.hasEncryptedBlobKeys) {
            InlineAttachmentPreviewUi(
                imageBytes = encryptedAttachmentFileManager.downloadAndDecryptBytes(
                    downloadUrl = downloadInfo.downloadUrl,
                    blobKeyBase64 = attachment.blobKeyBase64.orEmpty(),
                    blobNonceBase64 = attachment.blobNonceBase64.orEmpty(),
                ),
            )
        } else {
            InlineAttachmentPreviewUi(
                imageUrl = downloadInfo.downloadUrl,
            )
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
                    ?: error("Вложение больше недоступно на сервере")

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
                        error = "Не удалось сохранить расшифрованный файл на устройстве",
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
                        error = "Вложение больше недоступно на сервере",
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

    private fun launchGuarded(
        block: suspend () -> Unit,
    ): Job {
        return viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    error = error.message ?: "Не удалось обновить чат",
                )
            }
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
