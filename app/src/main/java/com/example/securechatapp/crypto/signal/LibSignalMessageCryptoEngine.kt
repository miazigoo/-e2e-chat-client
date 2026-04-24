package com.example.securechatapp.crypto.signal

import android.util.Base64
import com.example.securechatapp.domain.model.SignalKeyBundle
import javax.inject.Inject
import javax.inject.Singleton
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.SessionBuilder
import org.whispersystems.libsignal.SessionCipher
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.ecc.ECPublicKey
import org.whispersystems.libsignal.protocol.CiphertextMessage
import org.whispersystems.libsignal.protocol.PreKeySignalMessage
import org.whispersystems.libsignal.protocol.SignalMessage
import org.whispersystems.libsignal.state.PreKeyBundle

interface SignalMessageCryptoEngine {
    suspend fun encrypt(
        remoteAddress: SignalRemoteAddress,
        remoteBundle: SignalKeyBundle,
        plainText: String,
    ): SignalEncryptedMessage

    suspend fun decrypt(
        remoteAddress: SignalRemoteAddress,
        ciphertext: String,
        type: SignalCiphertextType,
    ): String
}

@Singleton
class LibSignalMessageCryptoEngine @Inject constructor(
    private val store: PersistentSignalProtocolStore,
) : SignalMessageCryptoEngine {

    override suspend fun encrypt(
        remoteAddress: SignalRemoteAddress,
        remoteBundle: SignalKeyBundle,
        plainText: String,
    ): SignalEncryptedMessage {
        val address = remoteAddress.toSignalAddress()
        if (!store.containsSession(address)) {
            SessionBuilder(store, address).process(remoteBundle.toPreKeyBundle())
        }

        val encrypted = SessionCipher(store, address)
            .encrypt(plainText.toByteArray(Charsets.UTF_8))

        return SignalEncryptedMessage(
            ciphertext = encode(encrypted.serialize()),
            type = when (encrypted.type) {
                CiphertextMessage.PREKEY_TYPE -> SignalCiphertextType.PREKEY
                CiphertextMessage.WHISPER_TYPE -> SignalCiphertextType.SIGNAL
                else -> throw IllegalStateException("Unsupported Signal ciphertext type: ${encrypted.type}")
            },
        )
    }

    override suspend fun decrypt(
        remoteAddress: SignalRemoteAddress,
        ciphertext: String,
        type: SignalCiphertextType,
    ): String {
        val address = remoteAddress.toSignalAddress()
        val cipher = SessionCipher(store, address)
        val decrypted = when (type) {
            SignalCiphertextType.PREKEY -> cipher.decrypt(PreKeySignalMessage(decode(ciphertext)))
            SignalCiphertextType.SIGNAL -> cipher.decrypt(SignalMessage(decode(ciphertext)))
        }
        return String(decrypted, Charsets.UTF_8)
    }

    private fun SignalRemoteAddress.toSignalAddress(): SignalProtocolAddress =
        SignalProtocolAddress(addressName, deviceId)

    private fun SignalKeyBundle.toPreKeyBundle(): PreKeyBundle {
        val oneTimePreKey = oneTimePreKey
        return PreKeyBundle(
            registrationId,
            deviceId,
            oneTimePreKey?.preKeyId ?: 0,
            oneTimePreKey?.publicPreKey?.let(::decodePublicKey),
            signedPreKeyId,
            decodePublicKey(signedPreKey),
            decode(signedPreKeySignature),
            IdentityKey(decode(publicIdentityKey), 0),
        )
    }

    private fun decodePublicKey(value: String): ECPublicKey =
        Curve.decodePoint(decode(value), 0)

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray =
        Base64.decode(value, Base64.NO_WRAP)
}
