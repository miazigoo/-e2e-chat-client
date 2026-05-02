package com.example.securechatapp.data.repository

import com.example.securechatapp.data.remote.api.ChatBackendApi
import com.example.securechatapp.data.remote.dto.CreateMediaTagRequestDto
import com.example.securechatapp.data.remote.dto.MediaTagDto
import com.example.securechatapp.data.remote.dto.UpdateMediaTagRequestDto
import com.example.securechatapp.domain.model.MediaTag
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class MediaTagRepository @Inject constructor(
    private val api: ChatBackendApi,
    private val cacheRepository: ConversationMediaTagCacheRepository,
    json: Json,
) : BaseApiRepository(json) {

    suspend fun listCachedConversationTags(
        conversationId: Int,
    ): List<MediaTag> {
        return cacheRepository.listConversationTags(conversationId)
    }

    suspend fun listConversationTags(
        conversationId: Int,
    ): List<MediaTag> {
        return runCatching {
            safe { api.listConversationMediaTags(conversationId).data }
                .items
                .map(::toDomain)
        }.onSuccess { tags ->
            cacheRepository.replaceConversationTags(conversationId, tags)
        }.getOrElse { error ->
            val cached = cacheRepository.listConversationTags(conversationId)
            if (cached.isNotEmpty() && error.isRecoverableNetworkUncertainty()) {
                cached
            } else {
                throw error
            }
        }
    }

    suspend fun createTag(
        conversationId: Int,
        name: String,
        color: String? = null,
    ): MediaTag {
        val normalizedName = name.trim()
        return runCatching {
            safe {
                api.createConversationMediaTag(
                    conversationId = conversationId,
                    body = CreateMediaTagRequestDto(
                        name = normalizedName,
                        color = color,
                    ),
                ).data
            }.let(::toDomain)
        }.getOrElse { error ->
            if (!error.isRecoverableNetworkUncertainty()) {
                throw error
            }

            val recovered = listConversationTags(conversationId)
                .lastOrNull { tag ->
                    tag.name.equals(normalizedName, ignoreCase = true) &&
                        tag.color == color
                }
            recovered ?: throw error
        }.also { created ->
            cacheRepository.upsertTag(created)
        }
    }

    suspend fun updateTag(
        conversationId: Int,
        tagId: Int,
        name: String? = null,
        color: String? = null,
    ): MediaTag {
        val normalizedName = name?.trim()
        return runCatching {
            safe {
                api.updateConversationMediaTag(
                    conversationId = conversationId,
                    tagId = tagId,
                    body = UpdateMediaTagRequestDto(
                        name = normalizedName,
                        color = color,
                    ),
                ).data
            }.let(::toDomain)
        }.getOrElse { error ->
            if (!error.isRecoverableNetworkUncertainty()) {
                throw error
            }

            val recovered = listConversationTags(conversationId)
                .firstOrNull { tag ->
                    tag.tagId == tagId &&
                        (normalizedName == null || tag.name == normalizedName) &&
                        (color == null || tag.color == color)
                }
            recovered ?: throw error
        }.also { updated ->
            cacheRepository.upsertTag(updated)
        }
    }

    suspend fun deleteTag(
        conversationId: Int,
        tagId: Int,
    ) {
        runCatching {
            safe {
                api.deleteConversationMediaTag(
                    conversationId = conversationId,
                    tagId = tagId,
                )
            }
        }.getOrElse { error ->
            if (!error.isRecoverableNetworkUncertainty()) {
                throw error
            }

            val stillExists = listConversationTags(conversationId)
                .any { tag -> tag.tagId == tagId }
            if (stillExists) {
                throw error
            }
        }
        cacheRepository.deleteTag(tagId)
    }

    private fun toDomain(
        dto: MediaTagDto,
    ): MediaTag {
        return MediaTag(
            tagId = dto.tagId,
            conversationId = dto.conversationId,
            name = dto.name,
            color = dto.color,
            createdByUserId = dto.createdByUserId,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
        )
    }
}
