package com.example.securechatapp.crypto.signal

data class SignalProtocolConfig(
    val enabled: Boolean,
) {
    companion object {
        val Disabled = SignalProtocolConfig(enabled = false)
    }
}
