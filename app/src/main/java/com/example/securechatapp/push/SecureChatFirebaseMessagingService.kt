package com.example.securechatapp.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SecureChatFirebaseMessagingService : FirebaseMessagingService() {
    @Inject
    lateinit var pushRegistrationManager: PushRegistrationManager

    @Inject
    lateinit var pushNotificationManager: PushNotificationManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        serviceScope.launch {
            pushRegistrationManager.syncToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val payload = parsePushPayload(message.data) ?: return
        serviceScope.launch {
            pushNotificationManager.handle(payload)
        }
    }
}
