package com.example.securechatapp.core.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
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

    fun encryptBlob(plainBytes: ByteArray): EncryptedBlobResult {
        val keyBytes = ByteArray(32)
        random.nextBytes(keyBytes)

        val nonceBytes = ByteArray(12)
        random.nextBytes(nonceBytes)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val gcmSpec = GCMParameterSpec(128, nonceBytes)

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val ciphertext = cipher.doFinal(plainBytes)

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
    ): ByteArray {
        val keyBytes = Base64.getDecoder().decode(keyBase64)
        val nonceBytes = Base64.getDecoder().decode(nonceBase64)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val gcmSpec = GCMParameterSpec(128, nonceBytes)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        return cipher.doFinal(ciphertext)
    }

    fun encryptText(value: String): EncryptedBlobResult {
        return encryptBlob(value.toByteArray(Charsets.UTF_8))
    }

    fun decryptText(
        ciphertextBase64: String,
        keyBase64: String,
        nonceBase64: String,
    ): String {
        val ciphertext = Base64.getDecoder().decode(ciphertextBase64)
        val plainBytes = decryptBlob(
            ciphertext = ciphertext,
            keyBase64 = keyBase64,
            nonceBase64 = nonceBase64,
        )
        return String(plainBytes, Charsets.UTF_8)
    }

    fun toBase64(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    fun fromBase64(value: String): ByteArray =
        Base64.getDecoder().decode(value)

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { b -> "%02x".format(b) }
    }
}
