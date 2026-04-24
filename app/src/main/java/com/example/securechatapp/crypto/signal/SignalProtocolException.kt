package com.example.securechatapp.crypto.signal

class SignalProtocolUnavailableException(
    message: String = "Signal Protocol is not enabled. Configure libsignal stores and key lifecycle before enabling it.",
) : IllegalStateException(message)
