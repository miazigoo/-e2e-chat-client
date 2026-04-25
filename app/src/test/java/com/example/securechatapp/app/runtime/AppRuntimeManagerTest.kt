package com.example.securechatapp.app.runtime

import androidx.lifecycle.LifecycleOwner
import com.example.securechatapp.data.local.preferences.SecureSessionLocalDataSource
import com.example.securechatapp.data.local.preferences.SessionState
import com.example.securechatapp.data.remote.websocket.RealtimeWebSocketManager
import com.example.securechatapp.data.repository.OutboxDispatcher
import com.example.securechatapp.data.repository.SessionRepository
import com.example.securechatapp.push.PushRegistrationManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class AppRuntimeManagerTest {

    private val sessionLocalDataSource = mockk<SecureSessionLocalDataSource>()
    private val sessionRepository = mockk<SessionRepository>(relaxed = true)
    private val realtimeWebSocketManager = mockk<RealtimeWebSocketManager>(relaxed = true)
    private val outboxDispatcher = mockk<OutboxDispatcher>(relaxed = true)
    private val pushRegistrationManager = mockk<PushRegistrationManager>(relaxed = true)
    private val lifecycleOwner = mockk<LifecycleOwner>()

    private val runtimeManager = AppRuntimeManager(
        sessionLocalDataSource = sessionLocalDataSource,
        sessionRepository = sessionRepository,
        realtimeWebSocketManager = realtimeWebSocketManager,
        outboxDispatcher = outboxDispatcher,
        pushRegistrationManager = pushRegistrationManager,
    )

    @Test
    fun `onStart activates runtime for authorized session`() {
        every {
            sessionLocalDataSource.getSessionSnapshot()
        } returns SessionState(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            deviceUuid = "device-uuid",
        )

        runtimeManager.onStart(lifecycleOwner)

        coVerify(timeout = 1_500, atLeast = 1) { pushRegistrationManager.syncCurrentToken() }
        coVerify(timeout = 1_500, atLeast = 1) { realtimeWebSocketManager.connectIfNeeded() }
        coVerify(timeout = 1_500, atLeast = 1) { outboxDispatcher.recoverStuckMessages() }
        coVerify(timeout = 1_500, atLeast = 1) { outboxDispatcher.drainAll() }
        coVerify(timeout = 1_500, atLeast = 1) { sessionRepository.heartbeat() }
    }

    @Test
    fun `onStart keeps runtime inactive for unauthorized session`() {
        every {
            sessionLocalDataSource.getSessionSnapshot()
        } returns SessionState(
            accessToken = null,
            refreshToken = null,
            deviceUuid = "device-uuid",
        )

        runtimeManager.onStart(lifecycleOwner)
        Thread.sleep(250)

        verify(atLeast = 1) { realtimeWebSocketManager.disconnect() }
        coVerify(atLeast = 1) { pushRegistrationManager.clearTokenIfUnavailable() }
        coVerify(exactly = 0) { realtimeWebSocketManager.connectIfNeeded() }
        coVerify(exactly = 0) { outboxDispatcher.recoverStuckMessages() }
        coVerify(exactly = 0) { sessionRepository.heartbeat() }
    }

    @Test
    fun `onStop disconnects runtime`() {
        every {
            sessionLocalDataSource.getSessionSnapshot()
        } returns SessionState(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            deviceUuid = "device-uuid",
        )

        runtimeManager.onStop(lifecycleOwner)

        verify(timeout = 1_500, atLeast = 1) { realtimeWebSocketManager.disconnect() }
        coVerify(exactly = 0) { pushRegistrationManager.clearTokenIfUnavailable() }
    }
}
