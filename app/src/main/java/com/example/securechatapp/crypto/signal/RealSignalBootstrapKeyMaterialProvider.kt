package com.example.securechatapp.crypto.signal

import android.util.Base64
import android.util.Log
import com.example.securechatapp.data.remote.dto.auth.OneTimePreKeyDto
import javax.inject.Inject
import javax.inject.Singleton
import org.whispersystems.libsignal.util.KeyHelper

@Singleton
class RealSignalBootstrapKeyMaterialProvider @Inject constructor(
    private val store: PersistentSignalProtocolStore,
) : SignalBootstrapKeyMaterialProvider {

    private val logTag = "SignalBootstrap"

    override suspend fun getOrCreateBootstrapMaterial(
        oneTimePreKeyCount: Int,
    ): SignalBootstrapKeyMaterial {
        return runCatching {
            getOrCreateBootstrapMaterialInternal(oneTimePreKeyCount)
        }.getOrElse { error ->
            Log.e(logTag, "bootstrap material: initial build failed, resetting Signal state", error)
            store.resetSignalState()
            getOrCreateBootstrapMaterialInternal(oneTimePreKeyCount)
        }
    }

    private fun getOrCreateBootstrapMaterialInternal(
        oneTimePreKeyCount: Int,
    ): SignalBootstrapKeyMaterial {
        require(oneTimePreKeyCount in 1..200) {
            "Signal bootstrap one-time pre-key count must be between 1 and 200"
        }

        Log.d(logTag, "bootstrap material: reading identity key pair")
        val identityKeyPair = store.identityKeyPair
        Log.d(logTag, "bootstrap material: identity key pair ready")

        val signedPreKeyId = store.currentSignedPreKeyId().takeIf { it > 0 } ?: 1
        Log.d(logTag, "bootstrap material: signedPreKeyId=$signedPreKeyId")
        val signedPreKey = if (store.containsSignedPreKey(signedPreKeyId)) {
            Log.d(logTag, "bootstrap material: loading existing signed pre-key")
            store.loadSignedPreKey(signedPreKeyId)
        } else {
            Log.d(logTag, "bootstrap material: generating new signed pre-key")
            KeyHelper.generateSignedPreKey(identityKeyPair, signedPreKeyId).also {
                store.storeSignedPreKey(it.id, it)
            }
        }
        Log.d(logTag, "bootstrap material: signed pre-key ready")

        val nextPreKeyId = nextPreKeyId()
        Log.d(logTag, "bootstrap material: generating $oneTimePreKeyCount pre-keys from id=$nextPreKeyId")
        val preKeys = KeyHelper.generatePreKeys(nextPreKeyId, oneTimePreKeyCount)
        Log.d(logTag, "bootstrap material: generated ${preKeys.size} pre-keys, storing")
        preKeys.forEach { store.storePreKey(it.id, it) }
        Log.d(logTag, "bootstrap material: pre-keys stored, reading registration id")

        val registrationId = store.localRegistrationId
        Log.d(logTag, "bootstrap material: registration id ready")

        return SignalBootstrapKeyMaterial(
            registrationId = registrationId,
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
        ).also {
            Log.d(logTag, "bootstrap material: result built successfully")
        }
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
