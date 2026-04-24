package com.example.securechatapp.crypto.signal

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibSignalStoreProvider @Inject constructor() {

    fun assertReady() {
        throw SignalProtocolUnavailableException(
            "No durable SignalProtocolStore is configured. Do not use in-memory or fake Signal stores in production."
        )
    }
}
