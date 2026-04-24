package com.example.securechatapp.core.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

data class EncryptedBlobResult(
    val ciphertext: ByteArray,
    val keyBase64: String,
    val nonceBase64: String,
    val sha256EncryptedBlob: String,
)

@Singleton
class AttachmentCryptoEngine @Inject constructor() {

    private val random = SecureRandom()

    fun encryptBlob(
        plainBytes: ByteArray,
        associatedData: ByteArray = ATTACHMENT_AAD,
    ): EncryptedBlobResult {
        require(plainBytes.isNotEmpty()) { "Attachment body must not be empty" }

        val keyBytes = ByteArray(AES_256_KEY_BYTES)
        val nonceBytes = ByteArray(AES_GCM_NONCE_BYTES)

        random.nextBytes(keyBytes)
        random.nextBytes(nonceBytes)

        val ciphertext = aesGcm(
            mode = Cipher.ENCRYPT_MODE,
            input = plainBytes,
            keyBytes = keyBytes,
            nonceBytes = nonceBytes,
            associatedData = associatedData,
        )

        return EncryptedBlobResult(
            ciphertext = ciphertext,
            keyBase64 = Base64.getEncoder().encodeToString(keyBytes),
            nonceBase64 = Base64.getEncoder().encodeToString(nonceBytes),
            sha256EncryptedBlob = sha256Hex(ciphertext),
        )
    }

    fun decryptBlob(
        ciphertext: ByteArray,
        keyBase64: String,
        nonceBase64: String,
        associatedData: ByteArray = ATTACHMENT_AAD,
    ): ByteArray {
        require(ciphertext.isNotEmpty()) { "Encrypted attachment body must not be empty" }

        val keyBytes = decodeRequiredBase64(
            value = keyBase64,
            expectedSize = AES_256_KEY_BYTES,
            fieldName = "attachment key",
        )
        val nonceBytes = decodeRequiredBase64(
            value = nonceBase64,
            expectedSize = AES_GCM_NONCE_BYTES,
            fieldName = "attachment nonce",
        )

        return aesGcm(
            mode = Cipher.DECRYPT_MODE,
            input = ciphertext,
            keyBytes = keyBytes,
            nonceBytes = nonceBytes,
            associatedData = associatedData,
        )
    }

    fun encryptText(value: String): EncryptedBlobResult {
        require(value.isNotBlank()) { "Encrypted text value must not be blank" }
        return encryptBlob(
            plainBytes = value.toByteArray(Charsets.UTF_8),
            associatedData = FILE_NAME_AAD,
        )
    }

    fun decryptText(
        ciphertextBase64: String,
        keyBase64: String,
        nonceBase64: String,
    ): String {
        val ciphertext = fromBase64(ciphertextBase64)
        val plainBytes = decryptBlob(
            ciphertext = ciphertext,
            keyBase64 = keyBase64,
            nonceBase64 = nonceBase64,
            associatedData = FILE_NAME_AAD,
        )
        return String(plainBytes, Charsets.UTF_8)
    }

    fun toBase64(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    fun fromBase64(value: String): ByteArray =
        runCatching { Base64.getDecoder().decode(value) }
            .getOrElse { error("Invalid base64 value") }

    private fun aesGcm(
        mode: Int,
        input: ByteArray,
        keyBytes: ByteArray,
        nonceBytes: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val gcmSpec = GCMParameterSpec(AES_GCM_TAG_BITS, nonceBytes)

        cipher.init(mode, keySpec, gcmSpec)
        cipher.updateAAD(associatedData)
        return cipher.doFinal(input)
    }

    private fun decodeRequiredBase64(
        value: String,
        expectedSize: Int,
        fieldName: String,
    ): ByteArray {
        val bytes = fromBase64(value)
        require(bytes.size == expectedSize) {
            "Invalid $fieldName length: expected $expectedSize bytes, got ${bytes.size}"
        }
        return bytes
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { b -> "%02x".format(b) }
    }

    private companion object {
        const val AES_256_KEY_BYTES = 32
        const val AES_GCM_NONCE_BYTES = 12
        const val AES_GCM_TAG_BITS = 128

        val ATTACHMENT_AAD = "securechat.attachment.blob.v1".toByteArray(Charsets.UTF_8)
        val FILE_NAME_AAD = "securechat.attachment.filename.v1".toByteArray(Charsets.UTF_8)
    }
}
