package com.example.securechatapp.crypto.engine

import android.util.Base64
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class EncryptedPlainTextPayload(
    val ciphertext: String,
    val nonce: String,
)

fun CryptoEngine.nowIso(): String {
    return OffsetDateTime.now(ZoneOffset.UTC).toString()
}

fun CryptoEngine.encryptPlainText(
    plainText: String,
): EncryptedPlainTextPayload {
    val ciphertext = encrypt(plainText)
    val nonce = runCatching {
        val decoded = Base64.decode(ciphertext, Base64.NO_WRAP)
        Base64.encodeToString(decoded.copyOfRange(0, 12), Base64.NO_WRAP)
    }.getOrDefault("")

    return EncryptedPlainTextPayload(
        ciphertext = ciphertext,
        nonce = nonce,
    )
}

fun CryptoEngine.decryptToPlainText(
    ciphertext: String,
): String {
    return decrypt(ciphertext)
}

fun CryptoEngine.randomBase64(
    sizeBytes: Int,
): String {
    val bytes = ByteArray(sizeBytes.coerceAtLeast(1))
    SecureRandom().nextBytes(bytes)
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}
