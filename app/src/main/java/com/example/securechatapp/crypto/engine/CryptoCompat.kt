package com.example.securechatapp.crypto.engine

import java.security.SecureRandom
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

data class EncryptedTextPayload(
    val ciphertext: String,
    val nonce: String,
)

private val secureRandom = SecureRandom()

fun CryptoEngine.encryptPlainText(plainText: String): EncryptedTextPayload {
    val ciphertext = encrypt(plainText)
    val nonce = extractNonceFromCiphertext(ciphertext) ?: randomBase64(12)

    return EncryptedTextPayload(
        ciphertext = ciphertext,
        nonce = nonce,
    )
}

fun CryptoEngine.decryptToPlainText(cipherText: String): String {
    return decrypt(cipherText)
}

fun CryptoEngine.randomBase64(sizeBytes: Int): String {
    require(sizeBytes > 0) { "sizeBytes must be greater than 0" }

    val bytes = ByteArray(sizeBytes)
    secureRandom.nextBytes(bytes)
    return Base64.getEncoder().encodeToString(bytes)
}

fun CryptoEngine.nowIso(): String {
    return OffsetDateTime.now(ZoneOffset.UTC)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}

private fun extractNonceFromCiphertext(ciphertext: String): String? {
    return runCatching {
        val decoded = Base64.getDecoder().decode(ciphertext)
        if (decoded.size <= 12) {
            return null
        }

        val iv = decoded.copyOfRange(0, 12)
        Base64.getEncoder().encodeToString(iv)
    }.getOrNull()
}
