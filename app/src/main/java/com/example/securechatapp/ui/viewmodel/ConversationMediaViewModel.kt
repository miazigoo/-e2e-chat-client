package com.example.securechatapp.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechatapp.data.repository.AttachmentRepository
import com.example.securechatapp.data.repository.ConversationRepository
import com.example.securechatapp.data.repository.MediaTagRepository
import com.example.securechatapp.data.repository.MessageRepository
import com.example.securechatapp.domain.model.AttachmentItem
import com.example.securechatapp.domain.model.ChatMessage
import com.example.securechatapp.domain.model.MediaTag
import com.example.securechatapp.domain.model.SharedTabCounts
import com.example.securechatapp.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

enum class ConversationMediaTab(
    val apiValue: String,
) {
    MEDIA("media"),
    FILES("files"),
    LINKS("links"),
}

data class ConversationMediaAttachmentEntry(
    val messageId: Int,
    val createdAt: String,
    val messageText: String,
    val attachment: AttachmentItem,
)

data class ConversationMediaUiState(
    val conversationId: Int? = null,
    val title: String = "",
    val peerUserId: Int? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val selectedTab: ConversationMediaTab = ConversationMediaTab.MEDIA,
    val selectedTagId: Int? = null,
    val tags: List<MediaTag> = emptyList(),
    val counts: SharedTabCounts = SharedTabCounts(),
    val attachmentItems: List<ConversationMediaAttachmentEntry> = emptyList(),
    val linkMessages: List<ChatMessage> = emptyList(),
    val hasMore: Boolean = false,
)

