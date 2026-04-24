package com.example.securechatapp.crypto.signal

import android.util.Base64
import com.example.securechatapp.data.remote.dto.auth.OneTimePreKeyDto
import javax.inject.Inject
import javax.inject.Singleton
import org.whispersystems.libsignal.util.KeyHelper

@Singleton
class RealSignalBootstrapKeyMaterialProvider @Inject constructor(
    private val store: PersistentSignalProtocolStore,
) : SignalBootstrapKeyMaterialProvider {

    override suspend fun getOrCreateBootstrapMaterial(
        oneTimePreKeyCount: Int,
    ): SignalBootstrapKeyMaterial {
        require(oneTimePreKeyCount in 1..200) {
            "Signal bootstrap one-time pre-key count must be between 1 and 200"
        }

        val identityKeyPair = store.identityKeyPair
        val signedPreKeyId = store.currentSignedPreKeyId().takeIf { it > 0 } ?: 1
        val signedPreKey = if (store.containsSignedPreKey(signedPreKeyId)) {
            store.loadSignedPreKey(signedPreKeyId)
        } else {
            KeyHelper.generateSignedPreKey(identityKeyPair, signedPreKeyId).also {
                store.storeSignedPreKey(it.id, it)
            }
        }

        val preKeys = KeyHelper.generatePreKeys(nextPreKeyId(), oneTimePreKeyCount)
        preKeys.forEach { store.storePreKey(it.id, it) }

        return SignalBootstrapKeyMaterial(
            registrationId = store.localRegistrationId,
            publicIdentityKey = encode(identityKeyPair.publicKey.serialize()),
            publicSigningKey = encode(identityKeyPair.publicKey.serialize()),
            signedPreKeyId = signedPreKey.id,
            signedPreKey = encode(signedPreKey.keyPair.publicKey.serialize()),
            signedPreKeySignature = encode(signedPreKey.signature),
            oneTimePreKeys = preKeys.map { record ->
                OneTimePreKeyDto(
                    prekeyId = record.id,
                    publicPrekey = encode(record.keyPair.publicKey.serialize()),
                )
            },
        )
    }

    private fun nextPreKeyId(): Int {
        for (id in 1..MAX_PREKEY_ID) {
            if (!store.containsPreKey(id)) return id
        }
        return 1
    }

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private companion object {
        const val MAX_PREKEY_ID = 16_777_215
    }
}
