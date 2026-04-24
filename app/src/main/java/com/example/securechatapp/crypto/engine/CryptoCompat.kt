package com.example.securechatapp.crypto.engine

import android.util.Base64
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class EncryptedPlainText(
    val ciphertext: String,
    val nonce: String,
)

fun CryptoEngine.nowIso(): String =
    OffsetDateTime.now(ZoneOffset.UTC).toString()

fun CryptoEngine.encryptPlainText(plainText: String): EncryptedPlainText {
    val payload = encrypt(plainText)
    val decoded = runCatching { Base64.decode(payload, Base64.NO_WRAP) }.getOrNull()
    val nonce = decoded
        ?.takeIf { it.size >= AES_GCM_NONCE_BYTES }
        ?.copyOfRange(0, AES_GCM_NONCE_BYTES)
        ?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        .orEmpty()

    return EncryptedPlainText(
        ciphertext = payload,
        nonce = nonce,
    )
}

fun CryptoEngine.decryptToPlainText(cipherText: String): String =
    decrypt(cipherText)

fun CryptoEngine.randomBase64(byteCount: Int = DEFAULT_RANDOM_BYTES): String {
    require(byteCount in 16..128) { "byteCount must be in range 16..128" }
    val bytes = ByteArray(byteCount)
    SecureRandom().nextBytes(bytes)
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}

private const val AES_GCM_NONCE_BYTES = 12
private const val DEFAULT_RANDOM_BYTES = 32
