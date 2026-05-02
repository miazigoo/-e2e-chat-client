package com.example.securechatapp.ui.screens.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.securechatapp.domain.model.AttachmentItem
import com.example.securechatapp.domain.model.ChatMessage
import com.example.securechatapp.ui.components.BrandedSkeletonBlock
import com.example.securechatapp.ui.viewmodel.ConversationMediaAttachmentEntry
import com.example.securechatapp.ui.viewmodel.ConversationMediaTab
import com.example.securechatapp.ui.viewmodel.ConversationMediaViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConversationMediaScreen(
    viewModel: ConversationMediaViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var showTagManager by remember { mutableStateOf(false) }
    var editingAttachment by remember { mutableStateOf<AttachmentItem?>(null) }

    LaunchedEffect(listState, state.isLoading, state.isLoadingMore, state.hasMore) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible to listState.layoutInfo.totalItemsCount
        }.collect { (lastVisible, totalCount) ->
            if (totalCount > 0 && lastVisible >= totalCount - 4) {
                viewModel.loadMore()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        MediaScreenTopBar(
            title = if (state.title.isBlank()) "Медиа" else state.title,
            onBack = onBack,
            onManageTags = { showTagManager = true },
        )

        state.error?.let { error ->
            Banner(
                text = error,
                isError = true,
                onDismiss = { viewModel.dismissError(error) },
            )
        }

        ScrollableTabRow(
            selectedTabIndex = state.selectedTab.ordinal,
        ) {
            ConversationMediaTab.entries.forEach { tab ->
                Tab(
                    selected = state.selectedTab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    text = {
                        Text(
                            when (tab) {
                                ConversationMediaTab.MEDIA -> "Media ${state.counts.media}"
                                ConversationMediaTab.FILES -> "Files ${state.counts.files}"
                                ConversationMediaTab.LINKS -> "Links ${state.counts.links}"
                            }
                        )
                    },
                )
            }
        }

        if (state.selectedTab != ConversationMediaTab.LINKS) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Теги",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(onClick = { showTagManager = true }) {
                    Text("Управление")
                }
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.FilterChip(
                    selected = state.selectedTagId == null,
                    onClick = { viewModel.selectTag(null) },
                    label = { Text("Все") },
                )
                state.tags.forEach { tag ->
                    MediaTagChip(
                        tag = tag,
                        selected = state.selectedTagId == tag.tagId,
                        onClick = { viewModel.selectTag(tag.tagId) },
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.isLoading) {
                items(4) {
                    BrandedSkeletonBlock(
                        modifier = Modifier.fillMaxWidth(),
                        height = 84.dp,
                        cornerRadius = 18.dp,
                    )
                }
            }

            if (!state.isLoading && state.selectedTab == ConversationMediaTab.LINKS && state.linkMessages.isEmpty()) {
                item {
                    EmptyConversationHint("В этой вкладке пока пусто")
                }
            }

            if (!state.isLoading && state.selectedTab != ConversationMediaTab.LINKS && state.attachmentItems.isEmpty()) {
                item {
                    EmptyConversationHint("По текущему фильтру вложений нет")
                }
            }

            if (state.selectedTab == ConversationMediaTab.LINKS) {
                items(
                    items = state.linkMessages,
                    key = { "link_${it.messageId}" },
                ) { message ->
                    SharedLinkCard(message = message)
                }
            } else {
                items(
                    items = state.attachmentItems,
                    key = { "attachment_${it.messageId}_${it.attachment.attachmentId}" },
                ) { entry ->
                    MediaAttachmentCard(
                        entry = entry,
                        onEditTags = { editingAttachment = entry.attachment },
                    )
                }
            }
        }
    }

    editingAttachment?.let { attachment ->
        AttachmentTagPickerDialog(
            title = "Теги вложения",
            tags = state.tags,
            isLoading = state.isLoading && state.tags.isEmpty(),
            selectedTagIds = attachment.mediaTags.map { it.tagId }.toSet(),
            onDismiss = { editingAttachment = null },
            onApply = { selected ->
                viewModel.setAttachmentTags(attachment.attachmentId, selected)
                editingAttachment = null
            },
            onOpenManageTags = {
                editingAttachment = null
                showTagManager = true
            },
        )
    }

    if (showTagManager) {
        ConversationMediaTagManagerDialog(
            tags = state.tags,
            onDismiss = { showTagManager = false },
            onCreateTag = viewModel::createTag,
            onUpdateTag = viewModel::updateTag,
            onDeleteTag = viewModel::deleteTag,
        )
    }
}

@Composable
private fun MediaScreenTopBar(
    title: String,
    onBack: () -> Unit,
    onManageTags: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable(onClick = onBack),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("←")
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = "Медиа",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            TextButton(onClick = onManageTags) {
                Text("Теги")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaAttachmentCard(
    entry: ConversationMediaAttachmentEntry,
    onEditTags: () -> Unit,
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AttachmentFileIcon(
                    fileName = entry.attachment.fileName,
                    mimeType = entry.attachment.mimeType,
                    contentDescription = entry.attachment.fileName,
                    modifier = Modifier.padding(end = 10.dp),
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.attachment.fileName,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildAttachmentMetaLine(entry.attachment),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                TextButton(onClick = onEditTags) {
                    Text("Теги")
                }
            }

            if (entry.attachment.mediaTags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    entry.attachment.mediaTags.forEach { tag ->
                        MediaTagChip(
                            tag = tag,
                            selected = true,
                            onClick = onEditTags,
                        )
                    }
                }
            }

            val snippet = entry.messageText
                .replace("[attachment]", "")
                .trim()
                .takeIf { it.isNotBlank() }
            if (snippet != null) {
                Text(
                    text = snippet,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SharedLinkCard(
    message: ChatMessage,
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = message.text.ifBlank { "Ссылка" },
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = message.createdAt,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun buildAttachmentMetaLine(
    attachment: AttachmentItem,
): String {
    val parts = buildList {
        attachment.mimeType?.takeIf { it.isNotBlank() }?.let(::add)
        add(
            when {
                attachment.fileSize >= 1024 * 1024 -> String.format("%.1f MB", attachment.fileSize / 1024f / 1024f)
                attachment.fileSize >= 1024 -> String.format("%.1f KB", attachment.fileSize / 1024f)
                else -> "${attachment.fileSize} B"
            }
        )
    }
    return parts.joinToString(" • ")
}
