package com.example.securechatapp.data.remote.websocket

import com.example.securechatapp.BuildConfig
import com.example.securechatapp.data.local.preferences.SecureSessionLocalDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
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
import kotlinx.coroutines.isActive
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
    private var keepAliveJob: Job? = null
    private var reconnectJob: Job? = null
    private var desiredConnection = false
    private var reconnectAttempt = 0

    private val subscribedConversationIds = linkedSetOf<Int>()

    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()

    private val _connectionState = MutableStateFlow(RealtimeConnectionState.DISCONNECTED)
    val connectionState: StateFlow<RealtimeConnectionState> = _connectionState.asStateFlow()

    suspend fun connectIfNeeded(): Boolean {
        return connectionMutex.withLock {
            desiredConnection = true

            if (webSocket != null) {
                true
            } else {
                reconnectJob?.cancel()
                reconnectJob = null
                openSocket(isReconnect = reconnectAttempt > 0)
            }
        }
    }

    fun disconnect() {
        desiredConnection = false
        reconnectAttempt = 0
        reconnectJob?.cancel()
        reconnectJob = null
        stopKeepAlive()
        webSocket?.close(1000, "client_disconnect")
        webSocket = null
        _connectionState.value = RealtimeConnectionState.DISCONNECTED
    }

    suspend fun subscribeConversation(conversationId: Int) {
        subscribedConversationIds.add(conversationId)
        if (!connectIfNeeded()) return

        if (_connectionState.value == RealtimeConnectionState.CONNECTED) {
            webSocket?.send(
                """{"type":"subscribe_conversation","conversation_id":$conversationId}"""
            )
        }
    }

    fun unsubscribeConversation(conversationId: Int) {
        subscribedConversationIds.remove(conversationId)

        if (_connectionState.value == RealtimeConnectionState.CONNECTED) {
            webSocket?.send(
                """{"type":"unsubscribe_conversation","conversation_id":$conversationId}"""
            )
        }
    }

    fun sendPing() {
        if (_connectionState.value == RealtimeConnectionState.CONNECTED) {
            webSocket?.send("""{"type":"ping"}""")
        }
    }

    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (isActive && webSocket != null) {
                delay(25_000)
                sendPing()
            }
        }
    }

    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    private suspend fun openSocket(
        isReconnect: Boolean,
    ): Boolean {
        val session = sessionLocalDataSource.getSessionSnapshot()
        val accessToken = session.accessToken?.takeIf { it.isNotBlank() } ?: run {
            _connectionState.value = RealtimeConnectionState.DISCONNECTED
            return false
        }
        val deviceUuid = session.deviceUuid?.takeIf { it.isNotBlank() } ?: run {
            _connectionState.value = RealtimeConnectionState.DISCONNECTED
            return false
        }

        _connectionState.value = if (isReconnect) {
            RealtimeConnectionState.RECONNECTING
        } else {
            RealtimeConnectionState.CONNECTING
        }

        val request = Request.Builder()
            .url(buildWsUrl())
            .header("Authorization", "Bearer $accessToken")
            .header("X-Device-UUID", deviceUuid)
            .build()

        webSocket = okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    reconnectAttempt = 0
                    _connectionState.value = RealtimeConnectionState.CONNECTED
                    startKeepAlive()

                    scope.launch {
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
                        socket = webSocket,
                        emitError = null,
                    )
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    handleSocketTermination(
                        socket = webSocket,
                        emitError = null,
                    )
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: okhttp3.Response?,
                ) {
                    handleSocketTermination(
                        socket = webSocket,
                        emitError = t.message ?: "WebSocket failure",
                    )
                }
            }
        )

        return true
    }

    private fun handleSocketTermination(
        socket: WebSocket,
        emitError: String?,
    ) {
        val isCurrentSocket = webSocket === socket
        stopKeepAlive()

        if (isCurrentSocket) {
            webSocket = null
        }

        if (emitError != null) {
            scope.launch {
                _events.emit(
                    RealtimeEvent.Error(
                        message = emitError,
                    )
                )
            }
        }

        scope.launch {
            _events.emit(RealtimeEvent.Disconnected)
        }

        if (desiredConnection) {
            scheduleReconnect()
        } else {
            _connectionState.value = RealtimeConnectionState.DISCONNECTED
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return

        reconnectJob = scope.launch {
            val attempt = reconnectAttempt
            val delayMs = calculateReconnectDelay(attempt)

            _connectionState.value = RealtimeConnectionState.RECONNECTING

            if (delayMs > 0) {
                delay(delayMs)
            }

            reconnectAttempt += 1

            connectionMutex.withLock {
                if (!desiredConnection || webSocket != null) {
                    return@withLock
                }
                openSocket(isReconnect = true)
            }
        }
    }

    private fun calculateReconnectDelay(
        attempt: Int,
    ): Long {
        if (attempt <= 0) return 0L
        val baseDelay = 1_500L
        val exponential = baseDelay * (1 shl min(attempt, 5))
        return exponential.coerceAtMost(30_000L)
    }

    private suspend fun resubscribeAll() {
        subscribedConversationIds.forEach { conversationId ->
            webSocket?.send(
                """{"type":"subscribe_conversation","conversation_id":$conversationId}"""
            )
        }
    }

    private suspend fun handleIncomingMessage(raw: String) {
        val parsed = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        val type = parsed["type"]?.jsonPrimitive?.contentOrNull ?: return

        when (type) {
            "connected" -> {
                _connectionState.value = RealtimeConnectionState.CONNECTED
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
                    )
                )
            }

            "error" -> {
                val code = parsed["code"]?.jsonPrimitive?.contentOrNull
                val message = parsed["message"]?.jsonPrimitive?.contentOrNull ?: "Unknown websocket error"
                _events.emit(
                    RealtimeEvent.Error(
                        code = code,
                        message = message,
                    )
                )
            }
        }
    }

    private fun buildWsUrl(): String {
        val httpBase = BuildConfig.API_BASE_URL.trimEnd('/')
        return when {
            httpBase.startsWith("https://") -> httpBase.replaceFirst("https://", "wss://") + "/ws"
            httpBase.startsWith("http://") -> httpBase.replaceFirst("http://", "ws://") + "/ws"
            else -> error("Unsupported API_BASE_URL: $httpBase")
        }
    }
}

private val kotlinx.serialization.json.JsonPrimitive.intOrNull: Int?
    get() = content.toIntOrNull()

private val kotlinx.serialization.json.JsonPrimitive.contentOrNull: String?
    get() = runCatching { content }.getOrNull()
