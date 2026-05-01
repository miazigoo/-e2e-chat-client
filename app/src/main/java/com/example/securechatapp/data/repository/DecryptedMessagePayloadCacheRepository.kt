package com.example.securechatapp.data.repository

import com.example.securechatapp.data.local.db.SecureChatDatabase
import com.example.securechatapp.data.local.entity.DecryptedMessagePayloadCacheEntity
import com.example.securechatapp.domain.model.AttachmentItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Singleton
class DecryptedMessagePayloadCacheRepository @Inject constructor(
    database: SecureChatDatabase,
    private val json: Json,
) {
    private val dao = database.decryptedMessagePayloadCacheDao()

    suspend fun getByMessageIds(
        messageIds: Collection<Int>,
    ): Map<Int, CachedDecryptedMessagePayload> {
        val normalizedIds = messageIds
            .filter { it > 0 }
            .distinct()
        if (normalizedIds.isEmpty()) return emptyMap()

        return dao.listByMessageIds(normalizedIds).associate { entity ->
            entity.messageId to entity.toDomain()
        }
    }

    suspend fun upsertAll(
        payloads: Collection<CachedDecryptedMessagePayload>,
    ) {
        val normalizedPayloads = payloads
            .filter { it.messageId > 0 }
            .distinctBy { it.messageId }
        if (normalizedPayloads.isEmpty()) return

        dao.upsertAll(
            normalizedPayloads.map { payload ->
                payload.toEntity(json)
            }
        )
    }

    data class CachedDecryptedMessagePayload(
        val messageId: Int,
        val messageUuid: String? = null,
        val text: String,
        val attachments: List<AttachmentItem> = emptyList(),
        val updatedAt: String,
    )

    private fun DecryptedMessagePayloadCacheEntity.toDomain(): CachedDecryptedMessagePayload {
        return CachedDecryptedMessagePayload(
            messageId = messageId,
            messageUuid = messageUuid,
            text = text,
            attachments = runCatching {
                json.decodeFromString(
                    ListSerializer(AttachmentItem.serializer()),
                    attachmentsJson,
                )
            }.getOrDefault(emptyList()),
            updatedAt = updatedAt,
        )
    }

    private fun CachedDecryptedMessagePayload.toEntity(
        json: Json,
    ): DecryptedMessagePayloadCacheEntity {
        return DecryptedMessagePayloadCacheEntity(
            messageId = messageId,
            messageUuid = messageUuid,
            text = text,
            attachmentsJson = json.encodeToString(
                ListSerializer(AttachmentItem.serializer()),
                attachments,
            ),
            updatedAt = updatedAt,
        )
    }
}
