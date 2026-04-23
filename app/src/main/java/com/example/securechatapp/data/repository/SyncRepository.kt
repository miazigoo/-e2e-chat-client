package com.example.securechatapp.data.repository

import com.example.securechatapp.data.remote.api.ChatBackendApi
import com.example.securechatapp.domain.model.ConversationEventsPage
import com.example.securechatapp.domain.model.ConversationSyncEvent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class SyncRepository @Inject constructor(
    private val api: ChatBackendApi,
    json: Json,
) : BaseApiRepository(json) {

    suspend fun getConversationEvents(
        conversationId: Int,
        afterEventId: Int?,
        limit: Int = 200,
    ): ConversationEventsPage {
        val data = safe {
            api.getConversationEvents(
                conversationId = conversationId,
                afterEventId = afterEventId,
                limit = limit,
            ).data
        }

        return ConversationEventsPage(
            conversationId = data.conversationId,
            items = data.items.map { item ->
                ConversationSyncEvent(
                    eventId = item.eventId,
                    eventUuid = item.eventUuid,
                    eventType = item.eventType,
                    actorUserId = item.actorUserId,
                    actorDeviceId = item.actorDeviceId,
                    targetMessageId = item.targetMessageId,
                    payload = item.payload,
                    createdAt = item.createdAt,
                )
            },
            nextAfterEventId = data.nextAfterEventId,
            hasMore = data.hasMore,
        )
    }
}
