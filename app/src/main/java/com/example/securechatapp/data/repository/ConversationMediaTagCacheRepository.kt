package com.example.securechatapp.data.repository

import com.example.securechatapp.data.local.db.SecureChatDatabase
import com.example.securechatapp.data.local.entity.ConversationMediaTagCacheEntity
import com.example.securechatapp.domain.model.MediaTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationMediaTagCacheRepository @Inject constructor(
    database: SecureChatDatabase,
) {
    private val dao = database.conversationMediaTagCacheDao()

    suspend fun listConversationTags(conversationId: Int): List<MediaTag> {
        return dao.listByConversation(conversationId).map(::toDomain)
    }

    suspend fun replaceConversationTags(
        conversationId: Int,
        tags: List<MediaTag>,
    ) {
        dao.deleteByConversation(conversationId)
        if (tags.isNotEmpty()) {
            dao.upsertAll(tags.map(::toEntity))
        }
    }

    suspend fun upsertTag(tag: MediaTag) {
        dao.upsert(toEntity(tag))
    }

    suspend fun deleteTag(tagId: Int) {
        dao.deleteByTagId(tagId)
    }

    private fun toEntity(tag: MediaTag): ConversationMediaTagCacheEntity {
        return ConversationMediaTagCacheEntity(
            tagId = tag.tagId,
            conversationId = tag.conversationId,
            name = tag.name,
            color = tag.color,
            createdByUserId = tag.createdByUserId,
            createdAt = tag.createdAt,
            updatedAt = tag.updatedAt,
        )
    }

    private fun toDomain(entity: ConversationMediaTagCacheEntity): MediaTag {
        return MediaTag(
            tagId = entity.tagId,
            conversationId = entity.conversationId,
            name = entity.name,
            color = entity.color,
            createdByUserId = entity.createdByUserId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }
}
