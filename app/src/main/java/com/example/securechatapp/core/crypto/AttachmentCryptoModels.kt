package com.example.securechatapp.core.crypto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EncryptedAttachmentDescriptor(
    @SerialName("attachment_id")
    val attachmentId: Int = 0,

    @SerialName("encrypted_file_name")
    val encryptedFileName: String? = null,

    @SerialName("file_name_key_base64")
    val fileNameKeyBase64: String? = null,

    @SerialName("file_name_nonce_base64")
    val fileNameNonceBase64: String? = null,

    @SerialName("mime_type")
    val mimeType: String? = null,

    @SerialName("file_size")
    val fileSize: Long = 0L,

    @SerialName("sha256_encrypted_blob")
    val sha256EncryptedBlob: String = "",

    @SerialName("blob_key_base64")
    val blobKeyBase64: String = "",

    @SerialName("blob_nonce_base64")
    val blobNonceBase64: String = "",
)

@Serializable
data class EncryptedAttachmentsPayload(
    val attachments: List<EncryptedAttachmentDescriptor> = emptyList(),
)
