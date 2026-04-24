package com.example.securechatapp.crypto.sharedsecret

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

data class ConversationSharedSecretState(
    val enabled: Boolean,
    val fingerprint: String? = null,
)

data class SharedSecretEncryptedPayload(
    val ciphertext: String,
    val nonce: String,
    val fingerprint: String,
)

class ConversationSharedSecretMissingException(
    message: String,
) : IllegalStateException(message)

@Singleton
class ConversationSharedSecretCrypto @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        "conversation_shared_secrets_v1",
        Context.MODE_PRIVATE,
    )
    private val random = SecureRandom()

    fun getState(conversationUuid: String): ConversationSharedSecretState {
        val normalizedUuid = normalizeConversationUuid(conversationUuid)
        val keyBase64 = preferences.getString(keyName(normalizedUuid, "key"), null)
        val fingerprint = preferences.getString(keyName(normalizedUuid, "fingerprint"), null)
        return ConversationSharedSecretState(
            enabled = !keyBase64.isNullOrBlank() && !fingerprint.isNullOrBlank(),
            fingerprint = fingerprint,
        )
    }

    fun enable(
        conversationUuid: String,
        token: String,
    ): ConversationSharedSecretState {
        val normalizedToken = token.trim()
        require(normalizedToken.length >= MIN_TOKEN_LENGTH) {
            "Токен должен быть не короче $MIN_TOKEN_LENGTH символов"
        }

        val normalizedUuid = normalizeConversationUuid(conversationUuid)
        val key = deriveKey(
            conversationUuid = normalizedUuid,
            token = normalizedToken,
        )
        val fingerprint = fingerprint(
            conversationUuid = normalizedUuid,
            key = key,
        )

        preferences.edit()
            .putString(keyName(normalizedUuid, "key"), encodeBase64(key))
            .putString(keyName(normalizedUuid, "fingerprint"), fingerprint)
            .apply()

        return ConversationSharedSecretState(
            enabled = true,
            fingerprint = fingerprint,
        )
    }

    fun disable(conversationUuid: String) {
        val normalizedUuid = normalizeConversationUuid(conversationUuid)
        preferences.edit()
            .remove(keyName(normalizedUuid, "key"))
            .remove(keyName(normalizedUuid, "fingerprint"))
            .apply()
    }

    fun encryptIfEnabled(
        conversationUuid: String,
        plainText: String,
    ): SharedSecretEncryptedPayload? {
        val normalizedUuid = normalizeConversationUuid(conversationUuid)
        val state = getState(normalizedUuid)
        if (!state.enabled || state.fingerprint.isNullOrBlank()) return null

        val key = loadKey(normalizedUuid)
        val nonce = ByteArray(GCM_NONCE_SIZE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, AES),
            GCMParameterSpec(GCM_TAG_SIZE_BITS, nonce),
        )
        cipher.updateAAD(buildAad(normalizedUuid, state.fingerprint))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return SharedSecretEncryptedPayload(
            ciphertext = buildPayload(
                fingerprint = state.fingerprint,
                nonce = nonce,
                ciphertext = encrypted,
            ),
            nonce = encodeBase64(nonce),
            fingerprint = state.fingerprint,
        )
    }

    fun decryptIfNeeded(
        conversationUuid: String,
        ciphertext: String,
    ): String {
        if (!ciphertext.startsWith(PAYLOAD_PREFIX)) return ciphertext

        val normalizedUuid = normalizeConversationUuid(conversationUuid)
        val parts = ciphertext.split(":", limit = 4)
        if (parts.size != 4) {
            throw ConversationSharedSecretMissingException("Некорректный формат дополнительного шифрования")
        }

        val fingerprint = parts[1]
        val nonce = decodeBase64(parts[2])
        val encrypted = decodeBase64(parts[3])
        val state = getState(normalizedUuid)

        if (!state.enabled || state.fingerprint != fingerprint) {
            throw ConversationSharedSecretMissingException(
                "Сообщение защищено доп. шифрованием. Введите токен этого чата в настройках."
            )
        }

        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(loadKey(normalizedUuid), AES),
            GCMParameterSpec(GCM_TAG_SIZE_BITS, nonce),
        )
        cipher.updateAAD(buildAad(normalizedUuid, fingerprint))

        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    fun computeFingerprintPreview(
        conversationUuid: String,
        token: String,
    ): String {
        val normalizedUuid = normalizeConversationUuid(conversationUuid)
        val key = deriveKey(
            conversationUuid = normalizedUuid,
            token = token.trim(),
        )
        return fingerprint(
            conversationUuid = normalizedUuid,
            key = key,
        )
    }

    private fun loadKey(conversationUuid: String): ByteArray {
        val encoded = preferences.getString(keyName(conversationUuid, "key"), null)
            ?: throw ConversationSharedSecretMissingException("Для этого чата не настроен shared secret")
        return decodeBase64(encoded)
    }

    private fun deriveKey(
        conversationUuid: String,
        token: String,
    ): ByteArray {
        val salt = MessageDigest.getInstance("SHA-256")
            .digest("securechat.shared-secret.salt.v1:$conversationUuid".toByteArray(Charsets.UTF_8))
        val spec = PBEKeySpec(
            token.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            AES_KEY_SIZE_BITS,
        )
        return SecretKeyFactory.getInstance(PBKDF2).generateSecret(spec).encoded
    }

    private fun fingerprint(
        conversationUuid: String,
        key: ByteArray,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("securechat.shared-secret.fingerprint.v1:".toByteArray(Charsets.UTF_8))
        digest.update(conversationUuid.toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(key)
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
            .take(FINGERPRINT_HEX_LENGTH)
    }

    private fun buildPayload(
        fingerprint: String,
        nonce: ByteArray,
        ciphertext: ByteArray,
    ): String {
        return "$PAYLOAD_PREFIX$fingerprint:${encodeBase64(nonce)}:${encodeBase64(ciphertext)}"
    }

    private fun buildAad(
        conversationUuid: String,
        fingerprint: String,
    ): ByteArray {
        return "securechat.shared-secret.message.v1:$conversationUuid:$fingerprint"
            .toByteArray(Charsets.UTF_8)
    }

    private fun normalizeConversationUuid(value: String): String {
        return value.trim().lowercase()
    }

    private fun keyName(conversationUuid: String, suffix: String): String {
        return "$conversationUuid:$suffix"
    }

    private fun encodeBase64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decodeBase64(value: String): ByteArray =
        Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        const val PAYLOAD_PREFIX = "ss1:"
        const val AES = "AES"
        const val AES_GCM = "AES/GCM/NoPadding"
        const val PBKDF2 = "PBKDF2WithHmacSHA256"
        const val AES_KEY_SIZE_BITS = 256
        const val GCM_TAG_SIZE_BITS = 128
        const val GCM_NONCE_SIZE_BYTES = 12
        const val PBKDF2_ITERATIONS = 210_000
        const val FINGERPRINT_HEX_LENGTH = 32
        const val MIN_TOKEN_LENGTH = 8
    }
}
