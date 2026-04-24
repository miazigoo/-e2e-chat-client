package com.example.securechatapp.data.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundWorkScheduler @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val workManager = WorkManager.getInstance(context)

    fun scheduleAuthenticatedWork() {
        enqueuePeriodicConversationSync()
        enqueueOutboxDrain()
    }

    fun enqueueOutboxDrain() {
        val request = OneTimeWorkRequestBuilder<OutboxDrainWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                OUTBOX_BACKOFF_MINUTES,
                TimeUnit.MINUTES,
            )
            .build()

        workManager.enqueueUniqueWork(
            OutboxDrainWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun enqueuePeriodicConversationSync() {
        val request = PeriodicWorkRequestBuilder<ConversationSyncWorker>(
            SYNC_REPEAT_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(networkConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                SYNC_BACKOFF_MINUTES,
                TimeUnit.MINUTES,
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            ConversationSyncWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancelAuthenticatedWork() {
        workManager.cancelUniqueWork(OutboxDrainWorker.UNIQUE_WORK_NAME)
        workManager.cancelUniqueWork(ConversationSyncWorker.UNIQUE_WORK_NAME)
    }

    private fun networkConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }

    companion object {
        private const val SYNC_REPEAT_MINUTES = 15L
        private const val SYNC_BACKOFF_MINUTES = 10L
        private const val OUTBOX_BACKOFF_MINUTES = 1L
    }
}
