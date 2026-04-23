package com.example.securechatapp.data.remote.websocket

import com.example.securechatapp.BuildConfig
import com.example.securechatapp.data.local.preferences.SecureSessionLocalDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener

enum class RealtimeConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
}

sealed interface RealtimeEvent {
    data object Connected : RealtimeEvent
    data object Disconnected : RealtimeEvent
    data class Subscribed(val conversationId: Int) : RealtimeEvent
    data class Unsubscribed(val conversationId: Int) : RealtimeEvent
    data class ConversationEvent(
        val conversationId: Int,
        val eventType: String,
    ) : RealtimeEvent

    data class Error(
        val code: String? = null,
        val message: String,
    ) : RealtimeEvent
}

@Singleton
class RealtimeWebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val sessionLocalDataSource: SecureSessionLocalDataSource,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectionMutex = Mutex()

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var keepAliveJob: Job? = null
    private var isConnected: Boolean = false
    private var reconnectAttempt: Int = 0
    private var explicitDisconnect: Boolean = false

    private val subscribedConversationIds = linkedSetOf<Int>()

    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()

    private val _connectionState = MutableStateFlow(RealtimeConnectionState.DISCONNECTED)
    val connectionState: StateFlow<RealtimeConnectionState> = _connectionState.asStateFlow()

    suspend fun connectIfNeeded(
        forceReconnect: Boolean = false,
    ): Boolean {
        return connectionMutex.withLock {
            if (!forceReconnect && webSocket != null) {
                return@withLock true
            }

            val session = sessionLocalDataSource.getSessionSnapshot() ?: run {
                updateConnectionState(RealtimeConnectionState.DISCONNECTED)
                return@withLock false
            }
            val accessToken = session.accessToken?.takeIf { it.isNotBlank() } ?: run {
                updateConnectionState(RealtimeConnectionState.DISCONNECTED)
                return@withLock false
            }
            val deviceUuid = session.deviceUuid?.takeIf { it.isNotBlank() } ?: run {
                updateConnectionState(RealtimeConnectionState.DISCONNECTED)
                return@withLock false
            }

            explicitDisconnect = false

            webSocket?.cancel()
            webSocket = null
            isConnected = false

            updateConnectionState(
                if (reconnectAttempt > 0 || forceReconnect) {
                    RealtimeConnectionState.RECONNECTING
                } else {
                    RealtimeConnectionState.CONNECTING
                },
            )

            val request = Request.Builder()
                .url(buildWsUrl())
                .header("Authorization", "Bearer $accessToken")
                .header("X-Device-UUID", deviceUuid)
                .build()

            webSocket = okHttpClient.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                        isConnected = true
                        reconnectAttempt = 0
                        reconnectJob = null
                        startKeepAlive()

                        scope.launch {
                            updateConnectionState(RealtimeConnectionState.CONNECTED)
                            _events.emit(RealtimeEvent.Connected)
                            resubscribeAll()
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        scope.launch {
                            handleIncomingMessage(text)
                        }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        handleSocketTermination(
                            reason = reason.takeIf { it.isNotBlank() },
                            shouldReconnect = !explicitDisconnect,
                        )
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        handleSocketTermination(
                            reason = reason.takeIf { it.isNotBlank() },
                            shouldReconnect = !explicitDisconnect,
                        )
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: okhttp3.Response?,
                    ) {
                        handleSocketTermination(
                            reason = t.message ?: "WebSocket failure",
                            shouldReconnect = !explicitDisconnect,
                        )
                    }
                },
            )

            true
        }
    }

    fun disconnect() {
        explicitDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        stopKeepAlive()

        webSocket?.close(1000, "client_disconnect")
        webSocket = null
        isConnected = false

        scope.launch {
            reconnectAttempt = 0
            updateConnectionState(RealtimeConnectionState.DISCONNECTED)
            _events.emit(RealtimeEvent.Disconnected)
        }
    }

    suspend fun subscribeConversation(conversationId: Int) {
        subscribedConversationIds.add(conversationId)
        if (!connectIfNeeded()) return

        if (isConnected) {
            sendSocketMessage(
                """
                {"type":"subscribe_conversation","conversation_id":$conversationId}
                """.trimIndent(),
            )
        }
    }

    fun unsubscribeConversation(conversationId: Int) {
        subscribedConversationIds.remove(conversationId)

        if (isConnected) {
            sendSocketMessage(
                """
                {"type":"unsubscribe_conversation","conversation_id":$conversationId}
                """.trimIndent(),
            )
        }
    }

    fun sendPing() {
        if (isConnected) {
            sendSocketMessage("""{"type":"ping"}""")
        }
    }

    private fun handleSocketTermination(
        reason: String?,
        shouldReconnect: Boolean,
    ) {
        stopKeepAlive()
        webSocket = null
        isConnected = false

        scope.launch {
            _events.emit(RealtimeEvent.Disconnected)

            if (!reason.isNullOrBlank()) {
                _events.emit(
                    RealtimeEvent.Error(
                        message = reason,
                    ),
                )
            }

            if (shouldReconnect) {
                scheduleReconnect()
            } else {
                reconnectAttempt = 0
                updateConnectionState(RealtimeConnectionState.DISCONNECTED)
            }
        }
    }

    private fun scheduleReconnect() {
        if (explicitDisconnect) {
            scope.launch {
                updateConnectionState(RealtimeConnectionState.DISCONNECTED)
            }
            return
        }

        if (reconnectJob?.isActive == true) return

        reconnectJob = scope.launch {
            while (!explicitDisconnect && webSocket == null) {
                reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(MAX_RECONNECT_ATTEMPTS)
                updateConnectionState(RealtimeConnectionState.RECONNECTING)

                delay(reconnectBackoffDelay(reconnectAttempt))

                val started = connectIfNeeded(forceReconnect = true)
                if (!started) {
                    reconnectAttempt = 0
                    updateConnectionState(RealtimeConnectionState.DISCONNECTED)
                    break
                }

                delay(RECONNECT_OBSERVE_WINDOW_MS)

                if (connectionState.value == RealtimeConnectionState.CONNECTED) {
                    break
                }
            }
        }
    }

    private fun sendSocketMessage(payload: String) {
        webSocket?.send(payload)
    }

    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (webSocket != null) {
                delay(KEEP_ALIVE_INTERVAL_MS)
                sendPing()
            }
        }
    }

    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    private suspend fun resubscribeAll() {
        subscribedConversationIds.forEach { conversationId ->
            sendSocketMessage(
                """
                {"type":"subscribe_conversation","conversation_id":$conversationId}
                """.trimIndent(),
            )
        }
    }

    private suspend fun handleIncomingMessage(raw: String) {
        val parsed = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        val type = parsed["type"]?.jsonPrimitive?.contentOrNull ?: return

        when (type) {
            "connected" -> {
                updateConnectionState(RealtimeConnectionState.CONNECTED)
                _events.emit(RealtimeEvent.Connected)
            }

            "subscribed" -> {
                val conversationId = parsed["conversation_id"]?.jsonPrimitive?.intOrNull ?: return
                _events.emit(RealtimeEvent.Subscribed(conversationId))
            }

            "unsubscribed" -> {
                val conversationId = parsed["conversation_id"]?.jsonPrimitive?.intOrNull ?: return
                _events.emit(RealtimeEvent.Unsubscribed(conversationId))
            }

            "conversation.event" -> {
                val conversationId = parsed["conversation_id"]?.jsonPrimitive?.intOrNull ?: return
                val eventType = parsed["event"]
                    ?.jsonObject
                    ?.get("event_type")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: "unknown"

                _events.emit(
                    RealtimeEvent.ConversationEvent(
                        conversationId = conversationId,
                        eventType = eventType,
                    ),
                )
            }

            "error" -> {
                val code = parsed["code"]?.jsonPrimitive?.contentOrNull
                val message = parsed["message"]?.jsonPrimitive?.contentOrNull ?: "Unknown websocket error"
                _events.emit(
                    RealtimeEvent.Error(
                        code = code,
                        message = message,
                    ),
                )
            }
        }
    }

    private suspend fun updateConnectionState(
        state: RealtimeConnectionState,
    ) {
        _connectionState.emit(state)
    }

    private fun reconnectBackoffDelay(attempt: Int): Long {
        val power = (attempt - 1).coerceAtLeast(0)
        val exponential = RECONNECT_BASE_DELAY_MS * (1L shl power)
        return exponential.coerceAtMost(RECONNECT_MAX_DELAY_MS)
    }

    private fun buildWsUrl(): String {
        val httpBase = BuildConfig.API_BASE_URL.trimEnd('/')
        return when {
            httpBase.startsWith("https://") -> httpBase.replaceFirst("https://", "wss://") + "/ws"
            httpBase.startsWith("http://") -> httpBase.replaceFirst("http://", "ws://") + "/ws"
            else -> error("Unsupported API_BASE_URL: $httpBase")
        }
    }

    private companion object {
        const val KEEP_ALIVE_INTERVAL_MS = 25_000L
        const val RECONNECT_BASE_DELAY_MS = 1_000L
        const val RECONNECT_MAX_DELAY_MS = 20_000L
        const val RECONNECT_OBSERVE_WINDOW_MS = 2_500L
        const val MAX_RECONNECT_ATTEMPTS = 6
    }
}

private val kotlinx.serialization.json.JsonPrimitive.intOrNull: Int?
    get() = content.toIntOrNull()

private val kotlinx.serialization.json.JsonPrimitive.contentOrNull: String?
    get() = runCatching { content }.getOrNull()
