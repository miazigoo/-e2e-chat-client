package com.example.securechatapp.core.crypto

import kotlinx.serialization.Serializable

@Serializable
data class SecureMessagePayloadV1(
    val schema: String = SCHEMA,
    val text: String? = null,
    val attachments: List<EncryptedAttachmentDescriptor> = emptyList(),
) {
    companion object {
        const val SCHEMA = "secure_chat_message_v1"
    }
}
