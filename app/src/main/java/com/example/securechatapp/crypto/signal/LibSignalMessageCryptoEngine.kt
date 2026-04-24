package com.example.securechatapp.crypto.signal

import android.util.Base64
import com.example.securechatapp.domain.model.SignalKeyBundle
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.SignalProtocolStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibSignalMessageCryptoEngine @Inject constructor(
    private val storeProvider: LibSignalStoreProvider,
) : SignalMessageCryptoEngine {

    override fun hasSession(remoteAddress: SignalRemoteAddress): Boolean {
        return storeProvider.store.containsSession(remoteAddress.toProtocolAddress())
    }

    override fun processRemoteBundle(bundle: SignalKeyBundle) {
        val remoteAddress = SignalRemoteAddress(
            userId = bundle.userId,
            deviceId = bundle.deviceId,
        )

        SessionBuilder(
            storeProvider.store,
            remoteAddress.toProtocolAddress(),
        ).process(bundle.toPreKeyBundle())
    }

    override fun encrypt(
        remoteAddress: SignalRemoteAddress,
        plainText: String,
    ): SignalCipherPayload {
        ensureEnabled()
        val cipher = SessionCipher(
            storeProvider.store,
            remoteAddress.toProtocolAddress(),
        )
        val encrypted = cipher.encrypt(plainText.toByteArray(Charsets.UTF_8))

        return SignalCipherPayload(
            ciphertextBase64 = Base64.encodeToString(encrypted.serialize(), Base64.NO_WRAP),
            messageType = encrypted.type,
        )
    }

    override fun decrypt(
        remoteAddress: SignalRemoteAddress,
        payload: SignalCipherPayload,
    ): String {
        ensureEnabled()
        val ciphertext = Base64.decode(payload.ciphertextBase64, Base64.NO_WRAP)
        val cipher = SessionCipher(
            storeProvider.store,
            remoteAddress.toProtocolAddress(),
        )

        val plainBytes = when (payload.messageType) {
            CiphertextMessage.PREKEY_TYPE -> cipher.decrypt(PreKeySignalMessage(ciphertext))
            CiphertextMessage.WHISPER_TYPE -> cipher.decrypt(SignalMessage(ciphertext))
            else -> throw SignalProtocolUnavailableException(
                "Unsupported Signal ciphertext type: ${payload.messageType}.",
            )
        }

        return plainBytes.toString(Charsets.UTF_8)
    }

    private fun ensureEnabled() {
        if (!storeProvider.config.isEnabled) {
            throw SignalProtocolUnavailableException(
                "Signal protocol is disabled by build config.",
            )
        }
    }

    private fun SignalRemoteAddress.toProtocolAddress(): SignalProtocolAddress {
        return SignalProtocolAddress(stableName, deviceId)
    }

    private fun SignalKeyBundle.toPreKeyBundle(): PreKeyBundle {
        return PreKeyBundle(
            requestedByDeviceId,
            deviceId,
            oneTimePreKey?.preKeyId ?: 0,
            oneTimePreKey?.publicPreKey?.decodeSignalPublicKey(),
            DEFAULT_SIGNED_PRE_KEY_ID,
            signedPreKey.decodeSignalPublicKey(),
            signedPreKeySignature.decodeSignalBase64(),
            IdentityKey(publicIdentityKey.decodeSignalBase64(), 0),
        )
    }
}

private fun String.decodeSignalBase64(): ByteArray =
    Base64.decode(this, Base64.NO_WRAP)

private fun String.decodeSignalPublicKey(): ECPublicKey =
    Curve.decodePoint(decodeSignalBase64(), 0)

private const val DEFAULT_SIGNED_PRE_KEY_ID = 1
