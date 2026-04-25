package com.example.securechatapp.app.runtime

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.securechatapp.data.local.preferences.SecureSessionLocalDataSource
import com.example.securechatapp.data.remote.websocket.RealtimeWebSocketManager
import com.example.securechatapp.data.repository.OutboxDispatcher
import com.example.securechatapp.data.repository.SessionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class AppRuntimeManager @Inject constructor(
    private val sessionLocalDataSource: SecureSessionLocalDataSource,
    private val sessionRepository: SessionRepository,
    private val realtimeWebSocketManager: RealtimeWebSocketManager,
    private val outboxDispatcher: OutboxDispatcher,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateMutex = Mutex()

    private var started = false
    private var isForeground = false

    private var heartbeatJob: Job? = null
    private var outboxJob: Job? = null
    private var realtimeReconnectJob: Job? = null
    private var sessionObserverJob: Job? = null

    fun start() {
        if (started) return
        started = true

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        sessionObserverJob = scope.launch {
            sessionLocalDataSource.sessionFlow.collectLatest {
                refreshRuntimeState()
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        isForeground = true
        scope.launch {
            refreshRuntimeState()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        isForeground = false
        scope.launch {
            refreshRuntimeState()
        }
    }

    private suspend fun refreshRuntimeState() {
        stateMutex.withLock {
            val session = sessionLocalDataSource.getSessionSnapshot()
            val authorized = !session.accessToken.isNullOrBlank()

            if (authorized && isForeground) {
                activateRuntime()
            } else {
                deactivateRuntime()
            }
        }
    }

    private suspend fun activateRuntime() {
        realtimeWebSocketManager.connectIfNeeded()
        outboxDispatcher.recoverStuckMessages()
        outboxDispatcher.drainAll()
        ensureHeartbeatLoop()
        ensureOutboxLoop()
        ensureRealtimeReconnectLoop()
    }

    private suspend fun deactivateRuntime() {
        heartbeatJob?.cancel()
        heartbeatJob = null

        outboxJob?.cancel()
        outboxJob = null

        realtimeReconnectJob?.cancel()
        realtimeReconnectJob = null

        realtimeWebSocketManager.disconnect()
    }

    private fun ensureHeartbeatLoop() {
        if (heartbeatJob != null) return

        heartbeatJob = scope.launch {
            while (isActive) {
                runCatching {
                    sessionRepository.heartbeat()
                }
                delay(45_000L)
            }
        }
    }

    private fun ensureOutboxLoop() {
        if (outboxJob != null) return

        outboxJob = scope.launch {
            while (isActive) {
                runCatching {
                    outboxDispatcher.drainAll()
                }
                delay(8_000L)
            }
        }
    }

    private fun ensureRealtimeReconnectLoop() {
        if (realtimeReconnectJob != null) return

        realtimeReconnectJob = scope.launch {
            while (isActive) {
                runCatching {
                    realtimeWebSocketManager.connectIfNeeded()
                }
                delay(10_000L)
            }
        }
    }
}
