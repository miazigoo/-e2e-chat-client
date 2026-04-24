package com.example.securechatapp.crypto.signal

import javax.inject.Inject
import javax.inject.Singleton

data class SignalProtocolReadiness(
    val ready: Boolean,
    val blockingReasons: List<String>,
)

@Singleton
class SignalProtocolReadinessChecker @Inject constructor(
    private val engine: SignalProtocolEngine,
) {
    fun check(): SignalProtocolReadiness {
        val reasons = buildList {
            if (!engine.isEnabled) {
                add("Signal Protocol feature flag is disabled")
            }
            add("Durable identity/session/pre-key stores are not wired yet")
            add("Server pre-key publish/fetch migration is not finalized")
            add("Legacy AES-GCM message migration strategy is not finalized")
        }

        return SignalProtocolReadiness(
            ready = reasons.isEmpty(),
            blockingReasons = reasons,
        )
    }
}
