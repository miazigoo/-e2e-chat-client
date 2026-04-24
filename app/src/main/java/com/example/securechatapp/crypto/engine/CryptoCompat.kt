package com.example.securechatapp.crypto.engine

import java.security.SecureRandom
import java.time.OffsetDateTime
import java.util.Base64

data class EncryptedTextPayload(
    val ciphertext: String,
    val nonce: String,
)

fun CryptoEngine.nowIso(): String = OffsetDateTime.now().toString()

fun CryptoEngine.encryptPlainText(plainText: String): EncryptedTextPayload {
    val ciphertext = encrypt(plainText)
    val decoded = runCatching {
        Base64.getDecoder().decode(ciphertext)
    }.getOrDefault(ByteArray(0))
    val nonce = if (decoded.size >= 12) {
        Base64.getEncoder().encodeToString(decoded.copyOfRange(0, 12))
    } else {
        ""
    }
    return EncryptedTextPayload(
        ciphertext = ciphertext,
        nonce = nonce,
    )
}

fun CryptoEngine.decryptToPlainText(ciphertext: String): String = decrypt(ciphertext)

fun randomBase64(sizeBytes: Int = 32): String {
    val bytes = ByteArray(sizeBytes)
    SecureRandom().nextBytes(bytes)
    return Base64.getEncoder().encodeToString(bytes)
}
