package com.example.securechatapp.crypto.signal

import android.util.Base64
import com.example.securechatapp.domain.model.SignalPublicPreKey
import com.example.securechatapp.domain.model.SignalSignedPreKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealSignalPreKeyMaterialProvider @Inject constructor(
    private val storeProvider: LibSignalStoreProvider,
) : SignalPreKeyMaterialProvider {

    override suspend fun generateOneTimePreKeys(count: Int): List<SignalPublicPreKey> {
        require(count in 1..200) { "count must be in range 1..200" }
        ensureEnabled()

        val startId = nextPositiveId()
        return (0 until count).map { offset ->
            val preKeyId = startId + offset
            val record = PreKeyRecord(preKeyId, Curve.generateKeyPair())
            storeProvider.store.storePreKey(preKeyId, record)

            SignalPublicPreKey(
                preKeyId = preKeyId,
                publicPreKey = record.keyPair.publicKey.serialize().toSignalBase64(),
            )
        }
    }

    override suspend fun generateSignedPreKey(): SignalSignedPreKey {
        ensureEnabled()

        val identityKeyPair: IdentityKeyPair = storeProvider.store.identityKeyPair
        val signedPreKeyId = DEFAULT_SIGNED_PRE_KEY_ID
        val signedPreKeyPair = Curve.generateKeyPair()
        val signature = Curve.calculateSignature(
            identityKeyPair.privateKey,
            signedPreKeyPair.publicKey.serialize(),
        )

        val record = SignedPreKeyRecord(
            signedPreKeyId,
            System.currentTimeMillis(),
            signedPreKeyPair,
            signature,
        )
        storeProvider.store.storeSignedPreKey(signedPreKeyId, record)

        return SignalSignedPreKey(
            signedPreKey = record.keyPair.publicKey.serialize().toSignalBase64(),
            signature = record.signature.toSignalBase64(),
        )
    }

    private fun ensureEnabled() {
        if (!storeProvider.config.isEnabled) {
            throw SignalProtocolUnavailableException(
                "Signal protocol is disabled by build config.",
            )
        }
    }

    private fun nextPositiveId(): Int {
        return SecureRandom().nextInt(MAX_PRE_KEY_ID - 1) + 1
    }

    private fun ByteArray.toSignalBase64(): String =
        Base64.encodeToString(this, Base64.NO_WRAP)

    private companion object {
        const val DEFAULT_SIGNED_PRE_KEY_ID = 1
        const val MAX_PRE_KEY_ID = 16_777_216
    }
}
