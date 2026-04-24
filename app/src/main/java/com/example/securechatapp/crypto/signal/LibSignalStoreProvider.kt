package com.example.securechatapp.crypto.signal

import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.state.impl.InMemorySignalProtocolStore
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibSignalStoreProvider @Inject constructor(
    val config: SignalProtocolConfig,
) {
    val store: InMemorySignalProtocolStore by lazy {
        InMemorySignalProtocolStore(
            IdentityKeyPair.generate(),
            generateRegistrationId(),
        )
    }

    private fun generateRegistrationId(): Int {
        return SecureRandom().nextInt(MAX_REGISTRATION_ID - 1) + 1
    }

    private companion object {
        const val MAX_REGISTRATION_ID = 16_384
    }
}
