package com.example.securechatapp.crypto.engine

import android.util.Base64
import java.security.SecureRandom
import java.time.Instant

fun CryptoEngine.nowIso(): String = Instant.now().toString()

fun CryptoEngine.encryptPlainText(plainText: String): EncryptedPlainText {
    val payload = encrypt(plainText)
    return EncryptedPlainText(
        ciphertext = payload,
        nonce = payload.extractNonceBase64(),
    )
}

fun CryptoEngine.decryptToPlainText(ciphertext: String): String = decrypt(ciphertext)

fun CryptoEngine.randomBase64(byteCount: Int): String {
    require(byteCount > 0) { "byteCount must be positive" }
    val bytes = ByteArray(byteCount)
    SecureRandom().nextBytes(bytes)
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}

data class EncryptedPlainText(
    val ciphertext: String,
    val nonce: String,
)

private fun String.extractNonceBase64(): String {
    return runCatching {
        val decoded = Base64.decode(this, Base64.NO_WRAP)
        val nonce = decoded.copyOfRange(0, minOf(12, decoded.size))
        Base64.encodeToString(nonce, Base64.NO_WRAP)
    }.getOrDefault("")
}
