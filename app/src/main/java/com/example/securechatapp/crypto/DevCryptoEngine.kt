package com.example.securechatapp.core.crypto

import java.security.SecureRandom
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

data class DevEncryptedPayload(
    val ciphertext: String,
    val nonce: String,
)

@Singleton
class DevCryptoEngine @Inject constructor() {
    private val random = SecureRandom()

    fun randomBase64(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun nowIso(): String {
        return OffsetDateTime.now(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    fun encryptPlainText(plainText: String): DevEncryptedPayload {
        return DevEncryptedPayload(
            ciphertext = Base64.getEncoder().encodeToString(plainText.toByteArray(Charsets.UTF_8)),
            nonce = randomBase64(24),
        )
    }

    fun decryptToPlainText(ciphertext: String): String {
        return runCatching {
            String(Base64.getDecoder().decode(ciphertext), Charsets.UTF_8)
        }.getOrDefault(ciphertext)
    }
}