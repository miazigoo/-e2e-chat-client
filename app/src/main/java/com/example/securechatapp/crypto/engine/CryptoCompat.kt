package com.example.securechatapp.crypto.engine

import android.util.Base64
import java.security.SecureRandom
import java.time.Instant

data class EncryptedPlainText(
    val ciphertext: String,
    val nonce: String,
)

fun CryptoEngine.nowIso(): String = Instant.now().toString()

fun CryptoEngine.encryptPlainText(plainText: String): EncryptedPlainText {
    val ciphertext = encrypt(plainText)
    val nonce = runCatching {
        val decoded = Base64.decode(ciphertext, Base64.NO_WRAP)
        if (decoded.size >= GCM_NONCE_SIZE_BYTES) {
            Base64.encodeToString(
                decoded.copyOfRange(0, GCM_NONCE_SIZE_BYTES),
                Base64.NO_WRAP,
            )
        } else {
            ""
        }
    }.getOrDefault("")

    return EncryptedPlainText(
        ciphertext = ciphertext,
        nonce = nonce,
    )
}

fun CryptoEngine.decryptToPlainText(ciphertext: String): String = decrypt(ciphertext)

fun CryptoEngine.randomBase64(byteCount: Int): String {
    require(byteCount > 0) { "byteCount must be positive" }

    val bytes = ByteArray(byteCount)
    SecureRandom().nextBytes(bytes)
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}

private const val GCM_NONCE_SIZE_BYTES = 12
