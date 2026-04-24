package com.example.securechatapp.crypto.signal

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.SignalProtocolAddress
import org.whispersystems.libsignal.state.IdentityKeyStore.Direction
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SessionRecord
import org.whispersystems.libsignal.state.SignalProtocolStore
import org.whispersystems.libsignal.state.SignedPreKeyRecord
import org.whispersystems.libsignal.util.KeyHelper

@Singleton
class PersistentSignalProtocolStore @Inject constructor(
    @ApplicationContext context: Context,
) : SignalProtocolStore {

    private val preferences: SharedPreferences = context.getSharedPreferences(
        "signal_protocol_store_v1",
        Context.MODE_PRIVATE,
    )

    override fun getIdentityKeyPair(): IdentityKeyPair {
        val encoded = preferences.getString(KEY_IDENTITY_PAIR, null)
        if (!encoded.isNullOrBlank()) {
            return IdentityKeyPair(decode(encoded))
        }

        val identityKeyPair = KeyHelper.generateIdentityKeyPair()
        preferences.edit()
            .putString(KEY_IDENTITY_PAIR, encode(identityKeyPair.serialize()))
            .apply()
        return identityKeyPair
    }

    override fun getLocalRegistrationId(): Int {
        val existing = preferences.getInt(KEY_REGISTRATION_ID, 0)
        if (existing > 0) return existing

        val registrationId = KeyHelper.generateRegistrationId(false)
        preferences.edit()
            .putInt(KEY_REGISTRATION_ID, registrationId)
            .apply()
        return registrationId
    }

    override fun saveIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
    ): Boolean {
        val key = identityKeyName(address)
        val previous = preferences.getString(key, null)
        val current = encode(identityKey.serialize())
        preferences.edit().putString(key, current).apply()
        return previous == null || previous != current
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: Direction,
    ): Boolean {
        val saved = preferences.getString(identityKeyName(address), null) ?: return true
        return saved == encode(identityKey.serialize())
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        val encoded = preferences.getString(identityKeyName(address), null) ?: return null
        return IdentityKey(decode(encoded), 0)
    }

    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        val encoded = preferences.getString(preKeyName(preKeyId), null)
            ?: throw IllegalArgumentException("Missing Signal pre-key: $preKeyId")
        return PreKeyRecord(decode(encoded))
    }

    override fun storePreKey(
        preKeyId: Int,
        record: PreKeyRecord,
    ) {
        preferences.edit()
            .putString(preKeyName(preKeyId), encode(record.serialize()))
            .apply()
    }

    override fun containsPreKey(preKeyId: Int): Boolean =
        preferences.contains(preKeyName(preKeyId))

    override fun removePreKey(preKeyId: Int) {
        preferences.edit().remove(preKeyName(preKeyId)).apply()
    }

    override fun loadSession(address: SignalProtocolAddress): SessionRecord {
        val encoded = preferences.getString(sessionName(address), null)
        return if (encoded.isNullOrBlank()) SessionRecord() else SessionRecord(decode(encoded))
    }

    override fun getSubDeviceSessions(name: String): MutableList<Int> {
        val prefix = "$KEY_SESSION_PREFIX$name:"
        return preferences.all.keys
            .asSequence()
            .filter { it.startsWith(prefix) }
            .mapNotNull { it.substringAfterLast(":").toIntOrNull() }
            .toMutableList()
    }

    override fun storeSession(
        address: SignalProtocolAddress,
        record: SessionRecord,
    ) {
        preferences.edit()
            .putString(sessionName(address), encode(record.serialize()))
            .apply()
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean =
        preferences.contains(sessionName(address))

    override fun deleteSession(address: SignalProtocolAddress) {
        preferences.edit().remove(sessionName(address)).apply()
    }

    override fun deleteAllSessions(name: String) {
        val prefix = "$KEY_SESSION_PREFIX$name:"
        val editor = preferences.edit()
        preferences.all.keys
            .filter { it.startsWith(prefix) }
            .forEach(editor::remove)
        editor.apply()
    }

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        val encoded = preferences.getString(signedPreKeyName(signedPreKeyId), null)
            ?: throw IllegalArgumentException("Missing Signal signed pre-key: $signedPreKeyId")
        return SignedPreKeyRecord(decode(encoded))
    }

    override fun loadSignedPreKeys(): MutableList<SignedPreKeyRecord> =
        preferences.all
            .filterKeys { it.startsWith(KEY_SIGNED_PREKEY_PREFIX) }
            .values
            .mapNotNull { value -> (value as? String)?.let { SignedPreKeyRecord(decode(it)) } }
            .toMutableList()

    override fun storeSignedPreKey(
        signedPreKeyId: Int,
        record: SignedPreKeyRecord,
    ) {
        preferences.edit()
            .putString(signedPreKeyName(signedPreKeyId), encode(record.serialize()))
            .putInt(KEY_CURRENT_SIGNED_PREKEY_ID, signedPreKeyId)
            .apply()
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean =
        preferences.contains(signedPreKeyName(signedPreKeyId))

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        preferences.edit().remove(signedPreKeyName(signedPreKeyId)).apply()
    }

    fun currentSignedPreKeyId(): Int =
        preferences.getInt(KEY_CURRENT_SIGNED_PREKEY_ID, 0)

    private fun identityKeyName(address: SignalProtocolAddress): String =
        "$KEY_IDENTITY_PREFIX${address.name}:${address.deviceId}"

    private fun sessionName(address: SignalProtocolAddress): String =
        "$KEY_SESSION_PREFIX${address.name}:${address.deviceId}"

    private fun preKeyName(preKeyId: Int): String =
        "$KEY_PREKEY_PREFIX$preKeyId"

    private fun signedPreKeyName(signedPreKeyId: Int): String =
        "$KEY_SIGNED_PREKEY_PREFIX$signedPreKeyId"

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray =
        Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        const val KEY_IDENTITY_PAIR = "identity_pair"
        const val KEY_REGISTRATION_ID = "registration_id"
        const val KEY_CURRENT_SIGNED_PREKEY_ID = "current_signed_prekey_id"
        const val KEY_IDENTITY_PREFIX = "identity:"
        const val KEY_SESSION_PREFIX = "session:"
        const val KEY_PREKEY_PREFIX = "prekey:"
        const val KEY_SIGNED_PREKEY_PREFIX = "signed_prekey:"
    }
}
