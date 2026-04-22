package com.example.securechatapp.ui.screens.chats

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.securechatapp.data.repository.BackendRepository
import com.example.securechatapp.ui.viewmodel.ChatsViewModel

@Composable
fun ChatsScreen(
    onLoggedOut: () -> Unit,
    viewModel: ChatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    var search by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var pendingAttachmentUri by remember { mutableStateOf<Uri?>(null) }
    var pendingAttachmentName by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        pendingAttachmentUri = uri
        pendingAttachmentName = uri?.lastPathSegment ?: "attachment"
    }

    LaunchedEffect(state.activeConversationId, state.messages.size) {
        if (state.activeConversationId != null && state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    if (state.activeConversationId == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Chats", style = MaterialTheme.typography.headlineSmall)

                Button(
                    onClick = { viewModel.logout(onLoggedOut) },
                    enabled = !state.isLoggingOut,
                ) {
                    Text(if (state.isLoggingOut) "..." else "Logout")
                }
            }

            Button(onClick = viewModel::refreshConversations) {
                Text("Refresh")
            }

            OutlinedTextField(
                value = search,
                onValueChange = {
                    search = it
                    if (it.isBlank()) {
                        viewModel.searchUsers("")
                    }
                },
                label = { Text("Search user") },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { viewModel.searchUsers(search) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Search")
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            if (state.isLoading) {
                Text("Loading...")
            }

            if (search.isNotBlank()) {
                Text("Search results", style = MaterialTheme.typography.titleMedium)

                if (!state.isLoading && state.users.isEmpty()) {
                    Text("No users found")
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.users) { user ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.createConversation(user.userId)
                                }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(user.nickname)
                                Text("Tap to start chat")
                            }
                        }
                    }
                }
            }

            Text("Conversations", style = MaterialTheme.typography.titleMedium)

            if (!state.isLoading && state.conversations.isEmpty()) {
                Text("No conversations yet")
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.conversations) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.openConversation(item.conversationId)
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(item.title)
                            Text("Unread: ${item.unreadCount}")
                        }
                    }
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Button(onClick = viewModel::backToConversationList) {
                Text("Back")
            }

            Button(
                onClick = { viewModel.logout(onLoggedOut) },
                enabled = !state.isLoggingOut,
            ) {
                Text(if (state.isLoggingOut) "..." else "Logout")
            }
        }

        Text(
            text = if (state.activeConversationTitle.isBlank()) {
                "Conversation"
            } else {
                state.activeConversationTitle
            },
            style = MaterialTheme.typography.headlineSmall,
        )

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        state.info?.let {
            Text(it)
        }

        if (state.isUploadingAttachment) {
            Text("Uploading attachment...")
        }

        if (state.isLoading && state.messages.isEmpty()) {
            Text("Loading...")
        }

        if (!state.isLoading && state.messages.isEmpty()) {
            Text("No messages yet")
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.messages) { msg ->
                MessageBubble(
                    msg = msg,
                    isDeleting = state.deletingMessageIds.contains(msg.messageId),
                    onDeleteLocal = { viewModel.deleteMessageLocal(msg.messageId) },
                    onDeleteGlobal = { viewModel.deleteMessageGlobal(msg.messageId) },
                )
            }
        }

        pendingAttachmentName?.let { name ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Attachment selected")
                    Text(name)
                    Button(
                        onClick = {
                            pendingAttachmentUri = null
                            pendingAttachmentName = null
                        }
                    ) {
                        Text("Clear attachment")
                    }
                }
            }
        }

        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { attachmentPicker.launch("*/*") },
                modifier = Modifier.weight(1f),
            ) {
                Text("Attach")
            }

            Button(
                onClick = {
                    val textToSend = message
                    val attachmentToSend = pendingAttachmentUri
                    viewModel.sendMessage(
                        text = textToSend,
                        attachmentUri = attachmentToSend,
                    ) {
                        message = ""
                        pendingAttachmentUri = null
                        pendingAttachmentName = null
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
private fun MessageBubble(
    msg: BackendRepository.MessageUi,
    isDeleting: Boolean,
    onDeleteLocal: () -> Unit,
    onDeleteGlobal: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (msg.isMine) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .fillMaxHeight(),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (msg.isMine) "Me" else "Peer",
                    style = MaterialTheme.typography.labelMedium,
                )

                if (msg.hasAttachments) {
                    Text("📎 attachment")
                }

                Text(msg.text)

                Text(
                    text = msg.createdAt,
                    style = MaterialTheme.typography.bodySmall,
                )

                if (msg.isMine) {
                    val status = when {
                        msg.readAt != null -> "Read"
                        msg.deliveredAt != null -> "Delivered"
                        else -> "Sent"
                    }

                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onDeleteLocal,
                        enabled = !isDeleting,
                    ) {
                        Text(if (isDeleting) "..." else "Hide")
                    }

                    if (msg.isMine) {
                        Button(
                            onClick = onDeleteGlobal,
                            enabled = !isDeleting,
                        ) {
                            Text(if (isDeleting) "..." else "Delete all")
                        }
                    }
                }
            }
        }
    }
}