@HiltViewModel
class ConversationMediaViewModel @Inject constructor(
    stateHandle: SavedStateHandle,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val attachmentRepository: AttachmentRepository,
    private val mediaTagRepository: MediaTagRepository,
) : ViewModel() {

    private val conversationId: Int = checkNotNull(stateHandle.get<Int>(Routes.ConversationArg))

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(
        ConversationMediaUiState(conversationId = conversationId)
    )
    val state: kotlinx.coroutines.flow.StateFlow<ConversationMediaUiState> = _state

    private var loadedMessages: List<ChatMessage> = emptyList()
    private var attachmentsByMessageId: Map<Int, List<AttachmentItem>> = emptyMap()
    private val pageSize = 50

    init {
        loadInitial()
    }

    fun retry() {
        loadInitial()
    }

    fun selectTab(tab: ConversationMediaTab) {
        if (_state.value.selectedTab == tab) return
        _state.value = _state.value.copy(
            selectedTab = tab,
            selectedTagId = if (tab == ConversationMediaTab.LINKS) null else _state.value.selectedTagId,
        )
        reloadShared()
    }

    fun selectTag(tagId: Int?) {
        if (_state.value.selectedTagId == tagId) return
        _state.value = _state.value.copy(selectedTagId = tagId)
        reloadShared()
    }

    fun loadMore() {
        if (_state.value.isLoading || _state.value.isLoadingMore || !_state.value.hasMore) return
        loadSharedPage(reset = false)
    }

    fun createTag(name: String, color: String?) {
        viewModelScope.launch {
            runCatching {
                mediaTagRepository.createTag(conversationId, name, color)
            }.onSuccess { created ->
                _state.value = _state.value.copy(
                    tags = (_state.value.tags + created).sortedBy { it.name.lowercase() },
                )
            }.onFailure {
                _state.value = _state.value.copy(error = it.message ?: "Не удалось создать тег")
            }
        }
    }

    fun updateTag(tagId: Int, name: String, color: String?) {
        viewModelScope.launch {
            runCatching {
                mediaTagRepository.updateTag(conversationId, tagId, name, color)
            }.onSuccess { updated ->
                _state.value = _state.value.copy(
                    tags = _state.value.tags.map { if (it.tagId == updated.tagId) updated else it }
                        .sortedBy { it.name.lowercase() },
                )
                rebuildScreenItems()
            }.onFailure {
                _state.value = _state.value.copy(error = it.message ?: "Не удалось обновить тег")
            }
        }
    }

    fun deleteTag(tagId: Int) {
        viewModelScope.launch {
            runCatching {
                mediaTagRepository.deleteTag(conversationId, tagId)
            }.onSuccess {
                val resetFilter = _state.value.selectedTagId == tagId
                _state.value = _state.value.copy(
                    tags = _state.value.tags.filterNot { it.tagId == tagId },
                    selectedTagId = if (resetFilter) null else _state.value.selectedTagId,
                )
                attachmentsByMessageId = attachmentsByMessageId.mapValues { (_, attachments) ->
                    attachments.map { attachment ->
                        attachment.copy(mediaTags = attachment.mediaTags.filterNot { it.tagId == tagId })
                    }
                }
                if (resetFilter) {
                    reloadShared()
                } else {
                    rebuildScreenItems()
                }
            }.onFailure {
                _state.value = _state.value.copy(error = it.message ?: "Не удалось удалить тег")
            }
        }
    }

    fun setAttachmentTags(attachmentId: Int, tagIds: List<Int>) {
        viewModelScope.launch {
            runCatching {
                attachmentRepository.setAttachmentTags(attachmentId, tagIds)
            }.onSuccess { updatedTags ->
                attachmentsByMessageId = attachmentsByMessageId.mapValues { (_, attachments) ->
                    attachments.map { attachment ->
                        if (attachment.attachmentId == attachmentId) {
                            attachment.copy(mediaTags = updatedTags)
                        } else {
                            attachment
                        }
                    }
                }
                rebuildScreenItems()
            }.onFailure {
                _state.value = _state.value.copy(error = it.message ?: "Не удалось обновить теги")
            }
        }
    }

    fun dismissError(error: String) {
        if (_state.value.error == error) {
            _state.value = _state.value.copy(error = null)
        }
    }

    private fun loadInitial() {
        viewModelScope.launch {
            val cachedTags = mediaTagRepository.listCachedConversationTags(conversationId)
            _state.value = _state.value.copy(
                isLoading = true,
                error = null,
                tags = if (cachedTags.isNotEmpty()) cachedTags else _state.value.tags,
            )

            runCatching {
                val conversation = conversationRepository.getConversation(conversationId)
                val resolvedTitle = conversation.title.ifBlank {
                    conversationRepository.listConversations()
                        .firstOrNull { it.conversationId == conversationId }
                        ?.title
                        .orEmpty()
                }
                val tags = mediaTagRepository.listConversationTags(conversationId)
                Triple(resolvedTitle, conversation.peerUserId, tags)
            }.onSuccess { (title, peerUserId, tags) ->
                _state.value = _state.value.copy(
                    title = title.ifBlank { "Медиа" },
                    peerUserId = peerUserId,
                    tags = tags,
                )
                loadedMessages = emptyList()
                attachmentsByMessageId = emptyMap()
                loadSharedPage(reset = true)
            }.onFailure {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = if (cachedTags.isEmpty()) {
                        it.message ?: "Не удалось загрузить медиа чата"
                    } else {
                        null
                    },
                )
            }
        }
    }

    private fun reloadShared() {
        loadedMessages = emptyList()
        attachmentsByMessageId = emptyMap()
        loadSharedPage(reset = true)
    }

    private fun loadSharedPage(reset: Boolean) {
        val peerUserId = _state.value.peerUserId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = reset,
                isLoadingMore = !reset,
                error = null,
            )

            runCatching {
                val selectedTab = _state.value.selectedTab
                val page = messageRepository.listSharedMessages(
                    conversationId = conversationId,
                    peerUserId = peerUserId,
                    tab = selectedTab.apiValue,
                    tagId = if (selectedTab == ConversationMediaTab.LINKS) null else _state.value.selectedTagId,
                    beforeMessageId = if (reset) null else loadedMessages.lastOrNull()?.messageId,
                )
                val pageMessages = page.items
                val newMessageIds = pageMessages.map { it.messageId }
                    .filterNot { attachmentsByMessageId.containsKey(it) }
                val newAttachments = if (selectedTab == ConversationMediaTab.LINKS || newMessageIds.isEmpty()) {
                    emptyMap()
                } else {
                    attachmentRepository.listAttachmentsForMessages(newMessageIds)
                }
                Triple(page, pageMessages, newAttachments)
            }.onSuccess { (page, pageMessages, newAttachments) ->
                loadedMessages = if (reset) {
                    pageMessages
                } else {
                    (loadedMessages + pageMessages).distinctBy { it.messageId }
                }
                attachmentsByMessageId = if (reset) {
                    newAttachments
                } else {
                    attachmentsByMessageId + newAttachments
                }

                _state.value = _state.value.copy(
                    counts = page.counts,
                    hasMore = pageMessages.size >= pageSize,
                    isLoading = false,
                    isLoadingMore = false,
                )
                rebuildScreenItems()
            }.onFailure {
                _state.value = _state.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = it.message ?: "Не удалось загрузить медиа",
                )
            }
        }
    }

    private fun rebuildScreenItems() {
        val selectedTab = _state.value.selectedTab
        if (selectedTab == ConversationMediaTab.LINKS) {
            _state.value = _state.value.copy(
                linkMessages = loadedMessages,
                attachmentItems = emptyList(),
            )
            return
        }

        val attachmentEntries = loadedMessages.flatMap { message ->
            val persistedAttachments = attachmentsByMessageId[message.messageId].orEmpty()
            val payloadAttachmentsById = message.attachments.associateBy { it.attachmentId }
            persistedAttachments.mapNotNull { persisted ->
                val merged = payloadAttachmentsById[persisted.attachmentId]?.let { payloadAttachment ->
                    payloadAttachment.copy(
                        canDownload = persisted.canDownload,
                        mediaTags = persisted.mediaTags,
                    )
                } ?: persisted

                if (!matchesTab(merged, selectedTab)) {
                    null
                } else {
                    ConversationMediaAttachmentEntry(
                        messageId = message.messageId,
                        createdAt = message.createdAt,
                        messageText = message.text,
                        attachment = merged,
                    )
                }
            }
        }

        _state.value = _state.value.copy(
            linkMessages = emptyList(),
            attachmentItems = attachmentEntries,
        )
    }

    private fun matchesTab(
        attachment: AttachmentItem,
        tab: ConversationMediaTab,
    ): Boolean {
        return when (tab) {
            ConversationMediaTab.MEDIA -> {
                attachment.mimeType?.startsWith("image/") == true ||
                    attachment.mimeType?.startsWith("video/") == true
            }
            ConversationMediaTab.FILES -> {
                attachment.mimeType?.startsWith("image/") != true &&
                    attachment.mimeType?.startsWith("video/") != true
            }
            ConversationMediaTab.LINKS -> false
        }
    }
}
