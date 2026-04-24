package com.example.securechatapp.crypto.signal

import android.util.Base64
import com.example.securechatapp.domain.model.SignalPublicPreKey
import com.example.securechatapp.domain.model.SignalSignedPreKey
import javax.inject.Inject
import javax.inject.Singleton
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.util.KeyHelper

@Singleton
class RealSignalPreKeyMaterialProvider @Inject constructor(
    private val store: PersistentSignalProtocolStore,
) : SignalPreKeyMaterialProvider {

    override suspend fun generateOneTimePreKeys(count: Int): List<SignalPublicPreKey> {
        require(count in 1..200) { "Signal one-time pre-key count must be between 1 and 200" }

        val startId = nextPreKeyId()
        return KeyHelper.generatePreKeys(startId, count).map { record ->
            store.storePreKey(record.id, record)
            SignalPublicPreKey(
                preKeyId = record.id,
                publicPreKey = encode(record.keyPair.publicKey.serialize()),
            )
        }
    }

    override suspend fun generateSignedPreKey(): SignalSignedPreKey {
        val identityKeyPair = store.identityKeyPair
        val signedPreKeyId = nextSignedPreKeyId()
        val record = KeyHelper.generateSignedPreKey(identityKeyPair, signedPreKeyId)
        store.storeSignedPreKey(record.id, record)
        return SignalSignedPreKey(
            signedPreKeyId = record.id,
            signedPreKey = encode(record.keyPair.publicKey.serialize()),
            signature = encode(record.signature),
        )
    }

    private fun nextPreKeyId(): Int {
        val usedIds = (1..MAX_PREKEY_ID).filter(store::containsPreKey).toSet()
        return (1..MAX_PREKEY_ID).firstOrNull { it !in usedIds } ?: 1
    }

    private fun nextSignedPreKeyId(): Int {
        val current = store.currentSignedPreKeyId()
        return if (current <= 0 || current >= MAX_SIGNED_PREKEY_ID) 1 else current + 1
    }

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private companion object {
        const val MAX_PREKEY_ID = 16_777_215
        const val MAX_SIGNED_PREKEY_ID = 16_777_215
    }
}
